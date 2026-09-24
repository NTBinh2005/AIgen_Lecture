package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * LIVE-BR-05: Payload webhook nhận từ video provider bên ngoài.
 * Các eventType điển hình: meeting.started, meeting.ended,
 * participant.joined, participant.left, recording.ready, recording.failed.
 *
 * Xác thực bằng HMAC-SHA256 (header X-Webhook-Signature).
 * Phải xử lý idempotent.
 */
public record WebhookEventRequest(

        @NotBlank(message = "eventType không được để trống")
        String eventType,

        /** ID phòng/meeting bên phía provider. */
        String externalMeetingId,

        /** ID participant (cho sự kiện join/leave). */
        String externalParticipantId,

        /** ID recording (cho sự kiện recording.*). */
        String externalRecordingId,

        @NotNull(message = "timestamp không được để trống")
        Long timestamp,

        /** Payload mở rộng tuỳ provider (playbackUrl, fileSize, v.v.). */
        Map<String, Object> payload
) {}
