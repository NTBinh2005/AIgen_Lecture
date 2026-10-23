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
) {
    @com.fasterxml.jackson.annotation.JsonProperty("targetType")
    public String getTargetType() {
        if (referenceId != null && referenceId.contains(":")) return referenceId.substring(0, referenceId.indexOf(':'));
        return switch (type) {
            case PAYMENT_SUCCESS, PAYMENT_FAILED -> "PAYMENT";
            case REFUND_STATUS -> "REFUND";
            default -> null;
        };
    }
    @com.fasterxml.jackson.annotation.JsonProperty("targetId")
    public String getTargetId() {
        String value = referenceId != null && referenceId.contains(":") ? referenceId.substring(referenceId.indexOf(':') + 1) : referenceId;
        return value != null && value.contains("/") ? value.substring(0, value.indexOf('/')) : value;
    }
}
