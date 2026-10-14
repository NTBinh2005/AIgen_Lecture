package com.example.demo.dto.response;

import com.example.demo.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        Integer userId,
        String title,
        String message,
        NotificationType type,
        boolean isRead,
        String referenceId,
        Instant createdAt
) {}
