package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class QuizUpdateRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private List<QuestionDto> questions;
}
