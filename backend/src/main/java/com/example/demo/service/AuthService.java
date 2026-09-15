package com.example.demo.service;

import com.example.demo.dto.request.GoogleLoginRequest;
import com.example.demo.dto.request.SmsLoginRequest;
import com.example.demo.dto.request.SmsOtpRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.OtpResponse;

public interface AuthService {
    AuthResponse loginWithGoogle(GoogleLoginRequest request);

    OtpResponse requestSmsOtp(SmsOtpRequest request);

    AuthResponse verifySmsOtp(SmsLoginRequest request);
}
