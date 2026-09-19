package com.example.demo.dto.response;

import com.example.demo.dto.request.QuestionDto;
import com.example.demo.entity.AttemptStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AttemptStartResponse {
    private Long attemptId;
    private Long assignmentId;
    private Integer attemptNo;
    private AttemptStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private List<QuestionDto> questions;
}
