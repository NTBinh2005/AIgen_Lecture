package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.AttendanceSummaryResponse;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.SessionAttendance;
import com.example.demo.entity.SessionAttendanceSource;
import com.example.demo.entity.SessionParticipant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ClassTeacherRepository;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.QrTokenRepository;
import com.example.demo.repository.SessionAttendanceRepository;
import com.example.demo.repository.SessionParticipantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.ClassAccessService;
import com.example.demo.service.Backend2EventService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LIVE-05: Điểm danh online — tổng hợp join/leave/thời lượng từ webhook.
 * LIVE-06: Điểm danh offline — QR hoặc Teacher đánh dấu thủ công.
 *
 * LIVE-BR-02: present = true khi tổng thời gian >= ngưỡng (% thời lượng buổi).
 * LIVE-BR-03: QR chỉ ghi nhận một attendance/student/session.
 * LIVE-AC-02: totalSeconds = cộng dồn tất cả khoảng join–leave hợp lệ.
 * LIVE-AC-03: QR hết hạn hoặc sai session → exception.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final LiveSessionRepository liveSessionRepository;
    private final SessionAttendanceRepository attendanceRepository;
    private final SessionParticipantRepository participantRepository;
    private final QrTokenRepository qrTokenRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassAccessService classAccessService;
    private final Backend2EventService eventService;

    @Value("${live.attendance.min-percentage:50}")
    private int minAttendancePercentage;

    // ─────────────────────────────────────────────────────────────────────────
    // ONLINE — từ webhook provider
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void recordOnlineJoin(Long sessionId, String externalParticipantId, Integer userId) {
        LiveSession session = getSession(sessionId);

        // Idempotent: nếu đã có bản ghi join chưa leave → bỏ qua
        Optional<SessionParticipant> existing =
                participantRepository.findBySession_SessionIdAndExternalParticipantIdAndLeftAtIsNull(
                        sessionId, externalParticipantId);
        if (existing.isPresent()) {
            log.info("[ATTENDANCE] Duplicate join event ignored for participant={}", externalParticipantId);
            return;
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        if (user != null && user.getRole() == UserRole.STUDENT) {
            getEnrolledStudent(session, user.getUserId());
        }

        SessionParticipant participant = new SessionParticipant();
        participant.setSession(session);
        participant.setUser(user);
        participant.setExternalParticipantId(externalParticipantId);
        participant.setJoinedAt(LocalDateTime.now());
        participantRepository.save(participant);

        log.info("[ATTENDANCE] Online join recorded: session={} participant={} user={}",
                sessionId, externalParticipantId, userId);
    }

    @Transactional
    @Override
    public void recordOnlineLeave(Long sessionId, String externalParticipantId) {
        // Idempotent: tìm bản ghi join chưa có leftAt
        Optional<SessionParticipant> participantOpt =
                participantRepository.findBySession_SessionIdAndExternalParticipantIdAndLeftAtIsNull(
                        sessionId, externalParticipantId);

        if (participantOpt.isEmpty()) {
            log.warn("[ATTENDANCE] Leave event received but no open join found for participant={}",
                    externalParticipantId);
            return;
        }

        SessionParticipant participant = participantOpt.get();
        LocalDateTime leftAt = LocalDateTime.now();
        participant.setLeftAt(leftAt);
        participantRepository.save(participant);

        // Tính duration và ghi SessionAttendance — LIVE-AC-02
        if (participant.getUser() != null
                && participant.getUser().getRole() == UserRole.STUDENT) {
            long seconds = ChronoUnit.SECONDS.between(participant.getJoinedAt(), leftAt);
            if (seconds > 0) {
                SessionAttendance attendance = new SessionAttendance();
                attendance.setSession(participant.getSession());
                attendance.setStudent(participant.getUser());
                attendance.setJoinedAt(participant.getJoinedAt());
                attendance.setLeftAt(leftAt);
                attendance.setDurationSeconds(seconds);
                attendance.setSource(SessionAttendanceSource.ONLINE);
                attendanceRepository.save(attendance);
                emitAttendanceRecorded(attendance);
                log.info("[ATTENDANCE] Online attendance: session={} user={} duration={}s",
                        sessionId, participant.getUser().getUserId(), seconds);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OFFLINE — Manual (LIVE-06)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void markManual(Long sessionId, Integer studentId, Integer teacherId) {
        LiveSession session = getSession(sessionId);
        assertSessionActive(session);
        assertIsTeacherOfClass(session, teacherId);
        User student = getEnrolledStudent(session, studentId);

        // LIVE-BR-03 (áp dụng cho manual): chỉ một lần/student/session
        boolean alreadyMarked = attendanceRepository
                .existsBySession_SessionIdAndStudent_UserIdAndSourceNot(
                        sessionId, studentId, SessionAttendanceSource.ONLINE);
        if (alreadyMarked) {
            throw new BadRequestException("Student đã được điểm danh trong buổi này");
        }

        SessionAttendance attendance = new SessionAttendance();
        attendance.setSession(session);
        attendance.setStudent(student);
        attendance.setJoinedAt(LocalDateTime.now());
        attendance.setDurationSeconds(null); // manual — không tính thời gian
        attendance.setSource(SessionAttendanceSource.MANUAL);
        attendanceRepository.save(attendance);
        emitAttendanceRecorded(attendance);

        log.info("[ATTENDANCE] Manual mark: session={} student={} by teacher={}",
                sessionId, studentId, teacherId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QR SCAN (LIVE-06, LIVE-BR-03, LIVE-AC-03)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void scanQr(Long sessionId, String code, Integer studentId) {
        LiveSession session = getSession(sessionId);
        assertSessionActive(session);

        var qrToken = qrTokenRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Mã QR không hợp lệ"));

        // LIVE-AC-03: QR thuộc buổi khác
        if (!qrToken.getSession().getSessionId().equals(sessionId)) {
            throw new BadRequestException("Mã QR không thuộc buổi học này");
        }

        // LIVE-AC-03: QR hết hạn
        if (qrToken.isExpired()) {
            throw new BadRequestException("Mã QR đã hết hạn");
        }

        User student = getEnrolledStudent(session, studentId);

        // LIVE-BR-03: chỉ ghi nhận một lần/student/session qua QR
        boolean alreadyMarked = attendanceRepository
                .existsBySession_SessionIdAndStudent_UserIdAndSourceNot(
                        sessionId, studentId, SessionAttendanceSource.ONLINE);
        if (alreadyMarked) {
            throw new BadRequestException("Bạn đã được điểm danh trong buổi này");
        }

        SessionAttendance attendance = new SessionAttendance();
        attendance.setSession(session);
        attendance.setStudent(student);
        attendance.setJoinedAt(LocalDateTime.now());
        attendance.setDurationSeconds(null);
        attendance.setSource(SessionAttendanceSource.QR);
        attendanceRepository.save(attendance);
        emitAttendanceRecorded(attendance);

        log.info("[ATTENDANCE] QR scan: session={} student={}", sessionId, studentId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUMMARY (LIVE-05)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LIVE-05: Tổng hợp attendance của tất cả student đã từng ghi nhận.
     * LIVE-BR-02: present = true khi tổng giây >= minAttendancePercentage% thời lượng buổi.
     * LIVE-AC-02: totalSeconds = cộng dồn qua nhiều lần reconnect.
     */
    @Transactional(readOnly = true)
    @Override
    public List<AttendanceSummaryResponse> getSummary(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        User currentUser = classAccessService.requireUser(currentUserId);
        boolean manager = classAccessService.canManage(session.getClassEntity(), currentUserId);
        if (!manager) {
            classAccessService.assertActiveStudent(session.getClassEntity(), currentUserId);
        }
        List<SessionAttendance> records = attendanceRepository.findBySession_SessionId(sessionId);
        if (!manager && currentUser.getRole() == UserRole.STUDENT) {
            records = records.stream()
                    .filter(record -> record.getStudent().getUserId().equals(currentUserId))
                    .toList();
        }

        long sessionDurationSeconds = ChronoUnit.SECONDS.between(
                session.getStartsAt(), session.getEndsAt());
        long minSeconds = sessionDurationSeconds * minAttendancePercentage / 100;

        // Gom theo studentId
        Map<Integer, StudentAccumulator> accMap = new LinkedHashMap<>();
        for (SessionAttendance rec : records) {
            Integer sid = rec.getStudent().getUserId();
            accMap.computeIfAbsent(sid, k -> new StudentAccumulator(rec.getStudent()))
                    .add(rec);
        }

        List<AttendanceSummaryResponse> result = new ArrayList<>();
        for (StudentAccumulator acc : accMap.values()) {
            long total = acc.totalSeconds;
            boolean present = acc.hasNonOnline || total >= minSeconds;
            result.add(new AttendanceSummaryResponse(
                    acc.student.getUserId(),
                    acc.student.getName(),
                    total,
                    present,
                    acc.primarySource
            ));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private LiveSession getSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession not found: " + sessionId));
    }

    private void assertSessionActive(LiveSession session) {
        if (session.getStatus() == LiveSessionStatus.CANCELLED) {
            throw new BadRequestException("Session đã bị hủy");
        }
        if (session.getStatus() == LiveSessionStatus.ENDED) {
            throw new BadRequestException("Session đã kết thúc");
        }
    }

    private void assertIsTeacherOfClass(LiveSession session, Integer teacherId) {
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + teacherId));
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        Integer classId = session.getClassEntity().getClassId();
        boolean isTeacher = session.getClassEntity().getTeacher().getUserId().equals(teacherId)
                || classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(classId, teacherId);
        if (!isTeacher) {
            throw new AccessDeniedException("Bạn không phải là giáo viên của lớp này");
        }
    }

    private User getEnrolledStudent(LiveSession session, Integer studentId) {
        return classStudentRepository
                .findByClassEntity_ClassIdAndStudent_UserIdAndStatusIn(
                        session.getClassEntity().getClassId(),
                        studentId,
                        List.of(EnrollmentStatus.ACTIVE))
                .map(cs -> {
                    // Kiểm tra role
                    if (cs.getStudent().getRole() != UserRole.STUDENT) {
                        throw new BadRequestException("User không phải là student");
                    }
                    return cs.getStudent();
                })
                .orElseThrow(() -> new BadRequestException(
                        "Student chưa ghi danh hoặc enrollment không ACTIVE trong lớp này"));
    }

    /** Accumulator tạm cho tổng hợp attendance summary. */
    private void emitAttendanceRecorded(SessionAttendance attendance) {
        eventService.emit("AttendanceRecorded", "LiveSession",
                attendance.getSession().getSessionId(),
                Map.of("sessionId", attendance.getSession().getSessionId(),
                        "classId", attendance.getSession().getClassEntity().getClassId(),
                        "studentId", attendance.getStudent().getUserId(),
                        "source", attendance.getSource().name()));
    }

    private static class StudentAccumulator {
        final User student;
        long totalSeconds = 0;
        boolean hasNonOnline = false;
        String primarySource = "ONLINE";

        StudentAccumulator(User student) {
            this.student = student;
        }

        void add(SessionAttendance rec) {
            if (rec.getSource() != SessionAttendanceSource.ONLINE) {
                hasNonOnline = true;
                primarySource = rec.getSource().name();
            } else if (rec.getDurationSeconds() != null) {
                totalSeconds += rec.getDurationSeconds();
            }
        }
    }
}
