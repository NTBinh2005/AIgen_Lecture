package com.example.demo.dto.request;

/**
 * LIVE-08 nhánh CANCELLED: Hủy buổi học kèm lý do.
 */
public record LiveSessionCancelRequest(
        String cancelReason
) {}
