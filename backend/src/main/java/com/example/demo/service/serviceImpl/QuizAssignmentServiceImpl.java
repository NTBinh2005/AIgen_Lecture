package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.QuizAssignmentCreateRequest;
import com.example.demo.dto.response.QuizAssignmentResponse;
import com.example.demo.dto.response.StudentAssignmentResponse;
import com.example.demo.entity.*;
import com.example.demo.event.QuizEventPublisher;
import com.example.demo.repository.*;
import com.example.demo.service.QuizAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizAssignmentServiceImpl implements QuizAssignmentService {

    private final QuizAssignmentRepository assignmentRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final ClassRepository classRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassStudentRepository classStudentRepository;
    private final AttemptRepository attemptRepository;
    private final QuizEventPublisher eventPublisher;

    @Override
    @Transactional
    public QuizAssignmentResponse createAssignment(Integer teacherId, QuizAssignmentCreateRequest request) {
        ClassEntity classEntity = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));

        if (!classEntity.getTeacher().getUserId().equals(teacherId) &&
            !classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(request.getClassId(), teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên lớp này");
        }

        QuizVersion version = quizVersionRepository.findById(request.getQuizVersionId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));

        QuizAssignment assignment = new QuizAssignment();
        assignment.setQuizVersion(version);
        assignment.setClassEntity(classEntity);
        assignment.setOpenAt(request.getOpenAt());
        assignment.setCloseAt(request.getCloseAt());
        assignment.setDurationMinutes(request.getDurationMinutes());
        assignment.setMaxAttempts(request.getMaxAttempts());
        assignment.setResultPolicy(request.getResultPolicy());
        assignment.setShuffleSeedBase(request.getShuffleSeedBase());
        
        QuizAssignment saved = assignmentRepository.save(assignment);
        
        eventPublisher.publishAssignmentCreated(saved.getAssignmentId(), version.getVersionId(), classEntity.getClassId());
        
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizAssignmentResponse> getAssignmentsByClass(Integer teacherId, Integer classId) {
        if (!classRepository.findById(classId).map(c -> c.getTeacher().getUserId().equals(teacherId)).orElse(false) &&
            !classTeacherRepository.existsByClassEntity_ClassIdAndTeacher_UserId(classId, teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên lớp này");
        }
        
        return assignmentRepository.findByClassEntity_ClassId(classId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentAssignmentResponse> getStudentAssignments(Integer studentId, Integer classId, String statusFilter) {
        // Verify enrollment
        classStudentRepository.findByClassEntity_ClassIdAndStudent_UserIdAndStatusIn(
                classId, studentId, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))
                .orElseThrow(() -> new AccessDeniedException("Bạn không thuộc lớp này"));

        List<QuizAssignment> assignments = assignmentRepository.findByClassEntity_ClassId(classId);
        List<StudentAssignmentResponse> responses = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();

        for (QuizAssignment assignment : assignments) {
            if (assignment.getStatus() == AssignmentStatus.CLOSED) continue;
            if (assignment.getOpenAt() != null && assignment.getOpenAt().isAfter(now)) continue;
            
            List<Attempt> attempts = attemptRepository.findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(
                    assignment.getAssignmentId(), studentId);
            
            String studentStatus = "TODO";
            if (!attempts.isEmpty()) {
                Attempt latest = attempts.get(0);
                if (latest.getStatus() == AttemptStatus.IN_PROGRESS) {
                    studentStatus = "IN_PROGRESS";
                } else {
                    studentStatus = "COMPLETED";
                }
            }
            if (studentStatus.equals("TODO") && assignment.getCloseAt() != null && assignment.getCloseAt().isBefore(now)) {
                studentStatus = "OVERDUE";
            }
            
            if (statusFilter != null && !statusFilter.isEmpty() && !studentStatus.equals(statusFilter)) {
                continue;
            }
            
            StudentAssignmentResponse res = new StudentAssignmentResponse();
            res.setAssignmentId(assignment.getAssignmentId());
            res.setQuizTitle(assignment.getQuizVersion().getQuiz().getTitle());
            res.setClassId(classId);
            res.setOpenAt(assignment.getOpenAt());
            res.setCloseAt(assignment.getCloseAt());
            res.setDurationMinutes(assignment.getDurationMinutes());
            res.setMaxAttempts(assignment.getMaxAttempts());
            res.setStatus(assignment.getStatus());
            res.setUsedAttempts(attempts.size());
            res.setStudentStatus(studentStatus);
            responses.add(res);
        }
        
        return responses;
    }

    private QuizAssignmentResponse mapToResponse(QuizAssignment assignment) {
        QuizAssignmentResponse res = new QuizAssignmentResponse();
        res.setAssignmentId(assignment.getAssignmentId());
        res.setQuizVersionId(assignment.getQuizVersion().getVersionId());
        res.setClassId(assignment.getClassEntity().getClassId());
        res.setOpenAt(assignment.getOpenAt());
        res.setCloseAt(assignment.getCloseAt());
        res.setDurationMinutes(assignment.getDurationMinutes());
        res.setMaxAttempts(assignment.getMaxAttempts());
        res.setResultPolicy(assignment.getResultPolicy());
        res.setShuffleSeedBase(assignment.getShuffleSeedBase());
        res.setStatus(assignment.getStatus());
        return res;
    }
}
