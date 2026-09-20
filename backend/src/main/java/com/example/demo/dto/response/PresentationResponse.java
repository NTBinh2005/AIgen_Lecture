package com.example.demo.dto.response;

import com.example.demo.entity.PresentationSourceType;
import com.example.demo.entity.PresentationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record PresentationResponse(
        UUID presentationId,
        Integer ownerId,
        String title,
        PresentationSourceType sourceType,
        String sourceId,
        UUID sourceAssetId,
        String templateId,
        JsonNode generationParameters,
        PresentationStatus status,
        UUID currentVersionId,
        UUID publishedVersionId,
        int currentVersionNumber,
        boolean savedToLibrary,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        PresentationVersionResponse currentVersion,
        boolean canEdit,
        boolean canPublish,
        boolean canArchive,
        boolean canExport
) {
}
