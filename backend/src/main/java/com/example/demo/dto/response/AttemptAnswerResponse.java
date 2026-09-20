package com.example.demo.dto.response;

import lombok.Data;

@Data
public class AttemptAnswerResponse {
    private Long answerId;
    private Long questionId;
    private String response;
    private Long answerVersion;
    
    // Conditional fields based on ResultPolicy
    private Boolean isCorrect;
    private String correctAnswer;
    private String explanation;
    private Double pointsAwarded;
    private Double teacherFinalScore;
}
