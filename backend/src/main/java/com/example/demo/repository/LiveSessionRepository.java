package com.example.demo.repository;

import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {
    @org.springframework.data.jpa.repository.Query("select s.classEntity.classId from LiveSession s where s.sessionId = :id")
    Optional<Integer> findClassIdBySessionId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from LiveSession s where s.sessionId = :id")
    Optional<LiveSession> lockBySessionId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from LiveSession s where s.externalMeetingId = :id")
    Optional<LiveSession> lockByExternalMeetingId(@org.springframework.data.repository.query.Param("id") String id);
    java.util.List<LiveSession> findByStatusInAndStartsAtBetween(java.util.List<LiveSessionStatus> statuses, java.time.LocalDateTime from, java.time.LocalDateTime to);


    List<LiveSession> findByClassEntity_ClassIdOrderByStartsAtAsc(Integer classId);

    List<LiveSession> findByClassEntity_ClassIdInOrderByStartsAtAsc(List<Integer> classIds);

    List<LiveSession> findAllByOrderByStartsAtAsc();

    Optional<LiveSession> findByExternalMeetingId(String externalMeetingId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<LiveSession> findByStatusInAndEndsAtBefore(
            List<LiveSessionStatus> statuses, java.time.LocalDateTime now);

    /** Kiểm tra class có session đang OPEN hoặc LIVE không (dùng khi đóng class). */
    boolean existsByClassEntity_ClassIdAndStatusIn(
            Integer classId, List<LiveSessionStatus> statuses);

    /** Phát hiện trùng lịch trong một class (overlap check). */
    List<LiveSession> findByClassEntity_ClassIdAndStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
            Integer classId,
            LiveSessionStatus excludeStatus,
            java.time.LocalDateTime endsAt,
            java.time.LocalDateTime startsAt);
}
