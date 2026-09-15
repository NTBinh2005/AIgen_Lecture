package com.example.demo.dto.response;

import com.example.demo.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response trả về sau khi đăng nhập / đăng ký thành công.
 * Frontend lưu token vào Zustand store để đính kèm vào các request tiếp theo.
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Integer userId;
    private String name;
    private String email;
    private UserRole role;
    private String phoneNumber;
    private String authProvider;

    public AuthResponse(String token, Integer userId, String name, String email, UserRole role) {
        this(token, userId, name, email, role, null, "LOCAL");
    }
}
