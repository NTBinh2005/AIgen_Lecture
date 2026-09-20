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
public class ClassStudentId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "class_id")
    private Integer classId;

    @Column(name = "student_id")
    private Integer studentId;

    public ClassStudentId() {
    }

    public ClassStudentId(Integer classId, Integer studentId) {
        this.classId = classId;
        this.studentId = studentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassStudentId that)) {
            return false;
        }
        return Objects.equals(classId, that.classId) && Objects.equals(studentId, that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classId, studentId);
    }
}
