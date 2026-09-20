package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

public record EnrollmentRequest(
        @NotNull Integer studentId
) {
}
