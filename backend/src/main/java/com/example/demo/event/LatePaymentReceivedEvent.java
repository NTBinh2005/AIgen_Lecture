package com.example.demo.event;

import java.math.BigDecimal;

public record LatePaymentReceivedEvent(Integer paymentId, String transactionId, BigDecimal amount) {
}
