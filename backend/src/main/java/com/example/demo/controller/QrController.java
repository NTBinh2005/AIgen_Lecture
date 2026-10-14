package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QrScanRequest;
import com.example.demo.dto.response.QrTokenResponse;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.QrService;
import com.example.demo.service.serviceImpl.AttendanceServiceImpl;
import com.example.demo.service.serviceImpl.QrServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LIVE-06: Điểm danh offline bằng QR code.
 * POST /api/live-sessions/{sessionId}/qr/generate — TEACHER tạo QR ngắn hạn.
 * POST /api/live-sessions/{sessionId}/qr/scan     — STUDENT quét QR.
 *
 * LIVE-BR-03: Mỗi student chỉ ghi nhận một attendance/session qua QR.
 * LIVE-AC-03: QR hết hạn hoặc sai session → 400.
 */
@RestController
@RequestMapping("/api/live-sessions/{sessionId}/qr")
@Tag(name = "QR Attendance", description = "LIVE-06: QR code điểm danh offline")
public class QrController {

    private final QrService qrService;
    private final AttendanceService attendanceService;

    public QrController(QrServiceImpl qrService,
                        AttendanceServiceImpl attendanceService) {
        this.qrService = qrService;
        this.attendanceService = attendanceService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "LIVE-06: Generate QR token for offline attendance (TEACHER)")
    public QrTokenResponse generate(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return qrService.generateQr(sessionId, principal.getUserId());
    }

    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "LIVE-06: Student scans QR to mark attendance (LIVE-AC-03)")
    public void scan(
            @PathVariable Long sessionId,
            @Valid @RequestBody QrScanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.scanQr(sessionId, request.code(), principal.getUserId());
    }
}
