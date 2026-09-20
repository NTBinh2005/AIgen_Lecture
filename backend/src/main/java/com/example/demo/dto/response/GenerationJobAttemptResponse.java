package com.example.demo.dto.response;

import com.example.demo.entity.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record GenerationJobAttemptResponse(
        UUID attemptId,
        int attemptNumber,
        JobStatus status,
        int progress,
        String currentStep,
        String safeErrorCode,
        String safeErrorMessage,
        String resultReference,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt
) {
}
