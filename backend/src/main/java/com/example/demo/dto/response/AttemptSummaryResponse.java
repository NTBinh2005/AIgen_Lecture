package com.example.demo.dto.response;

import com.example.demo.entity.AttemptStatus;
import java.time.LocalDateTime;

/** Attempt list item. Answers are intentionally available only from GET /attempts/{id}. */
public record AttemptSummaryResponse(
        Long attemptId,
        Long assignmentId,
        String quizTitle,
        Integer classId,
        String className,
        Integer studentId,
        String studentName,
        Integer attemptNo,
        AttemptStatus status,
        LocalDateTime startedAt,
        LocalDateTime deadlineAt,
        LocalDateTime submittedAt,
        Double finalScore,
        Double objectiveScore
) {
}
