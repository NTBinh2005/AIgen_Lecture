package com.example.demo.dto.response;

import com.example.demo.entity.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
        Integer refundId,
        Integer paymentId,
        Integer userId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
