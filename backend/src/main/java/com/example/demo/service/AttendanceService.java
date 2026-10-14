package com.example.demo.service;

import com.example.demo.dto.response.AttendanceSummaryResponse;
import java.util.List;

/**
 * LIVE-05: Điểm danh online — tổng hợp join/leave/thời lượng.
 * LIVE-06: Điểm danh offline — QR hoặc manual.
 */
public interface AttendanceService {

    /** Ghi nhận sự kiện join từ provider webhook (tạo SessionParticipant). */
    void recordOnlineJoin(Long sessionId, String externalParticipantId, Integer userId);

    /** Ghi nhận sự kiện leave: tính duration, tạo SessionAttendance. */
    void recordOnlineLeave(Long sessionId, String externalParticipantId);

    /** LIVE-06: Teacher đánh dấu thủ công — tạo SessionAttendance MANUAL. */
    void markManual(Long sessionId, Integer studentId, Integer teacherId);

    /**
     * LIVE-06: Student quét QR — validate và tạo SessionAttendance QR.
     * LIVE-BR-03: chỉ ghi nhận một lần/student/session.
     * LIVE-AC-03: QR hết hạn hoặc sai session → exception.
     */
    void scanQr(Long sessionId, String code, Integer studentId);

    /** LIVE-05: Tổng hợp attendance của tất cả student trong một session. */
    List<AttendanceSummaryResponse> getSummary(Long sessionId, Integer currentUserId);
}
