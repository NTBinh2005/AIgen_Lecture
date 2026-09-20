package com.example.demo.dto.request;

import com.example.demo.entity.EnrollmentStatus;
import jakarta.validation.constraints.NotNull;

public record EnrollmentStatusRequest(
        @NotNull EnrollmentStatus status
) {
}
