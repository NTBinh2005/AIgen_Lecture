package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsOtpRequest {
    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;
}
