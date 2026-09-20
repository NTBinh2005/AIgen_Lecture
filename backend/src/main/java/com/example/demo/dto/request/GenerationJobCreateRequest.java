package com.example.demo.dto.request;

import com.example.demo.entity.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Internal command used by trusted Lecture and Presentation processors.
 * It is not exposed as a public controller endpoint.
 */
public record GenerationJobCreateRequest(
        @NotNull JobType jobType,
        @NotNull @Positive Integer ownerId,
        @NotBlank @Size(max = 50) String sourceType,
        @Size(max = 255) String sourceId,
        UUID sourceAssetId,
        UUID templateId,
        JsonNode parameters,
        @Size(max = 50) String targetType,
        @Size(max = 255) String targetId,
        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
