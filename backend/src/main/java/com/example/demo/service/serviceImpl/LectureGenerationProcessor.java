package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.GenerationJobProgressRequest;
import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.service.AssetContent;
import com.example.demo.service.AssetService;
import com.example.demo.service.DocumentParserService;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.LlmService;
import com.example.demo.service.event.LectureGenerationQueuedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LectureGenerationProcessor {
    private final GenerationJobService generationJobService;
    private final AssetService assetService;
    private final DocumentParserService documentParserService;
    private final LlmService llmService;
    private final LectureGenerationResultService resultService;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void process(LectureGenerationQueuedEvent event) {
        try {
            generationJobService.start(event.jobId(), event.attemptId(), "PARSING_SOURCE");
            AssetContent source = assetService.getContentForProcessing(
                    event.sourceAssetId(), event.ownerId());
            String documentText = documentParserService.parseDocument(
                    source.getOriginalFilename(), source.getBytes());
            generationJobService.updateProgress(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobProgressRequest(35, "GENERATING_LECTURE"));

            LectureGenerateResponse generated = llmService.generateLectureScript(documentText);
            String slideContent = writeSlides(generated);
            generationJobService.updateProgress(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobProgressRequest(90, "SAVING_DRAFT"));
            resultService.complete(event, documentText, slideContent);
        } catch (Exception exception) {
            log.warn("Lecture generation job {} failed with {}",
                    event.jobId(), exception.getClass().getSimpleName());
            resultService.fail(event);
        }
    }

    private String writeSlides(LectureGenerateResponse response) {
        try {
            return objectMapper.writeValueAsString(response == null ? null : response.getSlides());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Generated slides could not be serialized", exception);
        }
    }
}
