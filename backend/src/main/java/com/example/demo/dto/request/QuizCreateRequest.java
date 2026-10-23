package com.example.demo.dto.request;

import com.example.demo.entity.QuestionType;
import com.example.demo.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuizCreateRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotNull(message = "Loại nguồn không được để trống")
    private SourceType sourceType;

    private Long sourceLectureId; // Required if sourceType is AI

    private List<QuestionDto> questions;
}
