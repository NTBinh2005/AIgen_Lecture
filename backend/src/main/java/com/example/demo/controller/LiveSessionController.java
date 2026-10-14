package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.LiveSessionCancelRequest;
import com.example.demo.dto.request.LiveSessionCreateRequest;
import com.example.demo.dto.request.LiveSessionUpdateRequest;
import com.example.demo.dto.request.OfflineAttendanceRequest;
import com.example.demo.dto.response.AttendanceSummaryResponse;
import com.example.demo.dto.response.JoinTokenResponse;
import com.example.demo.dto.response.LiveSessionResponse;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.LiveSessionService;
import com.example.demo.service.serviceImpl.LiveSessionServiceImpl;
import com.example.demo.service.serviceImpl.AttendanceServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LIVE-01: Lập lịch — POST /api/live-sessions
 * LIVE-02: Tham gia — POST /api/live-sessions/{id}/join
 * LIVE-03: Quản lý người tham gia — GET /api/live-sessions/{id}/participants
 * LIVE-05: Điểm danh online — GET /api/live-sessions/{id}/attendance
 * LIVE-06: Điểm danh manual — POST /api/live-sessions/{id}/attendance/manual
 * LIVE-08: Đổi/hủy lịch — PATCH / POST /cancel
 */
@RestController
@RequestMapping("/api/live-sessions")
@Tag(name = "Live Sessions", description = "Quản lý buổi học live (LIVE-01..08)")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;
    private final AttendanceService attendanceService;

    public LiveSessionController(LiveSessionServiceImpl liveSessionService,
                                 AttendanceServiceImpl attendanceService) {
        this.liveSessionService = liveSessionService;
        this.attendanceService = attendanceService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping("/{sessionId}")
    @Operation(summary = "LIVE-01: Get session by id")
    public LiveSessionResponse findById(@PathVariable Long sessionId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.findById(sessionId, principal.getUserId());
    }

    @GetMapping("/class/{classId}")
    @Operation(summary = "LIVE-01: Get sessions by class")
    public List<LiveSessionResponse> findByClass(@PathVariable Integer classId,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.findByClass(classId, principal.getUserId());
    }

    @GetMapping("/my")
    @Operation(summary = "SCLS-01: Get sessions visible to the current user")
    public List<LiveSessionResponse> findMine(@AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.findMine(principal.getUserId());
    }

    // ── CREATE (LIVE-01) ─────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "LIVE-01: Create live session (TEACHER / ADMIN)")
    public LiveSessionResponse create(
            @Valid @RequestBody LiveSessionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.create(request, principal.getUserId());
    }

    // ── UPDATE (LIVE-08) ─────────────────────────────────────────────────────

    @PatchMapping("/{sessionId}")
    @Operation(summary = "LIVE-08: Update session info (reschedule)")
    public LiveSessionResponse update(
            @PathVariable Long sessionId,
            @RequestBody LiveSessionUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.update(sessionId, request, principal.getUserId());
    }

    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "LIVE-08: Cancel session — LIVE-AC-04 sends notification")
    public LiveSessionResponse cancel(
            @PathVariable Long sessionId,
            @RequestBody(required = false) LiveSessionCancelRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.cancel(sessionId, request, principal.getUserId());
    }

    // ── LIFECYCLE TRANSITIONS ─────────────────────────────────────────────────

    @PostMapping("/{sessionId}/open")
    @Operation(summary = "Transition: SCHEDULED → OPEN")
    public LiveSessionResponse open(@PathVariable Long sessionId,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.open(sessionId, principal.getUserId());
    }

    @PostMapping("/{sessionId}/live")
    @Operation(summary = "Transition: OPEN → LIVE")
    public LiveSessionResponse goLive(@PathVariable Long sessionId,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.goLive(sessionId, principal.getUserId());
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "Transition: LIVE → ENDED")
    public LiveSessionResponse end(@PathVariable Long sessionId,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.end(sessionId, principal.getUserId());
    }

    // ── JOIN TOKEN (LIVE-02) ─────────────────────────────────────────────────

    @PostMapping("/{sessionId}/join")
    @Operation(summary = "LIVE-02: Get short-lived join token (LIVE-AC-01)")
    public JoinTokenResponse join(@PathVariable Long sessionId,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return liveSessionService.joinSession(sessionId, principal.getUserId());
    }

    // ── ATTENDANCE (LIVE-05, LIVE-06) ─────────────────────────────────────────

    @GetMapping("/{sessionId}/attendance")
    @Operation(summary = "LIVE-05: Get attendance summary (LIVE-BR-02, LIVE-AC-02)")
    public List<AttendanceSummaryResponse> getAttendance(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return attendanceService.getSummary(sessionId, principal.getUserId());
    }

    @PostMapping("/{sessionId}/attendance/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "LIVE-06: Teacher marks student attendance manually")
    public void markManual(
            @PathVariable Long sessionId,
            @Valid @RequestBody OfflineAttendanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.markManual(sessionId, request.studentId(), principal.getUserId());
    }
}
