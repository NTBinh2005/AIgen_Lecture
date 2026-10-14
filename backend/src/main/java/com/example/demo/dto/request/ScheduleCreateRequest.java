package com.example.demo.dto.request;

import com.example.demo.entity.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ScheduleCreateRequest(
        @NotNull Integer classId,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @NotNull ScheduleType type,
        @Size(max = 100) String recurrence,
        @NotBlank @Size(max = 100) String timezone
) {
}
