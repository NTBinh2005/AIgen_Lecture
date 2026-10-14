package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "lecture_collaborators",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lecture_collaborator",
                columnNames = {"lecture_id", "collaborator_user_id"}),
        indexes = @Index(name = "idx_lecture_collaborator_user", columnList = "collaborator_user_id"))
public class LectureCollaborator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lecture_collaborator_id", nullable = false, updatable = false)
    private UUID lectureCollaboratorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false, updatable = false)
    private Lecture lecture;

    @Column(name = "collaborator_user_id", nullable = false, updatable = false)
    private Integer collaboratorUserId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Integer createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
