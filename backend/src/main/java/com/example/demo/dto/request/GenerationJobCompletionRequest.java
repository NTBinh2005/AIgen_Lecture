package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Internal processor callback containing only safe, durable result references. */
public record GenerationJobCompletionRequest(
        @NotBlank @Size(max = 1000) String resultReference,
        @Size(max = 50) String targetType,
        @Size(max = 255) String targetId
) {
}
