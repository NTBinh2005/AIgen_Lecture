package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.SessionResourceRequest;
import com.example.demo.dto.response.SessionResourceResponse;
import com.example.demo.service.SessionResourceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live-sessions/{sessionId}/resources")
@RequiredArgsConstructor
public class SessionResourceController {
    private final SessionResourceService resourceService;

    @GetMapping
    public List<SessionResourceResponse> findAll(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return resourceService.findAll(sessionId, principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResourceResponse add(
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return resourceService.add(sessionId, request, principal.getUserId());
    }

    @DeleteMapping("/{resourceLinkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long sessionId,
            @PathVariable Long resourceLinkId,
            @AuthenticationPrincipal UserPrincipal principal) {
        resourceService.remove(sessionId, resourceLinkId, principal.getUserId());
    }
}
