package com.example.demo.dto.response;

import com.example.demo.entity.AuditAction;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Integer userId,
        AuditAction action,
        String resourceType,
        String resourceId,
        String ipAddress,
        String userAgent,
        String metadata,
        Instant createdAt
) {}
