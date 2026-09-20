package com.example.demo.dto.request;

import com.example.demo.entity.ExportType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExportRequest {
    @NotNull
    private ExportType type; // QUIZ_TEMPLATE, QUIZ_RESULTS, CLASS_SCORES
    
    // Optional context depending on export type
    private Long quizVersionId;
    private Long assignmentId;
    private Integer classId;
}
