package com.example.demo.dto.response;

import com.example.demo.entity.RecordingStatus;
import com.example.demo.entity.SessionRecording;
import java.time.LocalDateTime;

/**
 * LIVE-07: Metadata và trạng thái recording từ provider.
 * LIVE-BR-04: Quyền xem kế thừa Class — controller kiểm tra.
 */
public record RecordingResponse(
        Long recordingId,
        Long sessionId,
        String externalRecordingId,
        RecordingStatus status,
        String playbackUrl,
        Long durationSeconds,
        Long fileSizeBytes,
        boolean consentRequired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RecordingResponse from(SessionRecording r) {
        return new RecordingResponse(
                r.getRecordingId(),
                r.getSession().getSessionId(),
                r.getExternalRecordingId(),
                r.getStatus(),
                r.getPlaybackUrl(),
                r.getDurationSeconds(),
                r.getFileSizeBytes(),
                r.isConsentRequired(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
