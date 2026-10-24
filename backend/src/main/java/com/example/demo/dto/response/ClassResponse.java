package com.example.demo.dto.response;

import com.example.demo.entity.ClassStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ClassResponse(
        Integer classId,
        Integer teacherId,
        String teacherName,
        String className,
        String classCode,
        String semester,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime enrollmentOpensAt,
        LocalDateTime enrollmentClosesAt,
        Integer maxStudents,
        String description,
        ClassStatus status,
        LocalDateTime createdAt,
        LocalDateTime archivedAt,
        List<Integer> coTeacherIds,
        BigDecimal price
) {
    public ClassResponse(
            Integer classId,
            Integer teacherId,
            String teacherName,
            String className,
            String classCode,
            String semester,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            LocalDateTime enrollmentOpensAt,
            LocalDateTime enrollmentClosesAt,
            Integer maxStudents,
            String description,
            ClassStatus status,
            LocalDateTime createdAt,
            LocalDateTime archivedAt,
            List<Integer> coTeacherIds
    ) {
        this(classId, teacherId, teacherName, className, classCode, semester, startsAt, endsAt,
             enrollmentOpensAt, enrollmentClosesAt, maxStudents, description, status, createdAt,
             archivedAt, coTeacherIds, BigDecimal.ZERO);
    }
}
