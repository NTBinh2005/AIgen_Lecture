package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "class_students")
public class ClassStudent {
    @EmbeddedId
    private ClassStudentId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("classId")
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = EnrollmentStatus.ACTIVE;
        }
        if (enrolledAt == null) {
            enrolledAt = LocalDateTime.now();
        }
    }
}
