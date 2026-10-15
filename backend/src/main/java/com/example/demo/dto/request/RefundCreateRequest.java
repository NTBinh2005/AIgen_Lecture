package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundCreateRequest(
        @NotNull(message = "Mã giao dịch không được để trống")
        Integer paymentId,

        @NotBlank(message = "Lý do hoàn tiền không được để trống")
        @Size(max = 1000, message = "Lý do hoàn tiền không được vượt quá 1000 ký tự")
        String reason
) {}
