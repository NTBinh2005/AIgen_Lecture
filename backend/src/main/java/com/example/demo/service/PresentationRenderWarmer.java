package com.example.demo.service;

import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.dto.response.PresentationVersionResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Dựng sẵn HTML trình chiếu (chạy nền) sau khi tạo/lưu/xuất bản/giao cho lớp, để bản đã lưu
 * có sẵn khi slide-ai-service tắt. Lỗi bị bỏ qua: lần trình chiếu sau sẽ dựng lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresentationRenderWarmer {
    private final PresentationRenderService renderService;
    private final PresentationService presentationService;

    /** Dựng phiên bản hiện tại và phiên bản đã xuất bản (nếu khác) của bản trình chiếu. */
    @Async("taskExecutor")
    public void warm(PresentationResponse presentation) {
        if (presentation == null) {
            return;
        }
        PresentationVersionResponse current = presentation.currentVersion();
        warmVersion(current, presentation.templateId());
        if (presentation.publishedVersionId() != null
                && (current == null || !Objects.equals(presentation.publishedVersionId(), current.versionId()))) {
            // Phiên bản đã xuất bản khác bản nháp hiện tại (học sinh xem bản này)
            try {
                warmVersion(presentationService.publishedVersionContract(presentation.publishedVersionId()),
                        presentation.templateId());
            } catch (RuntimeException exception) {
                log.debug("Skip warming published render of {}: {}", presentation.presentationId(), exception.getMessage());
            }
        }
    }

    @Async("taskExecutor")
    public void warm(PresentationVersionResponse version, String templateId) {
        warmVersion(version, templateId);
    }

    private void warmVersion(PresentationVersionResponse version, String templateId) {
        if (version == null || version.slides() == null || version.slides().isEmpty()) {
            return;
        }
        try {
            renderService.renderVersion(version, templateId);
        } catch (RuntimeException exception) {
            // Chưa đủ 3 slide, service tắt và chưa có bản lưu...: bỏ qua
            log.debug("Skip warming render of version {}: {}", version.versionId(), exception.getMessage());
        }
    }
}
