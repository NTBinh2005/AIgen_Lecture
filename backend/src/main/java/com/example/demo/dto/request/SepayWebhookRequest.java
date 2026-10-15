package com.example.demo.dto.request;

import java.math.BigDecimal;

/** Payload giao dịch ngân hàng do SePay gửi tới webhook. */
public record SepayWebhookRequest(
        Long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String subAccount,
        String code,
        String content,
        String transferType,
        String description,
        BigDecimal transferAmount,
        BigDecimal accumulated,
        String referenceCode
) {}
