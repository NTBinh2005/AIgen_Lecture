package com.example.demo.service.event;

import com.example.demo.entity.PresentationSourceType;
import java.util.UUID;

public record PresentationGenerationQueuedEvent(
        UUID jobId,
        UUID attemptId,
        UUID presentationId,
        PresentationSourceType sourceType,
        String sourceId,
        UUID sourceAssetId,
        Integer ownerId
) {
}
