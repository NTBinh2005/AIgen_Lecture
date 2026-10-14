package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body cho POST /api/auth/logout
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken không được để trống")
        String refreshToken
) {}
