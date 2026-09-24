package com.example.demo.dto.response;

import com.example.demo.entity.ClassStatus;
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
        List<Integer> coTeacherIds
) {
}
