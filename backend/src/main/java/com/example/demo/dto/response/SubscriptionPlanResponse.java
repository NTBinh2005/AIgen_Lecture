package com.example.demo.dto.response;

import com.example.demo.entity.BillingCycle;
import com.example.demo.entity.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SubscriptionPlanResponse(
        Integer planId,
        String code,
        String name,
        String description,
        BigDecimal price,
        BillingCycle billingCycle,
        Integer durationDays,
        UserRole targetRole,
        List<String> features,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
