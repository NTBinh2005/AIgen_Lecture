package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.AttemptAnswerSubmitRequest;
import com.example.demo.dto.response.AttemptResponse;
import com.example.demo.dto.response.AttemptStartResponse;
import com.example.demo.entity.SubmitType;
import com.example.demo.service.AttemptService;
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

@Tag(name = "Quiz Attempt", description = "Student Quiz Attempt & Teacher Grading API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @Operation(summary = "Start a quiz attempt", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/quiz-assignments/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptStartResponse> startAttempt(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttemptStartResponse response = attemptService.startAttempt(principal.getUserId(), id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Teacher preview mode (làm thử)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/quiz-assignments/{id}/preview")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<AttemptStartResponse> startPreview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttemptStartResponse response = attemptService.startPreview(principal.getUserId(), id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fetch attempt details", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/attempts/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptResponse> fetchAttempt(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttemptResponse response = attemptService.fetchAttempt(principal.getUserId(), id, false);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Save/replace a single answer (idempotent autosave)", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/attempts/{id}/answers/{questionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> submitAnswer(
            @PathVariable Long id,
            @PathVariable Long questionId,
            @Valid @RequestBody AttemptAnswerSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        request.setQuestionId(questionId);
        attemptService.submitAnswer(principal.getUserId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Submit the entire attempt", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/attempts/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptResponse> submitAttempt(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttemptResponse response = attemptService.submitAttempt(principal.getUserId(), id, SubmitType.MANUAL);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Record tab switch signal", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/attempts/{id}/signals")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> recordSignal(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        attemptService.recordSignal(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reopen attempt (Teacher)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/attempts/{id}/reopen")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> reopenAttempt(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        attemptService.reopenAttempt(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get my result for an assignment", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/quiz-assignments/{id}/my-result")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AttemptResponse> getMyResult(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttemptResponse response = attemptService.getMyResult(principal.getUserId(), id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get student quiz history", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/students/me/quiz-history")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<AttemptResponse>> getStudentQuizHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<AttemptResponse> history = attemptService.getStudentQuizHistory(principal.getUserId());
        return ResponseEntity.ok(history);
    }
}
