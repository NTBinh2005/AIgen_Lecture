package com.example.demo.dto.response;

import com.example.demo.entity.QrToken;
import java.time.LocalDateTime;

/**
 * LIVE-06: Kết quả tạo QR code ngắn hạn cho điểm danh offline.
 * Client dùng `code` để render QR image.
 */
public record QrTokenResponse(
        Long tokenId,
        Long sessionId,
        String code,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static QrTokenResponse from(QrToken qr) {
        return new QrTokenResponse(
                qr.getTokenId(),
                qr.getSession().getSessionId(),
                qr.getCode(),
                qr.getExpiresAt(),
                qr.getCreatedAt()
        );
    }
}
