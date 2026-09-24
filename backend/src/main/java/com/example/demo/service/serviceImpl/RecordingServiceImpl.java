package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.RecordingResponse;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.RecordingStatus;
import com.example.demo.entity.SessionRecording;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassTeacherRepository;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.SessionRecordingRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RecordingService;
import com.example.demo.service.ClassAccessService;
import com.example.demo.service.Backend2EventService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LIVE-07: Ghi hình — nhận metadata và trạng thái recording từ provider.
 * LIVE-BR-04: Recording yêu cầu thông báo và đồng ý; quyền xem kế thừa Class.
 * LIVE-BR-05: Webhook phải idempotent (không update nếu đã READY/FAILED).
 * LIVE-BR-06: MVP không lưu media trực tiếp — chỉ lưu metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingServiceImpl implements RecordingService {

    private final SessionRecordingRepository recordingRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final UserRepository userRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassAccessService classAccessService;
    private final Backend2EventService eventService;

    // ─────────────────────────────────────────────────────────────────────────
    // REQUEST RECORDING (LIVE-07)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public RecordingResponse requestRecording(Long sessionId, Integer teacherId) {
        LiveSession session = getSession(sessionId);

        if (session.getStatus() != LiveSessionStatus.LIVE
                && session.getStatus() != LiveSessionStatus.OPEN) {
            throw new BadRequestException("Chỉ có thể yêu cầu ghi hình khi session đang OPEN hoặc LIVE");
        }

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + teacherId));
        assertIsTeacherOfClass(session, teacher);

        // Idempotent: nếu đã có recording đang xử lý thì trả về luôn
        var existing = recordingRepository.findBySession_SessionId(sessionId);
        if (existing.isPresent()) {
            RecordingStatus existingStatus = existing.get().getStatus();
            if (existingStatus == RecordingStatus.REQUESTED
                    || existingStatus == RecordingStatus.PROCESSING) {
                log.info("[RECORDING] Already requested for session={}", sessionId);
                return RecordingResponse.from(existing.get());
            }
            if (existingStatus == RecordingStatus.READY) {
                throw new BadRequestException("Session này đã có recording sẵn sàng");
            }
        }

        SessionRecording recording = new SessionRecording();
        recording.setSession(session);
        recording.setStatus(RecordingStatus.REQUESTED);
        recording.setConsentRequired(true); // LIVE-BR-04
        recording.setExternalRecordingId("rec-" + UUID.randomUUID());

        SessionRecording saved = recordingRepository.save(recording);

        eventService.emit("RecordingRequested", "LiveSession", sessionId,
                Map.of("sessionId", sessionId,
                        "externalRecordingId", saved.getExternalRecordingId(),
                        "consentRequired", saved.isConsentRequired()));

        // Stub: gọi provider API ở đây (ngoài MVP)
        log.info("[RECORDING] Recording requested for session={}", sessionId);

        return RecordingResponse.from(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET RECORDING (LIVE-07)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public RecordingResponse getRecording(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanView(session.getClassEntity(), currentUserId);
        return recordingRepository.findBySession_SessionId(sessionId)
                .map(RecordingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không có recording nào cho session: " + sessionId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WEBHOOK UPDATE — Idempotent (LIVE-BR-05)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LIVE-BR-05: Xử lý idempotent.
     * Không update nếu status đã là READY hoặc FAILED (terminal states).
     */
    @Transactional
    @Override
    public void handleWebhookUpdate(String externalRecordingId, RecordingStatus newStatus,
                                     String playbackUrl, Long durationSeconds, Long fileSizeBytes) {
        var recordingOpt = recordingRepository.findByExternalRecordingId(externalRecordingId);
        if (recordingOpt.isEmpty()) {
            log.warn("[RECORDING] Webhook for unknown externalRecordingId={}", externalRecordingId);
            return;
        }

        SessionRecording recording = recordingOpt.get();

        // Idempotent: không quay lại trạng thái terminal
        if (recording.getStatus() == RecordingStatus.READY
                || recording.getStatus() == RecordingStatus.FAILED) {
            log.info("[RECORDING] Ignoring webhook — already in terminal status={} for externalId={}",
                    recording.getStatus(), externalRecordingId);
            return;
        }

        recording.setStatus(newStatus);
        if (playbackUrl != null)     recording.setPlaybackUrl(playbackUrl);
        if (durationSeconds != null) recording.setDurationSeconds(durationSeconds);
        if (fileSizeBytes != null)   recording.setFileSizeBytes(fileSizeBytes);

        recordingRepository.save(recording);
        log.info("[RECORDING] Updated externalId={} status={}", externalRecordingId, newStatus);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private LiveSession getSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession not found: " + sessionId));
    }

    private void assertIsTeacherOfClass(LiveSession session, User user) {
        if (user.getRole() == UserRole.ADMIN) return;
        Integer classId = session.getClassEntity().getClassId();
        boolean isTeacher = session.getClassEntity().getTeacher().getUserId().equals(user.getUserId())
                || classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(
                        classId, user.getUserId());
        if (!isTeacher) {
            throw new AccessDeniedException("Bạn không phải là giáo viên của lớp này");
        }
    }
}
