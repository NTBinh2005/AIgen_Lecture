package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attempts_assignment_student_attempt_no", columnNames = {"assignment_id", "student_id", "attempt_no"})
}, indexes = {
        @Index(name = "idx_attempts_status_deadline", columnList = "status, deadline_at")
})
@SQLDelete(sql = "UPDATE attempts SET deleted_at = NOW() WHERE attempt_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long attemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private QuizAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_version_id", nullable = false)
    private QuizVersion quizVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "submit_type", length = 20)
    private SubmitType submitType;

    @Column(name = "objective_score")
    private Double objectiveScore;

    @Column(name = "final_score")
    private Double finalScore;

    @Column(name = "tab_switch_signal_count")
    private Integer tabSwitchSignalCount = 0;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = AttemptStatus.IN_PROGRESS;
        }
    }
}
