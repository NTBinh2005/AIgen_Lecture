package com.example.demo.service;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.PaymentSucceededRequest;
import com.example.demo.dto.response.EnrollmentResponse;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassStatus;
import com.example.demo.entity.ClassStudent;
import com.example.demo.entity.ClassStudentId;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.ProcessedIntegrationEvent;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ProcessedIntegrationEventRepository;
import com.example.demo.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentEnrollmentService {
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final ProcessedIntegrationEventRepository processedEventRepository;
    private final Backend2EventService eventService;

    @Value("${internal.events.token:}")
    private String internalToken;

    @Transactional
    public EnrollmentResponse handlePaymentSucceeded(PaymentSucceededRequest request, String suppliedToken) {
        verifyInternalToken(suppliedToken);
        ClassStudentId enrollmentId = new ClassStudentId(request.classId(), request.studentId());
        if (processedEventRepository.existsById(request.eventId())) {
            return classStudentRepository.findById(enrollmentId)
                    .map(PaymentEnrollmentService::toResponse)
                    .orElseThrow(() -> new BadRequestException(
                            "Event was processed but enrollment is unavailable"));
        }

        ClassEntity classEntity = classRepository.findById(request.classId())
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + request.classId()));
        if (classEntity.getStatus() != ClassStatus.ACTIVE) {
            throw new BadRequestException("Payment cannot activate enrollment for a non-ACTIVE class");
        }
        LocalDateTime now = LocalDateTime.now();
        if (classEntity.getEnrollmentOpensAt() != null
                && now.isBefore(classEntity.getEnrollmentOpensAt())) {
            throw new BadRequestException("Enrollment has not opened yet");
        }
        if (classEntity.getEnrollmentClosesAt() != null
                && now.isAfter(classEntity.getEnrollmentClosesAt())) {
            throw new BadRequestException("Enrollment is closed");
        }
        User student = userRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + request.studentId()));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Payment owner is not a STUDENT");
        }

        ClassStudent enrollment = classStudentRepository.findById(enrollmentId).orElse(null);
        boolean alreadyOccupiesSeat = enrollment != null
                && (enrollment.getStatus() == EnrollmentStatus.ACTIVE
                || enrollment.getStatus() == EnrollmentStatus.SUSPENDED);
        if (classEntity.getMaxStudents() != null && !alreadyOccupiesSeat) {
            long count = classStudentRepository.countByClassEntity_ClassIdAndStatusIn(
                    classEntity.getClassId(), List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED));
            if (count >= classEntity.getMaxStudents()) {
                throw new BadRequestException("Class capacity has been reached");
            }
        }
        if (enrollment == null) {
            ClassStudent created = new ClassStudent();
            created.setId(enrollmentId);
            created.setClassEntity(classEntity);
            created.setStudent(student);
            enrollment = created;
        }
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(now);
        enrollment = classStudentRepository.save(enrollment);

        ProcessedIntegrationEvent event = new ProcessedIntegrationEvent();
        event.setEventId(request.eventId());
        event.setEventType("PaymentSucceeded");
        processedEventRepository.save(event);
        eventService.emit("EnrollmentActivated", "Enrollment",
                request.classId() + ":" + request.studentId(),
                java.util.Map.of("classId", request.classId(),
                        "studentId", request.studentId(),
                        "sourceEventId", request.eventId(),
                        "status", EnrollmentStatus.ACTIVE.name()));
        return toResponse(enrollment);
    }

    private void verifyInternalToken(String suppliedToken) {
        if (!StringUtils.hasText(internalToken) || !StringUtils.hasText(suppliedToken)
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Invalid internal event token");
        }
    }

    private static EnrollmentResponse toResponse(ClassStudent value) {
        return new EnrollmentResponse(value.getClassEntity().getClassId(),
                value.getClassEntity().getClassName(), value.getStudent().getUserId(),
                value.getStudent().getName(), value.getEnrolledAt(), value.getStatus());
    }
}
