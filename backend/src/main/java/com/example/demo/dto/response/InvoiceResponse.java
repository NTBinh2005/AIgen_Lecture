package com.example.demo.dto.response;

import com.example.demo.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
    Integer id,
    String invoiceNumber,
    Integer userId,
    Integer paymentId,
    BigDecimal amount,
    InvoiceStatus status,
    Instant issuedAt,
    Instant createdAt
) {}
