package com.example.demo.repository;

import com.example.demo.entity.SessionParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    List<SessionParticipant> findBySession_SessionId(Long sessionId);

    Optional<SessionParticipant> findBySession_SessionIdAndExternalParticipantId(
            Long sessionId, String externalParticipantId);

    /** Tìm khoảng join chưa có leftAt — dùng khi nhận sự kiện leave. */
    Optional<SessionParticipant> findBySession_SessionIdAndExternalParticipantIdAndLeftAtIsNull(
            Long sessionId, String externalParticipantId);

    /** Kiểm tra user đã join session chưa (dùng cho quản lý người tham gia). */
    boolean existsBySession_SessionIdAndUser_UserId(Long sessionId, Integer userId);

    Optional<SessionParticipant> findByParticipantIdAndSession_SessionId(Long participantId, Long sessionId);
}
