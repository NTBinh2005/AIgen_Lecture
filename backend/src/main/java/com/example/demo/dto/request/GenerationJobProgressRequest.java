package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Internal processor callback. */
public record GenerationJobProgressRequest(
        @Min(0) @Max(100) int progress,
        @NotBlank @Size(max = 120) String currentStep
) {
}
