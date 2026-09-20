package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "presentation_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_presentation_version_number",
                columnNames = {"presentation_id", "version_number"})
}, indexes = {
        @Index(name = "idx_presentation_version_presentation", columnList = "presentation_id,status"),
        @Index(name = "idx_presentation_version_created", columnList = "presentation_id,created_at")
})
public class PresentationVersion {

    @Id
    @Column(name = "presentation_version_id", nullable = false, updatable = false)
    private UUID presentationVersionId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false, updatable = false)
    private Presentation presentation;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    /** Title is part of the immutable version snapshot. */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "slides_json", nullable = false, columnDefinition = "TEXT")
    private String slidesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PresentationVersionStatus status = PresentationVersionStatus.DRAFT;

    @Column(name = "restored_from_version_id", updatable = false)
    private UUID restoredFromVersionId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (presentationVersionId == null) presentationVersionId = UUID.randomUUID();
        if (status == null) status = PresentationVersionStatus.DRAFT;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
