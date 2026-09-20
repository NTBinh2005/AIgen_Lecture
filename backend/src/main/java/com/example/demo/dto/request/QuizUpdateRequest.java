package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class QuizUpdateRequest {
    @NotBlank(message = "Title is required")
    private String title;

    private List<QuestionDto> questions;
}
