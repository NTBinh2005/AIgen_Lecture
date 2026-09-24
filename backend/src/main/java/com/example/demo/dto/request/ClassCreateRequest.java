package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ClassCreateRequest(
        @NotNull Integer teacherId,
        @NotBlank @Size(max = 150) String className,
        @NotBlank @Size(max = 20) String classCode,
        @Size(max = 30) String semester,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime enrollmentOpensAt,
        LocalDateTime enrollmentClosesAt,
        Integer maxStudents,
        @Size(max = 500) String description,
        java.util.List<Integer> coTeacherIds
) {
}
