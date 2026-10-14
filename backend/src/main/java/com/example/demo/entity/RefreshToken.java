package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Lưu trữ Refresh Token của user trong database.
 * - tokenHash: SHA-256 hash của raw token (không lưu token thật để bảo mật)
 * - revoked: true nếu token đã bị thu hồi (logout)
 * - expiresAt: thời điểm hết hạn
 *
 * Khi user logout hoặc token được dùng để refresh, token cũ bị đánh dấu revoked=true
 * và một token mới được tạo (token rotation).
 */
@Entity
@Getter
@Setter
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hash của raw refresh token.
     * Raw token chỉ được gửi cho client, không bao giờ lưu plain text.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
