package com.example.demo.dto.response;

import com.example.demo.entity.AssignmentStatus;
import com.example.demo.entity.ResultPolicy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuizAssignmentResponse {
    private Long assignmentId;
    private Long quizVersionId;
    private Integer classId;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private Integer durationMinutes;
    private Integer maxAttempts;
    private ResultPolicy resultPolicy;
    private Integer shuffleSeedBase;
    private AssignmentStatus status;
}
