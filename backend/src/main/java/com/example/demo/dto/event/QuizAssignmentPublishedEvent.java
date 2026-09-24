package com.example.demo.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAssignmentPublishedEvent {
    private Long assignmentId;
    private Long quizVersionId;
    private Integer classId;
}
