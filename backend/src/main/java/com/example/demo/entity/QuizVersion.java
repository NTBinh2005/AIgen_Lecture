package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "quiz_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_quiz_versions_quiz_id_version_no", columnNames = {"quiz_id", "version_no"})
})
public class QuizVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "questions_snapshot", nullable = false, columnDefinition = "TEXT")
    private String questionsSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by", nullable = false)
    private User publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    @PrePersist
    void prePersist() {
        if (publishedAt == null) {
            publishedAt = LocalDateTime.now();
        }
    }
}
