package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentSucceededRequest(
        @NotBlank String eventId,
        @NotNull Integer classId,
        @NotNull Integer studentId
) {}
