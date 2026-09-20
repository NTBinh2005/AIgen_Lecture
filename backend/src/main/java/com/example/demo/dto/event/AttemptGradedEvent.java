package com.example.demo.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptGradedEvent {
    private Long attemptId;
    private Integer studentId;
    private Double finalScore;
}
