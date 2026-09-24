package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.LiveSessionCancelRequest;
import com.example.demo.dto.request.LiveSessionCreateRequest;
import com.example.demo.dto.request.LiveSessionUpdateRequest;
import com.example.demo.dto.response.JoinTokenResponse;
import com.example.demo.dto.response.LiveSessionResponse;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.LiveSessionType;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ClassTeacherRepository;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.LiveSessionService;
import com.example.demo.service.ClassAccessService;
import com.example.demo.service.Backend2EventService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * LIVE-01: Lập lịch buổi học.
 * LIVE-02: Cấp join token ngắn hạn.
 * LIVE-08: Đổi hoặc hủy lịch, gửi notification.
 *
 * LIVE-BR-01: Teacher chỉ tạo session cho Class mình phụ trách;
 *             Student phải có Enrollment ACTIVE.
 */
@Slf4j
@Service
public class LiveSessionServiceImpl implements LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassAccessService classAccessService;
    private final Backend2EventService eventService;

    private final SecretKey signingKey;
    private final long joinTokenExpirySeconds;
    private final boolean allowOverlap;

    public LiveSessionServiceImpl(
            LiveSessionRepository liveSessionRepository,
            ClassRepository classRepository,
            UserRepository userRepository,
            ClassStudentRepository classStudentRepository,
            ClassTeacherRepository classTeacherRepository,
            ClassAccessService classAccessService,
            Backend2EventService eventService,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${live.join-token.expiry-seconds:300}") long joinTokenExpirySeconds,
            @Value("${live.schedule.allow-overlap:false}") boolean allowOverlap) {
        this.liveSessionRepository = liveSessionRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.classStudentRepository = classStudentRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classAccessService = classAccessService;
        this.eventService = eventService;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.joinTokenExpirySeconds = joinTokenExpirySeconds;
        this.allowOverlap = allowOverlap;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE (LIVE-01)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public LiveSessionResponse create(LiveSessionCreateRequest request, Integer currentUserId) {
        User currentUser = getUser(currentUserId);
        ClassEntity classEntity = getClass(request.classId());

        // LIVE-BR-01: Teacher chỉ tạo session cho Class mình phụ trách
        assertCanManageSession(classEntity, currentUser);
        if (classEntity.getStatus() != com.example.demo.entity.ClassStatus.ACTIVE) {
            throw new BadRequestException("Sessions can only be created for an ACTIVE class");
        }

        validateTimeRange(request.startsAt(), request.endsAt());

        // OFFLINE phải có location
        if (request.type() == LiveSessionType.OFFLINE && !StringUtils.hasText(request.location())) {
            throw new BadRequestException("location bắt buộc khi type = OFFLINE");
        }

        // Kiểm tra trùng lịch
        checkOverlap(request.classId(), null, request.startsAt(), request.endsAt());

        LiveSession session = new LiveSession();
        session.setClassEntity(classEntity);
        session.setCreatedBy(currentUser);
        session.setTitle(requireText(request.title(), "title"));
        session.setType(request.type());
        session.setStartsAt(request.startsAt());
        session.setEndsAt(request.endsAt());
        session.setLocation(trimNullable(request.location()));
        session.setMeetingUrl(trimNullable(request.meetingUrl()));
        session.setExternalMeetingId(trimNullable(request.externalMeetingId()));

        return LiveSessionResponse.from(liveSessionRepository.save(session));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public LiveSessionResponse findById(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        classAccessService.assertCanView(session.getClassEntity(), currentUserId);
        return LiveSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    @Override
    public List<LiveSessionResponse> findByClass(Integer classId, Integer currentUserId) {
        ClassEntity classEntity = getClass(classId);
        classAccessService.assertCanView(classEntity, currentUserId);
        return liveSessionRepository
                .findByClassEntity_ClassIdOrderByStartsAtAsc(classId)
                .stream()
                .map(LiveSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<LiveSessionResponse> findMine(Integer currentUserId) {
        User user = getUser(currentUserId);
        if (user.getRole() == UserRole.ADMIN) {
            return liveSessionRepository.findAllByOrderByStartsAtAsc().stream()
                    .map(LiveSessionResponse::from).toList();
        }
        List<Integer> classIds;
        if (user.getRole() == UserRole.TEACHER) {
            classIds = classRepository.findManagedByTeacher(currentUserId).stream()
                    .map(ClassEntity::getClassId).toList();
        } else {
            classIds = classStudentRepository.findByStudent_UserIdAndStatusIn(currentUserId,
                            List.of(EnrollmentStatus.ACTIVE)).stream()
                    .map(e -> e.getClassEntity().getClassId()).toList();
        }
        if (classIds.isEmpty()) {
            return List.of();
        }
        return liveSessionRepository.findByClassEntity_ClassIdInOrderByStartsAtAsc(classIds).stream()
                .map(LiveSessionResponse::from).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE (LIVE-08)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public LiveSessionResponse update(Long sessionId, LiveSessionUpdateRequest request,
                                      Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        User currentUser = getUser(currentUserId);
        assertCanManageSession(session.getClassEntity(), currentUser);

        if (session.getStatus() == LiveSessionStatus.CANCELLED
                || session.getStatus() == LiveSessionStatus.ENDED) {
            throw new BadRequestException("Không thể cập nhật session đã ENDED hoặc CANCELLED");
        }

        LocalDateTime startsAt = request.startsAt() != null ? request.startsAt() : session.getStartsAt();
        LocalDateTime endsAt   = request.endsAt()   != null ? request.endsAt()   : session.getEndsAt();
        validateTimeRange(startsAt, endsAt);

        if (request.startsAt() != null || request.endsAt() != null) {
            checkOverlap(session.getClassEntity().getClassId(), sessionId, startsAt, endsAt);
        }

        if (request.title() != null)             session.setTitle(requireText(request.title(), "title"));
        if (request.type() != null)              session.setType(request.type());
        if (request.startsAt() != null)          session.setStartsAt(startsAt);
        if (request.endsAt() != null)            session.setEndsAt(endsAt);
        if (request.location() != null)          session.setLocation(trimNullable(request.location()));
        if (request.meetingUrl() != null)        session.setMeetingUrl(trimNullable(request.meetingUrl()));
        if (request.externalMeetingId() != null) session.setExternalMeetingId(trimNullable(request.externalMeetingId()));

        // LIVE-AC-04: gửi thông báo đổi lịch
        notifyParticipants(session, "Lịch buổi học đã được cập nhật");

        return LiveSessionResponse.from(session);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCEL (LIVE-08 nhánh CANCELLED)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public LiveSessionResponse cancel(Long sessionId, LiveSessionCancelRequest request,
                                      Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        User currentUser = getUser(currentUserId);
        assertCanManageSession(session.getClassEntity(), currentUser);

        if (session.getStatus() == LiveSessionStatus.CANCELLED) {
            throw new BadRequestException("Session đã bị hủy trước đó");
        }
        if (session.getStatus() == LiveSessionStatus.ENDED) {
            throw new BadRequestException("Không thể hủy session đã ENDED");
        }

        session.setStatus(LiveSessionStatus.CANCELLED);
        session.setCancelledAt(LocalDateTime.now());
        session.setCancelReason(trimNullable(request != null ? request.cancelReason() : null));

        // LIVE-AC-04: gửi thông báo hủy lịch
        notifyParticipants(session, "Buổi học đã bị hủy: " +
                (session.getCancelReason() != null ? session.getCancelReason() : ""));

        return LiveSessionResponse.from(session);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE TRANSITIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public LiveSessionResponse open(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        assertCanManageSession(session.getClassEntity(), getUser(currentUserId));
        assertTransition(session, LiveSessionStatus.SCHEDULED, LiveSessionStatus.OPEN);
        session.setStatus(LiveSessionStatus.OPEN);
        return LiveSessionResponse.from(session);
    }

    @Transactional
    @Override
    public LiveSessionResponse goLive(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        assertCanManageSession(session.getClassEntity(), getUser(currentUserId));
        assertTransition(session, LiveSessionStatus.OPEN, LiveSessionStatus.LIVE);
        session.setStatus(LiveSessionStatus.LIVE);
        return LiveSessionResponse.from(session);
    }

    @Transactional
    @Override
    public LiveSessionResponse end(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);
        assertCanManageSession(session.getClassEntity(), getUser(currentUserId));
        assertTransition(session, LiveSessionStatus.LIVE, LiveSessionStatus.ENDED);
        session.setStatus(LiveSessionStatus.ENDED);
        return LiveSessionResponse.from(session);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JOIN TOKEN (LIVE-02)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LIVE-02: Cấp join token ngắn hạn.
     * LIVE-AC-01: Token gắn với đúng userId + sessionId + thời hạn.
     * Exception: session CANCELLED → 403.
     */
    @Transactional(readOnly = true)
    @Override
    public JoinTokenResponse joinSession(Long sessionId, Integer currentUserId) {
        LiveSession session = getSession(sessionId);

        if (session.getStatus() == LiveSessionStatus.CANCELLED) {
            throw new AccessDeniedException("Session đã bị hủy, không thể tham gia");
        }
        if (session.getStatus() == LiveSessionStatus.ENDED) {
            throw new BadRequestException("Session đã kết thúc");
        }

        if (session.getStatus() != LiveSessionStatus.OPEN
                && session.getStatus() != LiveSessionStatus.LIVE) {
            throw new AccessDeniedException("Session is not open for joining");
        }

        User user = getUser(currentUserId);
        ClassEntity classEntity = session.getClassEntity();

        // LIVE-BR-01: Student phải có enrollment ACTIVE
        if (user.getRole() == UserRole.STUDENT) {
            boolean enrolled = classStudentRepository
                    .findByClassEntity_ClassIdAndStudent_UserIdAndStatusIn(
                            classEntity.getClassId(), currentUserId,
                            List.of(EnrollmentStatus.ACTIVE))
                    .isPresent();
            if (!enrolled) {
                throw new AccessDeniedException("Student chưa ghi danh hoặc enrollment không ACTIVE");
            }
        }

        // LIVE-BR-01: Teacher phải phụ trách class
        if (user.getRole() == UserRole.TEACHER) {
            boolean isTeacher = classEntity.getTeacher().getUserId().equals(currentUserId)
                    || classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(
                            classEntity.getClassId(), currentUserId);
            if (!isTeacher) {
                throw new AccessDeniedException("Teacher không phụ trách class này");
            }
        }

        // LIVE-AC-01: Tạo join token ngắn hạn
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(joinTokenExpirySeconds);
        Date expiryDate = new Date(System.currentTimeMillis() + joinTokenExpirySeconds * 1000);

        String token = Jwts.builder()
                .subject(currentUserId.toString())
                .claim("sessionId", sessionId)
                .claim("type", "JOIN_TOKEN")
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();

        return new JoinTokenResponse(token, session.getMeetingUrl(), expiresAt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private LiveSession getSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession not found: " + sessionId));
    }

    private ClassEntity getClass(Integer classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    /**
     * LIVE-BR-01: Teacher chỉ tạo session cho Class mình phụ trách; Admin có toàn quyền.
     */
    private void assertCanManageSession(ClassEntity classEntity, User user) {
        if (user.getRole() == UserRole.ADMIN) return;
        if (user.getRole() == UserRole.TEACHER) {
            boolean isMainTeacher = classEntity.getTeacher().getUserId().equals(user.getUserId());
            boolean isCoTeacher = classTeacherRepository
                    .existsByClassEntity_ClassIdAndTeacher_UserId(
                            classEntity.getClassId(), user.getUserId());
            if (isMainTeacher || isCoTeacher) return;
        }
        throw new AccessDeniedException("Bạn không có quyền quản lý session của lớp học này");
    }

    private void assertTransition(LiveSession session,
                                  LiveSessionStatus expected,
                                  LiveSessionStatus target) {
        if (session.getStatus() != expected) {
            throw new BadRequestException(
                    "Session phải ở trạng thái " + expected + " để chuyển sang " + target +
                    " (hiện tại: " + session.getStatus() + ")");
        }
    }

    private void validateTimeRange(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new BadRequestException("startsAt và endsAt không được để trống");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new BadRequestException("endsAt phải sau startsAt");
        }
    }

    /**
     * Kiểm tra trùng lịch trong cùng class (trừ session hiện tại nếu đang update).
     * Chính sách do live.schedule.allow-overlap cấu hình.
     */
    private void checkOverlap(Integer classId, Long excludeSessionId,
                               LocalDateTime startsAt, LocalDateTime endsAt) {
        List<LiveSession> overlapping = liveSessionRepository
                .findByClassEntity_ClassIdAndStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
                        classId, LiveSessionStatus.CANCELLED, endsAt, startsAt);

        overlapping = overlapping.stream()
                .filter(s -> excludeSessionId == null || !s.getSessionId().equals(excludeSessionId))
                .toList();

        if (!overlapping.isEmpty()) {
            String msg = "Lịch buổi học bị trùng với session #" + overlapping.get(0).getSessionId();
            if (!allowOverlap) {
                throw new BadRequestException(msg);
            }
            log.warn("[LIVE-OVERLAP] {}", msg);
        }
    }

    /**
     * LIVE-AC-04: Gửi thông báo khi đổi hoặc hủy lịch.
     * Stub: log message — tích hợp NotificationService/Email sau.
     */
    private void notifyParticipants(LiveSession session, String message) {
        log.info("[LIVE-NOTIFY] session={} class={} msg={}",
                session.getSessionId(), session.getClassEntity().getClassId(), message);
        eventService.emit("LiveSessionChanged", "LiveSession", session.getSessionId(),
                java.util.Map.of(
                        "classId", session.getClassEntity().getClassId(),
                        "status", session.getStatus().name(),
                        "message", message,
                        "notifyActiveEnrollments", true));
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}
