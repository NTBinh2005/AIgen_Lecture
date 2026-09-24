package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.GenerationJobProgressRequest;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.dto.response.LectureVersionResponse;
import com.example.demo.entity.PresentationSourceType;
import com.example.demo.service.AssetContent;
import com.example.demo.service.AssetService;
import com.example.demo.service.DocumentParserService;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.LectureService;
import com.example.demo.service.LlmService;
import com.example.demo.service.event.PresentationGenerationQueuedEvent;
import java.util.ArrayList;
import java.util.List;
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
            LectureGenerateResponse generated = llmService.generateLectureScript(sourceText);
            List<PresentationSlideRequest> slides = mapSlides(generated);
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
