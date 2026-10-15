package com.example.demo.common.exception;

import org.springframework.security.access.AccessDeniedException;

/** A premium feature was requested without an active subscription. */
public class SubscriptionRequiredException extends AccessDeniedException {
    public SubscriptionRequiredException(String message) {
        super(message);
    }
}
