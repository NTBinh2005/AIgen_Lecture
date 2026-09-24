package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * LIVE-06: Teacher đánh dấu điểm danh thủ công cho một student cụ thể.
 */
public record OfflineAttendanceRequest(

        @NotNull(message = "studentId không được để trống")
        Integer studentId
) {}
