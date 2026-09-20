package com.example.demo.dto.request;

import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        UserRole role,
        @Size(max = 100) String name,
        @Email @Size(max = 100) String email,
        @Size(max = 255) String passwordHash,
        UserStatus status
) {
}
