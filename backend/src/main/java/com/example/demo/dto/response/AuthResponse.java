package com.example.demo.dto.response;

import com.example.demo.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response trả về sau khi đăng nhập / đăng ký thành công.
 * Frontend lưu accessToken + refreshToken vào store để dùng cho các request tiếp theo.
 *
 * - token (accessToken): JWT ngắn hạn, đính kèm vào Authorization header
 * - refreshToken: UUID dài hạn (30 ngày), dùng để đổi lấy accessToken mới
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private Integer userId;
    private String name;
    private String email;
    private UserRole role;
    private String phoneNumber;
    private String authProvider;

    /** Convenience constructor dùng khi không có refreshToken (backward compat) */
    public AuthResponse(String token, Integer userId, String name, String email, UserRole role) {
        this(token, null, userId, name, email, role, null, "LOCAL");
    }
}
