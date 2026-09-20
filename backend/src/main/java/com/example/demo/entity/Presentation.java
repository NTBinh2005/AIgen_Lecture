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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Getter
@Setter
@Check(constraints = "(source_type = 'LECTURE_VERSION' AND source_id IS NOT NULL AND source_asset_id IS NULL) "
        + "OR (source_type = 'ASSET' AND source_asset_id IS NOT NULL)")
@Table(name = "presentations", indexes = {
        @Index(name = "idx_presentation_owner_status", columnList = "owner_id,status"),
        @Index(name = "idx_presentation_source", columnList = "source_type,source_id"),
        @Index(name = "idx_presentation_source_asset", columnList = "source_asset_id")
})
public class Presentation {

    @Id
    @Column(name = "presentation_id", nullable = false, updatable = false)
    private UUID presentationId = UUID.randomUUID();

    /** Scalar identity owned by the Auth/User module. */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Integer ownerId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32, updatable = false)
    private PresentationSourceType sourceType;

    /** External LectureVersion identifier. It intentionally is not a JPA relationship. */
    @Column(name = "source_id", length = 128, updatable = false)
    private String sourceId;

    /** External Asset identifier. It intentionally is not a JPA relationship. */
    @Column(name = "source_asset_id", updatable = false)
    private UUID sourceAssetId;

    @Column(name = "template_id", nullable = false, length = 100)
    private String templateId;

    @Column(name = "generation_parameters", nullable = false, columnDefinition = "TEXT")
    private String generationParameters = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PresentationStatus status = PresentationStatus.DRAFT;

    /** Mutable pointer to the teacher's working version. */
    @Column(name = "current_version_id")
    private UUID currentVersionId;

    /** Immutable published snapshot pointer consumed by Class/Student integrations. */
    @Column(name = "published_version_id")
    private UUID publishedVersionId;

    @Column(name = "current_version_number", nullable = false)
    private int currentVersionNumber;

    @Column(name = "saved_to_library", nullable = false)
    private boolean savedToLibrary = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (presentationId == null) presentationId = UUID.randomUUID();
        if (status == null) status = PresentationStatus.DRAFT;
        if (generationParameters == null) generationParameters = "{}";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
