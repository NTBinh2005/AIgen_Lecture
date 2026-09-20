package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

/** Immutable retry identity plus the mutable state of that single processing attempt. */
@Entity
@Getter
@Setter
@Table(
        name = "generation_job_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_gen_attempt_job_number",
                columnNames = {"job_id", "attempt_number"}
        ),
        indexes = {
                @Index(name = "idx_gen_attempt_job", columnList = "job_id, attempt_number"),
                @Index(name = "idx_gen_attempt_status", columnList = "status, created_at")
        }
)
@Check(name = "ck_gen_attempt_numbers", constraints =
        "progress >= 0 AND progress <= 100 AND attempt_number >= 1")
public class GenerationJobAttempt {

    @Id
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "job_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_gen_attempt_job")
    )
    private GenerationJob job;

    @Min(1)
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Min(0)
    @Max(100)
    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "current_step", length = 120)
    private String currentStep;

    @Column(name = "safe_error_code", length = 80)
    private String safeErrorCode;

    @Column(name = "safe_error_message", length = 1000)
    private String safeErrorMessage;

    @Column(name = "result_reference", length = 1000)
    private String resultReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (attemptId == null) {
            attemptId = UUID.randomUUID();
        }
        if (status == null) {
            status = JobStatus.QUEUED;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
