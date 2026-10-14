package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.QrTokenResponse;
import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import com.example.demo.entity.QrToken;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassTeacherRepository;
import com.example.demo.repository.LiveSessionRepository;
import com.example.demo.repository.QrTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.QrService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LIVE-06: Tạo QR code ngắn hạn cho điểm danh offline.
 * LIVE-BR-03: QR chứa mã ngẫu nhiên có thời hạn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrServiceImpl implements QrService {

    private final QrTokenRepository qrTokenRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final UserRepository userRepository;
    private final ClassTeacherRepository classTeacherRepository;

    @Value("${live.qr.expiry-seconds:600}")
    private long qrExpirySeconds;

    @Transactional
    @Override
    public QrTokenResponse generateQr(Long sessionId, Integer teacherId) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession not found: " + sessionId));

        // Session phải đang active (OPEN hoặc LIVE)
        if (session.getStatus() == LiveSessionStatus.CANCELLED
                || session.getStatus() == LiveSessionStatus.ENDED) {
            throw new BadRequestException("Không thể tạo QR cho session đã ENDED hoặc CANCELLED");
        }

        // Kiểm tra Teacher phụ trách class
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + teacherId));
        assertIsTeacherOfClass(session, teacher);

        QrToken qrToken = new QrToken();
        qrToken.setSession(session);
        qrToken.setCode(UUID.randomUUID().toString().replace("-", ""));
        qrToken.setExpiresAt(LocalDateTime.now().plusSeconds(qrExpirySeconds));

        QrToken saved = qrTokenRepository.save(qrToken);
        log.info("[QR] Generated QR for session={} expires={}", sessionId, saved.getExpiresAt());
        return QrTokenResponse.from(saved);
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
