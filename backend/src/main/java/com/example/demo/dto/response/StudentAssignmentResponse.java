package com.example.demo.dto.response;

import com.example.demo.entity.AssignmentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentAssignmentResponse {
    private Long assignmentId;
    private String quizTitle;
    private Integer classId;
    private LocalDateTime openAt;
    private LocalDateTime closeAt;
    private Integer durationMinutes;
    private Integer maxAttempts;
    private AssignmentStatus status;
    private Integer usedAttempts; // Number of attempts student has made
    private String studentStatus; // "TODO", "IN_PROGRESS", "COMPLETED", "OVERDUE"
}
