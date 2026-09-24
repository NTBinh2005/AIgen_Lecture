package com.example.demo.dto.response;

import com.example.demo.entity.AttemptStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AttemptResponse {
    private Long attemptId;
    private Long assignmentId;
    private Integer attemptNo;
    private AttemptStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
    private Double finalScore;
    private Double objectiveScore;
    
    private List<AttemptAnswerResponse> answers;
}
