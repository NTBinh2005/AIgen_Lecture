package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
public record AdminSubscriptionGrantRequest(@NotNull Integer userId, @NotNull Integer planId,
        @Min(1) @Max(3650) Integer days) {}
