package com.example.demo.entity;

/**
 * Workloads owned by Backend 3. Quiz generation deliberately lives outside
 * this enum because it is owned by Backend 4.
 */
public enum JobType {
    LECTURE_GENERATION,
    VIDEO_GENERATION,
    PRESENTATION_GENERATION,
    PRESENTATION_EXPORT
}
