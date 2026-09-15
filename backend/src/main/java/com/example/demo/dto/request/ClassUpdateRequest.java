package com.example.demo.dto.request;

import com.example.demo.entity.ClassStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ClassUpdateRequest(
        Integer teacherId,
        @Size(max = 150) String className,
        @Size(max = 20) String classCode,
        @Size(max = 30) String semester,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        @Size(max = 500) String description,
        ClassStatus status,
        java.util.List<Integer> coTeacherIds
) {
}
