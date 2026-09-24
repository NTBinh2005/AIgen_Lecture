package com.example.demo.dto.response;

import com.example.demo.dto.request.QuestionDto;
import com.example.demo.entity.QuizStatus;
import com.example.demo.entity.SourceType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuizDetailResponse {
    private Long quizId;
    private String title;
    private SourceType sourceType;
    private Long sourceLectureId;
    private QuizStatus status;
    private LocalDateTime createdAt;
    private List<QuestionDto> questions;
}
