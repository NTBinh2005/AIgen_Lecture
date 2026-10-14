package com.example.demo.event;

import com.example.demo.dto.event.QuizAssignmentPublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuizEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishAssignmentCreated(Long assignmentId, Long quizVersionId, Integer classId) {
        QuizAssignmentPublishedEvent event = new QuizAssignmentPublishedEvent(assignmentId, quizVersionId, classId);
        applicationEventPublisher.publishEvent(event);
    }
    
    public void publishAttemptSubmitted(Long attemptId, Integer studentId) {
        com.example.demo.dto.event.AttemptSubmittedEvent event = new com.example.demo.dto.event.AttemptSubmittedEvent(attemptId, studentId);
        applicationEventPublisher.publishEvent(event);
    }
    
    public void publishAttemptGraded(Long attemptId, Integer studentId, Double finalScore) {
        com.example.demo.dto.event.AttemptGradedEvent event = new com.example.demo.dto.event.AttemptGradedEvent(attemptId, studentId, finalScore);
        applicationEventPublisher.publishEvent(event);
    }
}
