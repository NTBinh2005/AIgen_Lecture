package com.example.demo.repository;

import com.example.demo.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi tất cả refresh token hợp lệ của một user (dùng khi logout all devices).
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.userId = :userId AND rt.revoked = false")
    void revokeAllByUserId(@Param("userId") Integer userId);

    /**
     * Xóa các token đã hết hạn để giữ bảng gọn.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteAllExpiredBefore(@Param("now") Instant now);
}
