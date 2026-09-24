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
@Table(name = "quiz_assignments")
@SQLDelete(sql = "UPDATE quiz_assignments SET deleted_at = NOW() WHERE assignment_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class QuizAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_version_id", nullable = false)
    private QuizVersion quizVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @Column(name = "open_at")
    private LocalDateTime openAt;

    @Column(name = "close_at")
    private LocalDateTime closeAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_policy", nullable = false, length = 30)
    private ResultPolicy resultPolicy;

    @Column(name = "shuffle_seed_base")
    private Integer shuffleSeedBase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssignmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = AssignmentStatus.OPEN;
        }
    }
}
