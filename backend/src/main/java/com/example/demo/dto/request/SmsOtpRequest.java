package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsOtpRequest {
    @NotBlank(message = "So dien thoai khong duoc de trong")
    private String phoneNumber;
}
