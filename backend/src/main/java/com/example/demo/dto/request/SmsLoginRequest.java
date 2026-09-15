package com.example.demo.dto.request;

import com.example.demo.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsLoginRequest {
    @NotBlank(message = "So dien thoai khong duoc de trong")
    private String phoneNumber;

    @NotBlank(message = "Ma OTP khong duoc de trong")
    private String otp;

    private String name;

    private UserRole role;
}
