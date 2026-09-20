package com.example.demo.dto.request;

import com.example.demo.entity.QuestionType;
import com.example.demo.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuizCreateRequest {
    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "SourceType is required")
    private SourceType sourceType;

    private Long sourceLectureId; // Required if sourceType is AI

    private List<QuestionDto> questions;
}
