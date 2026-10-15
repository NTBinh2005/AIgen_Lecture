package com.example.demo.dto.request;

import com.example.demo.entity.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuestionDto {
    private Long questionId; // Used for updates

    @NotNull(message = "Loại câu hỏi không được để trống")
    private QuestionType questionType;

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String questionText;

    private List<String> options;

    private String correctAnswer;

    @NotNull(message = "Điểm câu hỏi không được để trống")
    private Double points;

    private String explanation;
    
    private Integer orderIndex;
}
