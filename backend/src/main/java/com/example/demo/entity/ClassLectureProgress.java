package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "class_lecture_progress")
@Getter
@Setter
public class ClassLectureProgress {

    @EmbeddedId
    private ClassLectureProgressId id = new ClassLectureProgressId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentId")
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("classId")
    @JoinColumn(name = "class_id")
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("lectureId")
    @JoinColumn(name = "lecture_id")
    private Lecture lecture;

    @Column(name = "watched_seconds", nullable = false)
    private Integer watchedSeconds = 0;

    @Column(name = "total_seconds", nullable = false)
    private Integer totalSeconds = 0;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
