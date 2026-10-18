package com.example.demo.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Bản trình chiếu đã giao cho lớp (thông tin phiên bản đã xuất bản, không kèm nội dung slide). */
public record ClassPresentationResponse(
        UUID presentationId,
        String title,
        String templateId,
        int slideCount,
        Instant publishedAt,
        LocalDateTime assignedAt
) {
}
