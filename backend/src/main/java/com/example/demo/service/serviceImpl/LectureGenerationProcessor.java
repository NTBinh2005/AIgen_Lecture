package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

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

            Object params = generationJobService.get(event.jobId(), event.ownerId(), false).parameters();
            String language = objectMapper.valueToTree(params).path("language").asText("auto");
            LectureGenerateResponse generated = llmService.generateLectureScript(documentText, 5, language);
            if (!generated.isAiGenerated()) throw new IllegalStateException("AI_AUTH_FAILED: Nội dung mẫu không được lưu như bài giảng AI");
            String slideContent = writeSlides(generated);
            generationJobService.updateProgress(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobProgressRequest(90, "SAVING_DRAFT"));
            resultService.complete(event, documentText, slideContent, generated.getQuizzes());
        } catch (Exception exception) {
            log.error("Lecture generation job {} failed", event.jobId(), exception);
            Failure failure = safeFailure(exception);
            resultService.fail(event, failure.code(), failure.message());
        }
    }

    private Failure safeFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().startsWith("AI_AUTH_FAILED")) {
                return new Failure("AI_AUTH_FAILED", "Chưa cấu hình khoá Gemini hợp lệ. Vui lòng liên hệ quản trị viên.");
            }
            if (cause instanceof BadRequestException) {
                return new Failure("DOCUMENT_INVALID", cause.getMessage());
            }
            if (cause instanceof HttpStatusCodeException httpError) {
                int status = httpError.getStatusCode().value();
                if (status == 401 || status == 403) {
                    return new Failure("AI_AUTH_FAILED",
                            "Khoá Gemini không hợp lệ hoặc không có quyền truy cập model.");
                }
                if (status == 404) {
                    return new Failure("AI_MODEL_NOT_FOUND",
                            "Model Gemini đang cấu hình không tồn tại. Vui lòng kiểm tra GEMINI_API_URL.");
                }
                if (status == 429 || status == 500 || status == 503) {
                    return new Failure("AI_TEMPORARILY_UNAVAILABLE",
                            "Dịch vụ AI đang quá tải hoặc tạm thời không khả dụng. Vui lòng thử lại.");
                }
                return new Failure("AI_PROVIDER_ERROR",
                        "Dịch vụ AI từ chối yêu cầu (HTTP " + status + ").");
            }
            if (cause instanceof ResourceAccessException) {
                return new Failure("AI_TEMPORARILY_UNAVAILABLE",
                        "Không thể kết nối tới dịch vụ AI. Vui lòng thử lại.");
            }
        }
        return new Failure("LECTURE_GENERATION_FAILED",
                "Không thể tạo bài giảng từ tài liệu này. Vui lòng kiểm tra file và thử lại.");
    }

    private record Failure(String code, String message) {
    }

    private String writeSlides(LectureGenerateResponse response) {
        try {
            return objectMapper.writeValueAsString(response == null ? null : response.getSlides());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Generated slides could not be serialized", exception);
        }
    }
}
