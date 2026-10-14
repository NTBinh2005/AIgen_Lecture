package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class TeacherGradeSubmitRequest {
    @NotNull
    private Map<Long, Double> questionScores; // QuestionId -> Score
}
