package com.example.demo.dto.response;

import java.time.LocalDateTime;

/**
 * LIVE-02: Token ngắn hạn cấp cho user sau khi kiểm tra quyền tham gia.
 * LIVE-AC-01: Token chứa userId, sessionId và thời hạn.
 */
public record JoinTokenResponse(
        String token,
        String meetingUrl,
        LocalDateTime expiresAt
) {}
