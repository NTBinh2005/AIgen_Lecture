package com.example.demo.repository;

import com.example.demo.entity.LiveSession;
import com.example.demo.entity.LiveSessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findByClassEntity_ClassIdOrderByStartsAtAsc(Integer classId);

    List<LiveSession> findByClassEntity_ClassIdInOrderByStartsAtAsc(List<Integer> classIds);

    List<LiveSession> findAllByOrderByStartsAtAsc();

    Optional<LiveSession> findByExternalMeetingId(String externalMeetingId);

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
