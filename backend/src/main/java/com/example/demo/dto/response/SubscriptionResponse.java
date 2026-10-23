package com.example.demo.dto.response;

import com.example.demo.entity.SubscriptionStatus;
import java.time.Instant;

public record SubscriptionResponse(
        Long subscriptionId,
        Integer userId,
        SubscriptionPlanResponse plan,
        SubscriptionStatus status,
        Instant startedAt,
        Instant expiresAt,
        boolean cancelAtPeriodEnd,
        Instant cancelledAt,
        Integer lastPaymentId,
        Instant createdAt,
        Instant updatedAt
) {
    @com.fasterxml.jackson.annotation.JsonProperty("billingMode")
    public String billingMode() { return "PREPAID"; }
    @com.fasterxml.jackson.annotation.JsonProperty("automaticRenewal")
    public boolean automaticRenewal() { return false; }
}
