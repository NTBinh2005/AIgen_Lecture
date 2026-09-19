package com.example.demo.dto.request;

import com.example.demo.entity.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuestionDto {
    private Long questionId; // Used for updates

    @NotNull(message = "QuestionType is required")
    private QuestionType questionType;

    @NotBlank(message = "Question text is required")
    private String questionText;

    private List<String> options;

    private String correctAnswer;

    @NotNull(message = "Points is required")
    private Integer points;

    private String explanation;
    
    private Integer orderIndex;
}
