package com.example.demo.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuizVersionResponse {
    private Long versionId;
    private Long quizId;
    private Integer versionNo;
    private LocalDateTime publishedAt;
    private Integer publishedBy;
    private String questionsSnapshot; // Raw JSON for now
}
