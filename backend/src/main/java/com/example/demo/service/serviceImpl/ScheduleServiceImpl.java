package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.ScheduleCreateRequest;
import com.example.demo.dto.request.ScheduleUpdateRequest;
import com.example.demo.dto.response.ScheduleResponse;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.Schedule;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ScheduleRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.service.ScheduleService;
import com.example.demo.service.ClassAccessService;
import com.example.demo.service.Backend2EventService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassAccessService classAccessService;
    private final Backend2EventService eventService;

    public ScheduleServiceImpl(ScheduleRepository scheduleRepository, ClassRepository classRepository,
                               ClassStudentRepository classStudentRepository,
                               ClassAccessService classAccessService,
                               Backend2EventService eventService) {
        this.scheduleRepository = scheduleRepository;
        this.classRepository = classRepository;
        this.classStudentRepository = classStudentRepository;
        this.classAccessService = classAccessService;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findVisible(Integer currentUserId) {
        User user = classAccessService.requireUser(currentUserId);
        if (user.getRole() == UserRole.ADMIN) {
            return scheduleRepository.findAllByOrderByStartsAtAsc().stream()
                    .map(ScheduleResponse::from).toList();
        }
        List<Integer> classIds;
        if (user.getRole() == UserRole.TEACHER) {
            classIds = classRepository.findManagedByTeacher(currentUserId).stream()
                    .map(ClassEntity::getClassId).toList();
        } else {
            classIds = classStudentRepository.findByStudent_UserIdAndStatusIn(currentUserId,
                            List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)).stream()
                    .map(e -> e.getClassEntity().getClassId()).toList();
        }
        if (classIds.isEmpty()) {
            return List.of();
        }
        return scheduleRepository.findByClassEntity_ClassIdInOrderByStartsAtAsc(classIds).stream()
                .map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long scheduleId, Integer currentUserId) {
        Schedule schedule = getSchedule(scheduleId);
        classAccessService.assertCanView(schedule.getClassEntity(), currentUserId);
        return ScheduleResponse.from(schedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findByClass(Integer classId, Integer currentUserId) {
        ClassEntity classEntity = getClassEntity(classId);
        classAccessService.assertCanView(classEntity, currentUserId);
        return scheduleRepository.findByClassEntity_ClassId(classId).stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleResponse create(ScheduleCreateRequest request, Integer currentUserId) {
        validateTimeRange(request.startsAt(), request.endsAt());
        ClassEntity classEntity = getClassEntity(request.classId());
        classAccessService.assertCanManage(classEntity, currentUserId);
        if (classEntity.getStatus() != com.example.demo.entity.ClassStatus.ACTIVE) {
            throw new BadRequestException("Schedules can only be created for an ACTIVE class");
        }
        Schedule schedule = new Schedule();
        schedule.setClassEntity(classEntity);
        schedule.setStartsAt(request.startsAt());
        schedule.setEndsAt(request.endsAt());
        schedule.setType(request.type());
        schedule.setRecurrence(trimNullable(request.recurrence()));
        schedule.setTimezone(requireText(request.timezone(), "timezone"));
        Schedule saved = scheduleRepository.save(schedule);
        emitScheduleEvent("ScheduleCreated", saved);
        return ScheduleResponse.from(saved);
    }

    @Transactional
    public ScheduleResponse update(Long scheduleId, ScheduleUpdateRequest request, Integer currentUserId) {
        Schedule schedule = getSchedule(scheduleId);
        classAccessService.assertCanManage(schedule.getClassEntity(), currentUserId);

        ClassEntity classEntity = request.classId() == null
                ? schedule.getClassEntity()
                : getClassEntity(request.classId());
        classAccessService.assertCanManage(classEntity, currentUserId);
        if (classEntity.getStatus() != com.example.demo.entity.ClassStatus.ACTIVE) {
            throw new BadRequestException("Schedules can only be updated for an ACTIVE class");
        }
        java.time.LocalDateTime startsAt = request.startsAt() == null ? schedule.getStartsAt() : request.startsAt();
        java.time.LocalDateTime endsAt = request.endsAt() == null ? schedule.getEndsAt() : request.endsAt();
        validateTimeRange(startsAt, endsAt);

        schedule.setClassEntity(classEntity);
        schedule.setStartsAt(startsAt);
        schedule.setEndsAt(endsAt);
        if (request.type() != null) {
            schedule.setType(request.type());
        }
        if (request.recurrence() != null) {
            schedule.setRecurrence(trimNullable(request.recurrence()));
        }
        if (request.timezone() != null) {
            schedule.setTimezone(requireText(request.timezone(), "timezone"));
        }
        emitScheduleEvent("ScheduleUpdated", schedule);
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(Long scheduleId, Integer currentUserId) {
        Schedule schedule = getSchedule(scheduleId);
        classAccessService.assertCanManage(schedule.getClassEntity(), currentUserId);
        emitScheduleEvent("ScheduleCancelled", schedule);
        scheduleRepository.delete(schedule);
    }

    private Schedule getSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + scheduleId));
    }

    private ClassEntity getClassEntity(Integer classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    private void validateTimeRange(java.time.LocalDateTime startsAt, java.time.LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new BadRequestException("startsAt và endsAt không được để trống");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new BadRequestException("endsAt phải sau startsAt");
        }
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private void emitScheduleEvent(String eventType, Schedule schedule) {
        eventService.emit(eventType, "Schedule", schedule.getScheduleId(),
                Map.of("scheduleId", schedule.getScheduleId(),
                        "classId", schedule.getClassEntity().getClassId(),
                        "startsAt", schedule.getStartsAt().toString(),
                        "endsAt", schedule.getEndsAt().toString(),
                        "type", schedule.getType().name()));
    }
}
