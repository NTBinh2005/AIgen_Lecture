package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundCreateRequest(
        @NotNull(message = "Mã giao dịch không được để trống")
        Integer paymentId,

        @NotBlank(message = "Lý do hoàn tiền không được để trống")
        @Size(max = 1000, message = "Lý do hoàn tiền không được vượt quá 1000 ký tự")
        String reason,
        @NotBlank(message = "Ngân hàng nhận tiền không được để trống")
        @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9]{2,30}", message = "Dùng mã hoặc tên viết tắt ngân hàng VietQR, từ 2 đến 30 ký tự chữ/số")
        String bankName,
        @NotBlank(message = "Số tài khoản nhận tiền không được để trống")
        @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9]{4,34}", message = "Số tài khoản phải gồm 4 đến 34 ký tự chữ/số")
        String bankAccount,
        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        @Size(max = 150, message = "Tên chủ tài khoản không được vượt quá 150 ký tự")
        String accountHolder
) {}
