package com.example.demo.dto.response;

import com.example.demo.entity.Schedule;
import com.example.demo.entity.ScheduleType;
import java.time.LocalDateTime;

public record ScheduleResponse(
        Long scheduleId,
        Integer classId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        ScheduleType type,
        String recurrence,
        String timezone,
        LocalDateTime createdAt
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getScheduleId(),
                schedule.getClassEntity().getClassId(),
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                schedule.getType(),
                schedule.getRecurrence(),
                schedule.getTimezone(),
                schedule.getCreatedAt()
        );
    }
}
