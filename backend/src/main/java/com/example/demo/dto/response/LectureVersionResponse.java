package com.example.demo.dto.response;

import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import java.time.Instant;
import java.util.UUID;

public record LectureVersionResponse(
        UUID versionId,
        Long lectureId,
        UUID lectureBusinessId,
        int versionNumber,
        String title,
        String content,
        String slideContent,
        LectureVersionStatus status,
        UUID sourceAssetId,
        boolean aiGenerated,
        Integer createdBy,
        Instant createdAt,
        Instant publishedAt
) {
    public static LectureVersionResponse from(LectureVersion version) {
        return new LectureVersionResponse(
                version.getLectureVersionId(),
                version.getLecture().getLectureId(),
                version.getLecture().getBusinessId(),
                version.getVersionNumber(),
                version.getTitle(),
                version.getContent(),
                version.getSlideContent(),
                version.getStatus(),
                version.getSourceAssetId(),
                version.isAiGenerated(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getPublishedAt());
    }
}
