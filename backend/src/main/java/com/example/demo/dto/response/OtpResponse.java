package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OtpResponse {
    private String message;
    private Integer expiresInSeconds;
}
