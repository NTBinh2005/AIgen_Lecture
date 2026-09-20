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
@Table(name = "presentation_collaborators", uniqueConstraints = {
        @UniqueConstraint(name = "uk_presentation_collaborator",
                columnNames = {"presentation_id", "collaborator_user_id"})
}, indexes = {
        @Index(name = "idx_presentation_collaborator_user", columnList = "collaborator_user_id")
})
public class PresentationCollaborator {

    @Id
    @Column(name = "presentation_collaborator_id", nullable = false, updatable = false)
    private UUID presentationCollaboratorId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false, updatable = false)
    private Presentation presentation;

    /** Scalar identity owned by the Auth/User module. */
    @Column(name = "collaborator_user_id", nullable = false, updatable = false)
    private Integer collaboratorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 24)
    private PresentationCollaboratorRole role = PresentationCollaboratorRole.EDITOR;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        if (presentationCollaboratorId == null) presentationCollaboratorId = UUID.randomUUID();
        if (role == null) role = PresentationCollaboratorRole.EDITOR;
        if (createdAt == null) createdAt = Instant.now();
    }
}
