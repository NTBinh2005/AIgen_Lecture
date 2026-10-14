package com.example.demo.dto.response;

public record BulkEnrollmentItemResponse(
        Integer studentId,
        boolean success,
        EnrollmentResponse enrollment,
        String error
) {}
