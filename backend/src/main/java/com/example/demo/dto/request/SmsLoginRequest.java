package com.example.demo.dto.request;

import com.example.demo.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsLoginRequest {
    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;

    @NotBlank(message = "Mã OTP không được để trống")
    private String otp;

    private String name;

    private UserRole role;
}
