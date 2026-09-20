package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for validating a JWT token across services")
public record TokenVerifyRequest(
        @Schema(description = "JWT Access Token to verify", example = "eyJhbGciOiJIUzUxMiJ9...")
        @NotBlank(message = "Token cannot be blank")
        String token
) {}
