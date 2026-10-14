package com.example.demo.dto.request;

import com.example.demo.entity.BillingCycle;
import com.example.demo.entity.UserRole;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record SubscriptionPlanRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @NotNull BillingCycle billingCycle,
        @Positive Integer durationDays,
        @NotNull UserRole targetRole,
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 200) String> features,
        Boolean active
) {}
