package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

/**
 * Durable, idempotent asynchronous work item for Backend 3.
 *
 * Cross-module references intentionally remain scalar values. This keeps the
 * Lecture and Presentation processors from coupling this module to another
 * module's tables.
 */
@Entity
@Getter
@Setter
@Table(
        name = "generation_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_gen_job_owner_type_key",
                columnNames = {"owner_id", "job_type", "idempotency_key"}
        ),
        indexes = {
                @Index(name = "idx_gen_job_owner_created", columnList = "owner_id, created_at"),
                @Index(name = "idx_gen_job_status_created", columnList = "status, created_at"),
                @Index(name = "idx_gen_job_source", columnList = "source_type, source_id"),
                @Index(name = "idx_gen_job_target", columnList = "target_type, target_id")
        }
)
@Check(name = "ck_gen_job_numbers", constraints =
        "progress >= 0 AND progress <= 100 AND attempt_count >= 1 AND max_attempts >= 1")
public class GenerationJob {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "owner_id", nullable = false)
    private Integer ownerId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    /** String by design because the source may use a legacy numeric or UUID id. */
    @Column(name = "source_id", length = 255)
    private String sourceId;

    @Column(name = "source_asset_id")
    private UUID sourceAssetId;

    @Column(name = "template_id")
    private UUID templateId;

    /** Canonical JSON document. TEXT keeps the mapping portable for PostgreSQL and H2 tests. */
    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "target_type", length = 50)
    private String targetType;

    /** String for the same cross-module compatibility reason as sourceId. */
    @Column(name = "target_id", length = 255)
    private String targetId;

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

    @Min(1)
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Min(1)
    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

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
        if (jobId == null) {
            jobId = UUID.randomUUID();
        }
        if (status == null) {
            status = JobStatus.QUEUED;
        }
        if (parametersJson == null) {
            parametersJson = "{}";
        }
        if (attemptCount < 1) {
            attemptCount = 1;
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
