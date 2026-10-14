package com.example.demo.entity;

/**
 * Business purpose of a stored asset. Keeping this on the asset prevents a
 * source document from being accidentally served as a generated artifact.
 */
public enum AssetPurpose {
    LECTURE_SOURCE,
    PRESENTATION_SOURCE,
    PRESENTATION_IMAGE,
    PRESENTATION_EXPORT,
    OTHER
}
