package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "attempt_answers", indexes = {
        @Index(name = "idx_attempt_answers_attempt_id", columnList = "attempt_id")
})
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Version
    @Column(name = "answer_version", nullable = false)
    private Long answerVersion = 0L;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "points_awarded")
    private Double pointsAwarded;

    @Column(name = "ai_suggested_score")
    private Double aiSuggestedScore;

    @Column(name = "teacher_final_score")
    private Double teacherFinalScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;
}
