package com.example.demo.dto.response;

import com.example.demo.entity.SessionParticipant;
import com.example.demo.entity.SessionParticipantRole;
import java.time.LocalDateTime;

public record SessionParticipantResponse(
        Long participantId,
        Long sessionId,
        Integer userId,
        String userName,
        String externalParticipantId,
        SessionParticipantRole role,
        boolean muted,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        LocalDateTime removedAt
) {
    public static SessionParticipantResponse from(SessionParticipant value) {
        return new SessionParticipantResponse(
                value.getParticipantId(), value.getSession().getSessionId(),
                value.getUser() == null ? null : value.getUser().getUserId(),
                value.getUser() == null ? null : value.getUser().getName(),
                value.getExternalParticipantId(), value.getRole(), value.isMuted(),
                value.getJoinedAt(), value.getLeftAt(), value.getRemovedAt());
    }
}
