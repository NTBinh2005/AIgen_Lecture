package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.TeacherGradeSubmitRequest;
import com.example.demo.service.GradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Grading", description = "Teacher manual grading API")
@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
public class GradingController {

    private final GradingService gradingService;

    @Operation(summary = "Confirm grade for an attempt", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> confirmGrade(
            @PathVariable Long id,
            @Valid @RequestBody TeacherGradeSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        gradingService.confirmGrade(principal.getUserId(), id, request.getQuestionScores());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trigger AI suggest score for an essay answer", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/answers/{answerId}/ai-suggest")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> triggerAiSuggest(
            @PathVariable Long id,
            @PathVariable Long answerId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Run AI suggest asynchronously
        gradingService.suggestScoreForEssay(answerId);
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Finalize grading for an attempt", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> finalizeGrading(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Mark attempt as GRADED
        // For simplicity, using confirmGrade with an empty map here, but in real logic it would explicitly finalize.
        gradingService.confirmGrade(principal.getUserId(), id, java.util.Collections.emptyMap());
        return ResponseEntity.noContent().build();
    }
}
