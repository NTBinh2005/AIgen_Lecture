package com.example.demo.dto.request;

import com.example.demo.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {
    @NotBlank(message = "Google ID token khong duoc de trong")
    private String idToken;

    private UserRole role;
}
