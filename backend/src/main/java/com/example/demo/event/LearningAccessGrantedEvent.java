package com.example.demo.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published sau khi LearningAccess được tạo thành công.
 *
 * Các service khác (Course Service, v.v.) có thể consume event này
 * để cập nhật quyền học theo đặc tả task.md §11.3.
 *
 * eventId: UUID unique cho mỗi event instance để hỗ trợ idempotency cho consumer.
 *
 * Ví dụ payload:
 * {
 *   "eventId": "a7b3c2d1-e5f6-4a8b-9c0d-1e2f3a4b5c6d",
 *   "eventType": "LearningAccessGranted",
 *   "userId": 123,
 *   "productId": 456,
 *   "paymentId": 789,
 *   "accessType": "PREMIUM",
 *   "grantedAt": "2026-09-15T10:00:00Z",
 *   "expiresAt": "2026-12-15T00:00:00Z"
 * }
 */
public record LearningAccessGrantedEvent(
        String eventId,
        String eventType,
        Integer userId,
        Integer productId,
        Integer paymentId,
        String accessType,
        Instant grantedAt,
        Instant expiresAt
) {
    /** Convenience constructor — tự sinh UUID cho eventId và eventType là "LearningAccessGranted" */
    public LearningAccessGrantedEvent(Integer userId, Integer productId, Integer paymentId,
                                      String accessType, Instant expiresAt) {
        this(UUID.randomUUID().toString(), "LearningAccessGranted", userId, productId, paymentId,
                accessType, Instant.now(), expiresAt);
    }
}
