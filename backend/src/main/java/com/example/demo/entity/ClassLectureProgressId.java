package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class ClassLectureProgressId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "student_id")
    private Integer studentId;

    @Column(name = "class_id")
    private Integer classId;

    @Column(name = "lecture_id")
    private Long lectureId;

    public ClassLectureProgressId() {
    }

    public ClassLectureProgressId(Integer studentId, Integer classId, Long lectureId) {
        this.studentId = studentId;
        this.classId = classId;
        this.lectureId = lectureId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassLectureProgressId that)) return false;
        return Objects.equals(studentId, that.studentId) &&
               Objects.equals(classId, that.classId) &&
               Objects.equals(lectureId, that.lectureId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, classId, lectureId);
    }
}
