package com.example.demo.dto.response;

import java.time.Instant;

public record LearningAccessResponse(
        Integer id,
        Integer userId,
        Integer productId,
        Integer paymentId,
        String accessType,
        Instant expiresAt,
        Instant grantedAt
) {}
