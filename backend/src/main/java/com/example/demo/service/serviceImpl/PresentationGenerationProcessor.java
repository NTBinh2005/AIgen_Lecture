package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.GenerationJobProgressRequest;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.dto.response.LectureVersionResponse;
import com.example.demo.entity.Presentation;
import com.example.demo.entity.PresentationSourceType;
import com.example.demo.repository.PresentationRepository;
import com.example.demo.service.AssetContent;
import com.example.demo.service.AssetService;
import com.example.demo.service.DocumentParserService;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.LectureService;
import com.example.demo.service.LlmService;
import com.example.demo.service.event.PresentationGenerationQueuedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresentationGenerationProcessor {
    private final GenerationJobService generationJobService;
    private final AssetService assetService;
    private final LectureService lectureService;
    private final DocumentParserService documentParserService;
    private final LlmService llmService;
    private final PresentationGenerationResultService resultService;
    private final SlideAiClient slideAiClient;
    private final PresentationRepository presentationRepository;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_SLIDE_COUNT = 10;
    private static final Set<String> AUDIENCES = Set.of("THCS", "THPT", "DAI_HOC", "DI_LAM");

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void process(PresentationGenerationQueuedEvent event) {
        try {
            generationJobService.start(event.jobId(), event.attemptId(), "READING_SOURCE");
            String sourceText = readSource(event);
            generationJobService.updateProgress(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobProgressRequest(35, "GENERATING_SLIDES"));
            List<PresentationSlideRequest> slides = generateWithSlideAi(event, sourceText);
            if (slides == null) {
                LectureGenerateResponse generated = llmService.generateLectureScript(sourceText);
                if (!generated.isAiGenerated()) throw new IllegalStateException("AI_AUTH_FAILED: Nội dung mẫu không được lưu như slide AI");
                slides = mapSlides(generated);
            }
            generationJobService.updateProgress(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobProgressRequest(90, "SAVING_DRAFT"));
            resultService.complete(event, slides);
        } catch (Exception exception) {
            log.warn("Presentation generation job {} failed with {}",
                    event.jobId(), exception.getClass().getSimpleName());
            resultService.fail(event);
        }
    }

    /**
     * Sinh slide nhiều bố cục bằng slide-ai-service (Ollama, dự phòng Gemini).
     * Trả về null khi service tắt hoặc lỗi để dùng luồng Gemini cũ.
     */
    private List<PresentationSlideRequest> generateWithSlideAi(
            PresentationGenerationQueuedEvent event, String sourceText) {
        if (!slideAiClient.isEnabled()) {
            return null;
        }
        try {
            Presentation presentation = presentationRepository.findById(event.presentationId())
                    .orElseThrow(() -> new IllegalStateException("Presentation was removed during generation"));
            JsonNode parameters = readParameters(presentation.getGenerationParameters());
            int slideCount = Math.clamp(parameters.path("slideCount").asInt(DEFAULT_SLIDE_COUNT),
                    SlideAiDeckMapper.MIN_RENDER_SLIDES, SlideAiDeckMapper.MAX_RENDER_SLIDES);
            String audience = parameters.path("audience").asText("DAI_HOC").toUpperCase();
            JsonNode deck = slideAiClient.generate(
                    sourceText,
                    slideCount,
                    AUDIENCES.contains(audience) ? audience : "DAI_HOC",
                    SlideAiDeckMapper.themeFor(presentation.getTemplateId()));
            return SlideAiDeckMapper.toSlideRequests(deck);
        } catch (Exception exception) {
            log.warn("slide-ai-service failed for presentation job {} ({}); falling back to Gemini",
                    event.jobId(), exception.getMessage());
            return null;
        }
    }

    private JsonNode readParameters(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String readSource(PresentationGenerationQueuedEvent event) {
        if (event.sourceType() == PresentationSourceType.ASSET) {
            AssetContent asset = assetService.getContentForProcessing(
                    event.sourceAssetId(), event.ownerId());
            return documentParserService.parseDocument(asset.getOriginalFilename(), asset.getBytes());
        }
        LectureVersionResponse version = lectureService.getPublishedVersionContract(
                java.util.UUID.fromString(event.sourceId()));
        return version.content();
    }

    private List<PresentationSlideRequest> mapSlides(LectureGenerateResponse generated) {
        if (generated == null || generated.getSlides() == null || generated.getSlides().isEmpty()) {
            throw new IllegalStateException("AI did not return any slides");
        }
        List<PresentationSlideRequest> result = new ArrayList<>();
        int order = 0;
        for (LectureGenerateResponse.SlideDto slide : generated.getSlides()) {
            List<String> blocks = slide.getBulletPoints() == null
                    ? List.of()
                    : slide.getBulletPoints();
            result.add(new PresentationSlideRequest(
                    slide.getTitle(),
                    blocks,
                    slide.getNarrationText(),
                    "TITLE_AND_CONTENT",
                    order++));
        }
        return result;
    }
}
