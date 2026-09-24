package com.example.demo.service;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.ParticipantActionRequest;
import com.example.demo.dto.response.SessionParticipantResponse;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.SessionParticipant;
import com.example.demo.entity.SessionParticipantRole;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.SessionParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantService {
    private final LiveSessionRepository liveSessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final ClassAccessService classAccessService;

    @Transactional(readOnly = true)
    public List<SessionParticipantResponse> findAll(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanManage(session.getClassEntity(), currentUserId);
        return participantRepository.findBySession_SessionId(sessionId).stream()
                .map(SessionParticipantResponse::from).toList();
    }

    @Transactional
    public SessionParticipantResponse applyAction(Long sessionId, Long participantId,
                                                  ParticipantActionRequest.Action action,
                                                  Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanManage(session.getClassEntity(), currentUserId);
        if (session.getStatus() != LiveSessionStatus.OPEN
                && session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BadRequestException("Participant management requires an OPEN or LIVE session");
        }
        SessionParticipant participant = participantRepository
                .findByParticipantIdAndSession_SessionId(participantId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + participantId));
        switch (action) {
            case MUTE -> participant.setMuted(true);
            case UNMUTE -> participant.setMuted(false);
            case REMOVE -> {
                participant.setRemovedAt(LocalDateTime.now());
                if (participant.getLeftAt() == null) participant.setLeftAt(LocalDateTime.now());
            }
            case GRANT_PRESENTER -> participant.setRole(SessionParticipantRole.PRESENTER);
            case REVOKE_PRESENTER -> participant.setRole(SessionParticipantRole.ATTENDEE);
        }
        return SessionParticipantResponse.from(participant);
    }

    private LiveSession getSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession not found: " + sessionId));
    }
}
