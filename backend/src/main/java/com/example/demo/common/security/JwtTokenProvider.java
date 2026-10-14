package com.example.demo.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Tạo và xác thực JWT tokens.
 *
 * Token payload chứa:
 *   - sub: userId (String)
 *   - email: email
 *   - role: TEACHER / STUDENT / ADMIN
 *   - iat / exp: issued at / expiration
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Dùng HMAC-SHA key từ secret string
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Tạo JWT token từ UserPrincipal.
     */
    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("type", "ACCESS_TOKEN")
                .claim("tokenVersion", principal.getTokenVersion())
                .claim("email", principal.getUsername())
                .claim("role", principal.getAuthorities().iterator().next().getAuthority()
                        .replace("ROLE_", ""))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Lấy userId từ token (dùng để resolve principal).
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Integer.parseInt(claims.getSubject());
    }

    /**
     * Kiểm tra token hợp lệ (chữ ký + hạn dùng).
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Integer.parseInt(claims.getSubject());
            return "ACCESS_TOKEN".equals(claims.get("type"))
                    || (claims.get("type") == null && claims.get("role") != null && claims.get("email") != null);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean matchesUser(String token, com.example.demo.entity.User user) {
        Object version = parseClaims(token).get("tokenVersion");
        return user.getTokenVersion() == (version instanceof Number n ? n.longValue() : 0L);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
