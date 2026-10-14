package com.example.demo.service;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.SessionResourceRequest;
import com.example.demo.dto.response.SessionResourceResponse;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.SessionResource;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.SessionResourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionResourceService {
    private final LiveSessionRepository liveSessionRepository;
    private final SessionResourceRepository resourceRepository;
    private final ClassAccessService classAccessService;

    @Transactional(readOnly = true)
    public List<SessionResourceResponse> findAll(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanView(session.getClassEntity(), currentUserId);
        return resourceRepository.findBySession_SessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(SessionResourceResponse::from).toList();
    }

    @Transactional
    public SessionResourceResponse add(Long sessionId, SessionResourceRequest request,
                                       Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanManage(session.getClassEntity(), currentUserId);
        if (session.getStatus() == LiveSessionStatus.CANCELLED
                || session.getStatus() == LiveSessionStatus.ENDED) {
            throw new BadRequestException("Resources cannot be changed after a session ends or is cancelled");
        }
        SessionResource value = resourceRepository
                .findBySession_SessionIdAndResourceTypeAndResourceId(
                        sessionId, request.resourceType(), request.resourceId())
                .orElseGet(SessionResource::new);
        value.setSession(session);
        value.setResourceType(request.resourceType());
        value.setResourceId(request.resourceId());
        value.setTitle(request.title() == null ? null : request.title().trim());
        value.setDownloadAllowed(request.downloadAllowed());
        return SessionResourceResponse.from(resourceRepository.save(value));
    }

    @Transactional
    public void remove(Long sessionId, Long resourceLinkId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanManage(session.getClassEntity(), currentUserId);
        SessionResource value = resourceRepository.findByIdAndSession_SessionId(resourceLinkId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session resource not found"));
        resourceRepository.delete(value);
    }

    private LiveSession getSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession not found: " + sessionId));
    }
}
