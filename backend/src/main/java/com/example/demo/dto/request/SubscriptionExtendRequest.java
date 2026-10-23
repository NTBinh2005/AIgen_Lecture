package com.example.demo.dto.request;
import jakarta.validation.constraints.*;
public record SubscriptionExtendRequest(@NotNull @Min(1) @Max(3650) Integer days) {}
