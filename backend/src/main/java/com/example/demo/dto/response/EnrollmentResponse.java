package com.example.demo.dto.response;

import com.example.demo.entity.EnrollmentStatus;
import java.time.LocalDateTime;

public record EnrollmentResponse(
        Integer classId,
        String className,
        Integer studentId,
        String studentName,
        LocalDateTime enrolledAt,
        EnrollmentStatus status
) {
}
