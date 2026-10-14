package com.example.demo.dto.response;

import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.LiveSessionType;
import java.time.LocalDateTime;

public record LiveSessionResponse(
        Long sessionId,
        Integer classId,
        String className,
        Integer createdByUserId,
        String createdByName,
        String title,
        LiveSessionType type,
        LiveSessionStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String location,
        String meetingUrl,
        String externalMeetingId,
        String cancelReason,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LiveSessionResponse from(LiveSession s) {
        return new LiveSessionResponse(
                s.getSessionId(),
                s.getClassEntity().getClassId(),
                s.getClassEntity().getClassName(),
                s.getCreatedBy().getUserId(),
                s.getCreatedBy().getName(),
                s.getTitle(),
                s.getType(),
                s.getStatus(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getLocation(),
                s.getMeetingUrl(),
                s.getExternalMeetingId(),
                s.getCancelReason(),
                s.getCancelledAt(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
