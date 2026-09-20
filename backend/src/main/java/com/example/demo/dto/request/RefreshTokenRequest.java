package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body cho POST /api/auth/refresh
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken không được để trống")
        String refreshToken
) {}
