package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QuizCreateRequest;
import com.example.demo.dto.request.QuizUpdateRequest;
import com.example.demo.dto.response.QuizDetailResponse;
import com.example.demo.dto.response.QuizVersionResponse;
import com.example.demo.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quiz", description = "Teacher Quiz Authoring API")
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @Operation(summary = "Create a new Quiz Draft", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<QuizDetailResponse> createQuiz(
            @Valid @RequestBody QuizCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizDetailResponse response = quizService.createQuizDraft(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Update an existing Quiz Draft", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<QuizDetailResponse> updateQuiz(
            @PathVariable Long id,
            @Valid @RequestBody QuizUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizDetailResponse response = quizService.updateQuizDraft(principal.getUserId(), id, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Quiz Detail", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<QuizDetailResponse> getQuiz(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizDetailResponse response = quizService.getQuiz(id, principal);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Publish Quiz (Create a new Quiz Version)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<QuizVersionResponse> publishQuiz(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizVersionResponse response = quizService.publishQuiz(principal.getUserId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get Quiz Versions", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<QuizVersionResponse>> getQuizVersions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<QuizVersionResponse> versions = quizService.getQuizVersions(id, principal);
        return ResponseEntity.ok(versions);
    }

    @Operation(summary = "Close Quiz", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> closeQuiz(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        quizService.closeQuiz(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive Quiz", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> archiveQuiz(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        quizService.archiveQuiz(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
