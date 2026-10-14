package com.example.demo.dto.request;

import com.example.demo.entity.RefundStatus;
import jakarta.validation.constraints.NotNull;

public record RefundDecisionRequest(@NotNull RefundStatus status) {}
