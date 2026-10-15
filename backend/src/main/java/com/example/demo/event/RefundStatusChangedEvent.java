package com.example.demo.event;

import com.example.demo.entity.RefundStatus;

public record RefundStatusChangedEvent(Integer refundId, Integer userId, RefundStatus status) {}
