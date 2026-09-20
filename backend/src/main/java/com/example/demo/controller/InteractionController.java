package com.example.demo.controller;

import com.example.demo.dto.response.QuizResponse;
import com.example.demo.dto.request.SubmitAnswerRequest;
import com.example.demo.dto.response.SubmitAnswerResponse;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.service.InteractionService;
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

/**
 * REST API cho Interaction Log — câu hỏi trắc nghiệm và lịch sử trả lời học sinh.
 *
 * Endpoints:
 *   GET  /api/lectures/{id}/quizzes  → TEACHER hoặc STUDENT (correctAnswer ẩn với Student)
 *   POST /api/interactions           → STUDENT only (nộp câu trả lời)
 */
@Tag(name = "Interactions", description = "API câu hỏi trắc nghiệm và tương tác học sinh")
@RestController
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    /**
     * GET /api/lectures/{id}/quizzes
     * Lấy danh sách câu hỏi trắc nghiệm của một lecture.
     * - Teacher: nhận đủ correctAnswer
     * - Student: correctAnswer = null (ẩn để chống gian lận)
     */
    @Operation(summary = "Lấy câu hỏi trắc nghiệm của bài giảng",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/lectures/{lectureId}/quizzes")
    public ResponseEntity<List<QuizResponse>> getQuizzes(
            @PathVariable Long lectureId,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isTeacher = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));

        List<QuizResponse> quizzes = interactionService.getQuizzes(lectureId, isTeacher);
        return ResponseEntity.ok(quizzes);
    }

    /**
     * POST /api/interactions
     * Student nộp câu trả lời cho một câu hỏi.
     * Backend tự tính đúng/sai và xác định isFirstAttempt (BR-06).
     */
    @Operation(summary = "Nộp câu trả lời trắc nghiệm (Student)",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/interactions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        SubmitAnswerResponse response = interactionService.submitAnswer(
                principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
