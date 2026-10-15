package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Payload webhook nội bộ đời cũ, chỉ còn giữ cho tương thích với Java callers/tests.
 * <p>
 * Dùng cho endpoint nội bộ (cũ): {@code POST /api/payments/webhook}.
 * Không có public endpoint cho DTO này; tích hợp mới dùng webhook SePay có HMAC.
 */
@Schema(description = "Payload webhook gốc (legacy) — dùng cùng với webhook endpoint chuyên biệt")
public record PaymentWebhookRequest(
        @Schema(description = "Mã giao dịch nội bộ (transactionId)")
        String transactionId,

        @Schema(description = "Trạng thái: SUCCESS hoặc FAILED")
        String status,

        @Schema(description = "Số tiền giao dịch")
        java.math.BigDecimal amount
) {}
