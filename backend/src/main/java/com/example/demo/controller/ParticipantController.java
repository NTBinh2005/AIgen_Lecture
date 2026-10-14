package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.ParticipantActionRequest;
import com.example.demo.dto.response.SessionParticipantResponse;
import com.example.demo.service.ParticipantService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live-sessions/{sessionId}/participants")
@RequiredArgsConstructor
public class ParticipantController {
    private final ParticipantService participantService;

    @GetMapping
    public List<SessionParticipantResponse> findAll(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return participantService.findAll(sessionId, principal.getUserId());
    }

    @PatchMapping("/{participantId}")
    public SessionParticipantResponse applyAction(
            @PathVariable Long sessionId,
            @PathVariable Long participantId,
            @Valid @RequestBody ParticipantActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return participantService.applyAction(sessionId, participantId, request.action(), principal.getUserId());
    }
}
