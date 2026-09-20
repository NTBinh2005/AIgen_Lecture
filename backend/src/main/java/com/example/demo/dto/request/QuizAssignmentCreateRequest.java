package com.example.demo.dto.request;

import com.example.demo.entity.ResultPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuizAssignmentCreateRequest {
    @NotNull(message = "VersionId is required")
    private Long quizVersionId;

    @NotNull(message = "ClassId is required")
    private Integer classId;

    private LocalDateTime openAt;
    
    private LocalDateTime closeAt;
    
    private Integer durationMinutes;
    
    private Integer maxAttempts;
    
    @NotNull(message = "ResultPolicy is required")
    private ResultPolicy resultPolicy;
    
    private Integer shuffleSeedBase;
}
