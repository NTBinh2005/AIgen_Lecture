package com.example.demo.dto.request;

import com.example.demo.entity.ScheduleType;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ScheduleUpdateRequest(
        Integer classId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        ScheduleType type,
        @Size(max = 100) String recurrence,
        @Size(max = 100) String timezone
) {
}
