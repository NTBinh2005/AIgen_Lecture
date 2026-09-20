package com.example.demo.service.event;

import java.util.UUID;

public record LectureGenerationQueuedEvent(
        UUID jobId,
        UUID attemptId,
        Long lectureId,
        UUID sourceAssetId,
        Integer ownerId
) {
}
