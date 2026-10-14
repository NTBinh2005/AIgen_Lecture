package com.example.demo.entity;

/**
 * Trạng thái recording của buổi live.
 * REQUESTED  — đã yêu cầu provider ghi hình.
 * PROCESSING — provider đang xử lý.
 * READY      — sẵn sàng phát lại (playbackUrl có giá trị).
 * FAILED     — provider báo lỗi.
 */
public enum RecordingStatus {
    REQUESTED,
    PROCESSING,
    READY,
    FAILED
}
