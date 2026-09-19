package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QuizAssignmentCreateRequest;
import com.example.demo.dto.response.QuizAssignmentResponse;
import com.example.demo.dto.response.StudentAssignmentResponse;
import com.example.demo.service.QuizAssignmentService;
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

@Tag(name = "Quiz Assignment", description = "Teacher Assigning Quizzes & Student Listing")
@RestController
@RequestMapping("/api/quiz-assignments")
@RequiredArgsConstructor
public class QuizAssignmentController {

    private final QuizAssignmentService assignmentService;

    @Operation(summary = "Assign a Quiz Version to a Class", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<QuizAssignmentResponse> createAssignment(
            @Valid @RequestBody QuizAssignmentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuizAssignmentResponse response = assignmentService.createAssignment(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get Assignments by Class (Teacher)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/teacher/class/{classId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<QuizAssignmentResponse>> getAssignmentsByClass(
            @PathVariable Integer classId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<QuizAssignmentResponse> responses = assignmentService.getAssignmentsByClass(principal.getUserId(), classId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get Student Assignments by Class", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/student/class/{classId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentAssignmentResponse>> getStudentAssignments(
            @PathVariable Integer classId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<StudentAssignmentResponse> responses = assignmentService.getStudentAssignments(principal.getUserId(), classId, status);
        return ResponseEntity.ok(responses);
    }
}
