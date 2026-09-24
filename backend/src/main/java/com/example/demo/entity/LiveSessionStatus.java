package com.example.demo.entity;

/**
 * Vòng đời của một live session.
 * SCHEDULED -> OPEN -> LIVE -> ENDED; nhánh CANCELLED.
 */
public enum LiveSessionStatus {
    SCHEDULED,
    OPEN,
    LIVE,
    ENDED,
    CANCELLED
}
