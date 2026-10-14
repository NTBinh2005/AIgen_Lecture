package com.example.demo.service;

import java.util.UUID;

/**
 * Integration port owned by Backend 3. Backend 2 may supply an implementation
 * that validates Class assignment and an ACTIVE enrollment without exposing its DB.
 */
public interface LectureAccessGrantVerifier {
    boolean canReadPublishedLecture(Integer studentId, Long lectureId, UUID publishedVersionId);
}
