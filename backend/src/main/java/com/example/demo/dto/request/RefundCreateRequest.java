package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundCreateRequest(
        @NotNull(message = "paymentId is required")
        Integer paymentId,

        @NotBlank(message = "reason is required")
        @Size(max = 1000, message = "reason must not exceed 1000 characters")
        String reason
) {}
