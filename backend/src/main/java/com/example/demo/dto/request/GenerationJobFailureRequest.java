package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Internal processor callback. The message must already be sanitized and must
 * never contain stack traces, secrets or provider payloads.
 */
public record GenerationJobFailureRequest(
        @NotBlank @Size(max = 80) String safeErrorCode,
        @NotBlank @Size(max = 1000) String safeErrorMessage
) {
}
