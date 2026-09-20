package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Payload IPN/callback nhận từ VNPay hoặc MoMo.
 * <p>
 * Dùng cho endpoint nội bộ (cũ): {@code POST /api/payments/webhook}.
 * Thay thế bằng các webhook chuyên biệt:
 * <ul>
 *   <li>{@code POST /api/payments/webhook/vnpay} — nhận tham số từ VNPay</li>
 *   <li>{@code POST /api/payments/webhook/momo} — nhận JSON body từ MoMo IPN</li>
 * </ul>
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
