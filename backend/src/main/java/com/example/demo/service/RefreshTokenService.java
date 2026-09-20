package com.example.demo.service;

import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.User;

/**
 * Service quản lý vòng đời của Refresh Token.
 */
public interface RefreshTokenService {

    /**
     * Tạo refresh token mới cho user và lưu vào database.
     * @return raw token (UUID) — chỉ trả cho client một lần duy nhất
     */
    String createRefreshToken(User user);

    /**
     * Validate raw token, thu hồi token cũ (rotation), tạo và trả token mới.
     * Ném exception nếu token không hợp lệ/đã hết hạn/đã bị revoked.
     * @return raw token mới
     */
    String rotateRefreshToken(String rawToken);

    /**
     * Lấy User từ raw refresh token (dùng để build auth response sau khi rotate).
     * Ném exception nếu token không hợp lệ.
     */
    User getUserFromToken(String rawToken);

    /**
     * Thu hồi một refresh token cụ thể (logout đơn thiết bị).
     */
    void revokeToken(String rawToken);

    /**
     * Thu hồi tất cả refresh token của user (logout tất cả thiết bị).
     */
    void revokeAllTokensForUser(Integer userId);

    /**
     * Xóa các refresh token đã hết hạn khỏi database.
     */
    void deleteExpiredTokens();
}
