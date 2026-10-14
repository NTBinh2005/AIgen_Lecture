package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Immutable content snapshot exposed to Class and Quiz integrations. */
@Entity
@Getter
@Setter
@Table(
        name = "lecture_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lecture_version_number",
                columnNames = {"lecture_id", "version_number"}),
        indexes = {
                @Index(name = "idx_lecture_version_lecture", columnList = "lecture_id"),
                @Index(name = "idx_lecture_version_status", columnList = "status")
        })
public class LectureVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lecture_version_id", nullable = false, updatable = false)
    private UUID lectureVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false, updatable = false)
    private Lecture lecture;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** JSON snapshot of slide/script input used by the legacy video pipeline. */
    @Column(name = "slide_content", columnDefinition = "TEXT")
    private String slideContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LectureVersionStatus status;

    @Column(name = "source_asset_id")
    private UUID sourceAssetId;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = LectureVersionStatus.DRAFT;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
