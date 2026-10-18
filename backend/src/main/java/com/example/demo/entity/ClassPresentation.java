package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Bản trình chiếu đã xuất bản được giao cho lớp (giống ClassLecture). */
@Entity
@Getter
@Setter
@Table(name = "class_presentations", uniqueConstraints = @UniqueConstraint(
        name = "uk_class_presentation", columnNames = {"class_id", "presentation_id"}))
public class ClassPresentation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
}
