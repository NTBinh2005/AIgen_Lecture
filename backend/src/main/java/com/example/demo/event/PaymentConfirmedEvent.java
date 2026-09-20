package com.example.demo.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published khi Payment có trạng thái SUCCESS (xác nhận thành công).
 *
 * eventId: UUID unique cho mỗi event instance để hỗ trợ idempotency cho consumer.
 */
public record PaymentConfirmedEvent(
    String eventId,
    String eventType,
    Integer paymentId,
    String transactionId,
    Integer userId,
    Integer productId,
    BigDecimal amount,
    Instant timestamp
) {
    public PaymentConfirmedEvent(Integer paymentId, String transactionId, Integer userId, Integer productId, BigDecimal amount) {
        this(UUID.randomUUID().toString(), "PaymentConfirmed", paymentId, transactionId, userId, productId, amount, Instant.now());
    }

    public PaymentConfirmedEvent(String eventId, Integer paymentId, String transactionId, Integer userId, Integer productId, BigDecimal amount) {
        this(eventId, "PaymentConfirmed", paymentId, transactionId, userId, productId, amount, Instant.now());
    }
}
