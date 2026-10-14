package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.User;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    /** Thời gian sống của refresh token — mặc định 30 ngày */
    @Value("${jwt.refresh-expiration-ms:2592000000}")
    private long refreshExpirationMs;

    @Override
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(tokenHash);
        rt.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);

        return rawToken;
    }

    @Override
    @Transactional
    public String rotateRefreshToken(String rawToken) {
        RefreshToken existing = findValidToken(rawToken);

        // Thu hồi token cũ (rotation — ngăn re-use)
        existing.setRevoked(true);

        // Tạo token mới cho cùng user
        User user = existing.getUser();
        String newRawToken = UUID.randomUUID().toString();
        String newHash = sha256(newRawToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(user);
        newToken.setTokenHash(newHash);
        newToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        newToken.setRevoked(false);

        refreshTokenRepository.save(existing);
        refreshTokenRepository.save(newToken);

        return newRawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserFromToken(String rawToken) {
        return findValidToken(rawToken).getUser();
    }

    @Override
    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = sha256(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    @Override
    @Transactional
    public void revokeAllTokensForUser(Integer userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private RefreshToken findValidToken(String rawToken) {
        String tokenHash = sha256(rawToken);
        RefreshToken rt = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Refresh token không hợp lệ hoặc không tồn tại"));

        if (rt.isRevoked()) {
            throw new BadRequestException("Refresh token đã bị thu hồi");
        }
        if (rt.isExpired()) {
            throw new BadRequestException("Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }
        return rt;
    }

    /**
     * SHA-256 hash của raw token — không bao giờ lưu token thật vào DB.
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
