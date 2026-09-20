package com.example.demo.dto.response;

import com.example.demo.entity.PresentationVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PresentationVersionResponse(
        UUID versionId,
        int versionNumber,
        String title,
        List<PresentationSlideResponse> slides,
        PresentationVersionStatus status,
        UUID restoredFromVersionId,
        Integer createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {
}
