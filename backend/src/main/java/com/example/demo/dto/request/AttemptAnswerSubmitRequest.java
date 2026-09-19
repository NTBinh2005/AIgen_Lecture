package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttemptAnswerSubmitRequest {
    @NotNull(message = "QuestionId is required")
    private Long questionId;
    
    private String response; // null or empty for un-answering
    
    @NotNull(message = "AnswerVersion is required for optimistic locking")
    private Long answerVersion;
}
