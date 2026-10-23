package com.example.demo.service;

import com.example.demo.dto.request.GoogleLoginRequest;
import com.example.demo.dto.request.SmsLoginRequest;
import com.example.demo.dto.request.SmsOtpRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.OtpResponse;

public interface AuthService {

    /**
     * Bước 1 đăng ký email: lưu đăng ký tạm và gửi OTP tới email. Chưa tạo tài khoản.
     */
    OtpResponse requestRegistration(com.example.demo.dto.request.RegisterRequest request);

    /**
     * Bước 2 đăng ký email: xác nhận OTP, tạo tài khoản và trả token.
     */
    AuthResponse verifyRegistration(com.example.demo.dto.request.VerifyRegisterRequest request);

    AuthResponse loginWithGoogle(GoogleLoginRequest request);

    OtpResponse requestSmsOtp(SmsOtpRequest request);

    AuthResponse verifySmsOtp(SmsLoginRequest request);
    /**
     * Đổi refresh token cũ lấy accessToken + refreshToken mới (token rotation).
     * @param rawRefreshToken raw token nhận từ client
     * @return response chứa accessToken và refreshToken mới
     */
    AuthResponse refresh(String rawRefreshToken);

    /**
     * Logout: thu hồi refresh token, vô hiệu hóa phiên hiện tại.
     * @param rawRefreshToken raw token cần thu hồi
     */
    void logout(String rawRefreshToken);

    /**
     * Xác thực token cho các service khác và trả về danh tính, role, permissions.
     * @param token JWT access token
     * @return TokenVerifyResponse chứa thông tin danh tính và quyền hạn
     */
    com.example.demo.dto.response.TokenVerifyResponse verifyToken(String token);
}
