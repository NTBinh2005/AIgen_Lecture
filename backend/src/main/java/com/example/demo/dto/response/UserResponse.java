package com.example.demo.dto.response;

import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import java.time.LocalDateTime;

public record UserResponse(
        Integer userId,
        UserRole role,
        String name,
        String email,
        UserStatus status,
        LocalDateTime createdAt
) {
}
