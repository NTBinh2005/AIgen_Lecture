package com.example.demo.dto.response;

import com.example.demo.entity.JobStatus;
import com.example.demo.entity.JobType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GenerationJobResponse(
        UUID jobId,
        JobType jobType,
        JobStatus status,
        Integer ownerId,
        String idempotencyKey,
        String sourceType,
        String sourceId,
        UUID sourceAssetId,
        UUID templateId,
        JsonNode parameters,
        String targetType,
        String targetId,
        int progress,
        String currentStep,
        String safeErrorCode,
        String safeErrorMessage,
        String resultReference,
        int attemptCount,
        int maxAttempts,
        UUID currentAttemptId,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        List<GenerationJobAttemptResponse> attempts
) {
}
