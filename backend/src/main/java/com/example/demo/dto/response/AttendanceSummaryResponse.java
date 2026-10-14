package com.example.demo.dto.response;

/**
 * LIVE-05: Tổng hợp điểm danh của một student trong một session.
 * LIVE-BR-02: present = true khi totalSeconds >= ngưỡng cấu hình.
 * LIVE-AC-02: totalSeconds phản ánh tổng thời gian sau nhiều lần reconnect.
 */
public record AttendanceSummaryResponse(
        Integer studentId,
        String studentName,
        long totalSeconds,
        boolean present,
        String source
) {}
