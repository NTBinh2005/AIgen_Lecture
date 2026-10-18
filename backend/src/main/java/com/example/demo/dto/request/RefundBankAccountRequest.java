package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RefundBankAccountRequest(
        @NotBlank(message = "Ngân hàng nhận tiền không được để trống")
        @Pattern(regexp = "[A-Za-z0-9]{2,30}", message = "Dùng mã hoặc tên viết tắt ngân hàng VietQR")
        String bankName,
        @NotBlank(message = "Số tài khoản nhận tiền không được để trống")
        @Pattern(regexp = "[A-Za-z0-9]{4,34}", message = "Số tài khoản phải gồm 4 đến 34 ký tự chữ/số")
        String bankAccount,
        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        @Size(max = 150, message = "Tên chủ tài khoản không được vượt quá 150 ký tự")
        String accountHolder
) {}
