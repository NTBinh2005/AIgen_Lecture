package com.example.demo.service;

import com.example.demo.dto.response.QrTokenResponse;

/**
 * LIVE-06: Tạo và xác thực QR code ngắn hạn cho điểm danh offline.
 */
public interface QrService {

    /**
     * Tạo QR code mới cho session.
     * TTL cấu hình qua live.qr.expiry-seconds.
     */
    QrTokenResponse generateQr(Long sessionId, Integer teacherId);
}
