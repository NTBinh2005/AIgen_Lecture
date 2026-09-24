package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.response.GenerationJobResponse;
import com.example.demo.entity.JobStatus;
import com.example.demo.entity.JobType;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.event.LectureGenerationQueuedEvent;
import com.example.demo.service.event.PresentationGenerationQueuedEvent;
import com.example.demo.entity.PresentationSourceType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generation-jobs")
@RequiredArgsConstructor
public class GenerationJobController {
    private final GenerationJobService generationJobService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/{jobId}")
    public ResponseEntity<GenerationJobResponse> get(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        return ResponseEntity.ok(generationJobService.get(
                jobId, actor.getUserId(), isAdmin(actor)));
    }

    @PostMapping("/{jobId}/retry")
    public ResponseEntity<GenerationJobResponse> retry(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        GenerationJobResponse job = generationJobService.retry(
                jobId, actor.getUserId(), isAdmin(actor));
        dispatch(job);
        return ResponseEntity.accepted().body(job);
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<GenerationJobResponse> cancel(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal actor = requirePrincipal(principal);
        return ResponseEntity.ok(generationJobService.cancel(
                jobId, actor.getUserId(), isAdmin(actor)));
    }

    private void dispatch(GenerationJobResponse job) {
        if (job.status() != JobStatus.QUEUED || job.currentAttemptId() == null) {
            return;
        }
        if (job.jobType() == JobType.LECTURE_GENERATION) {
            eventPublisher.publishEvent(new LectureGenerationQueuedEvent(
                    job.jobId(),
                    job.currentAttemptId(),
                    Long.valueOf(job.targetId()),
                    job.sourceAssetId(),
                    job.ownerId()));
        } else if (job.jobType() == JobType.PRESENTATION_GENERATION) {
            eventPublisher.publishEvent(new PresentationGenerationQueuedEvent(
                    job.jobId(),
                    job.currentAttemptId(),
                    UUID.fromString(job.targetId()),
                    PresentationSourceType.valueOf(job.sourceType()),
                    job.sourceId(),
                    job.sourceAssetId(),
                    job.ownerId()));
        }
    }

    private UserPrincipal requirePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return principal;
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
