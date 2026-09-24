package com.example.demo.entity;

/**
 * Nguồn gốc bản ghi điểm danh.
 * ONLINE — tổng hợp từ sự kiện join/leave của provider.
 * QR     — sinh từ QR code ngắn hạn (LIVE-06).
 * MANUAL — giáo viên đánh dấu thủ công (LIVE-06).
 */
public enum SessionAttendanceSource {
    ONLINE,
    QR,
    MANUAL
}
