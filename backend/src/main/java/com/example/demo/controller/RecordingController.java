package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.response.RecordingResponse;
import com.example.demo.service.RecordingService;
import com.example.demo.service.serviceImpl.RecordingServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LIVE-07: Ghi hình — yêu cầu và xem trạng thái recording.
 * LIVE-BR-04: Quyền xem kế thừa Class (kiểm tra ở service/security).
 * LIVE-BR-06: MVP không lưu media trực tiếp.
 */
@RestController
@RequestMapping("/api/live-sessions/{sessionId}/recording")
@Tag(name = "Session Recording", description = "LIVE-07: Quản lý recording buổi học")
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingServiceImpl recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "LIVE-07: Request recording from provider (TEACHER)")
    public RecordingResponse requestRecording(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return recordingService.requestRecording(sessionId, principal.getUserId());
    }

    @GetMapping
    @Operation(summary = "LIVE-07: Get recording status and playback URL")
    public RecordingResponse getRecording(@PathVariable Long sessionId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return recordingService.getRecording(sessionId, principal.getUserId());
    }
}
