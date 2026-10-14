package com.example.demo.repository;

import com.example.demo.entity.SessionAttendance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, Long> {

    List<SessionAttendance> findBySession_SessionId(Long sessionId);

    List<SessionAttendance> findBySession_SessionIdAndStudent_UserId(Long sessionId, Integer userId);

    /** Kiểm tra student đã có attendance QR/MANUAL trong session chưa (LIVE-BR-03). */
    boolean existsBySession_SessionIdAndStudent_UserIdAndSourceNot(
            Long sessionId,
            Integer userId,
            com.example.demo.entity.SessionAttendanceSource source);

    /** Tổng thời gian tham dự (giây) của student trong một session. */
    @Query("SELECT COALESCE(SUM(a.durationSeconds), 0) FROM SessionAttendance a " +
           "WHERE a.session.sessionId = :sessionId AND a.student.userId = :userId " +
           "AND a.durationSeconds IS NOT NULL")
    Long sumDurationBySessionAndStudent(
            @Param("sessionId") Long sessionId,
            @Param("userId") Integer userId);
}
