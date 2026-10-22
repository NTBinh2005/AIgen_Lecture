package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassOrderRequest {
    @NotNull(message = "ID lớp học không được để trống")
    private Integer classId;

    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String paymentMethod; // "WALLET" | "SEPAY_QR"
}
