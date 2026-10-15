package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.QuizAssignmentCreateRequest;
import com.example.demo.dto.response.QuizAssignmentResponse;
import com.example.demo.dto.response.StudentAssignmentResponse;
import com.example.demo.dto.response.AttemptResponse;
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
    private final com.example.demo.service.ClassAccessService classAccessService;
    private final QuizEventPublisher eventPublisher;

    @Override
    @Transactional
    public QuizAssignmentResponse createAssignment(Integer teacherId, QuizAssignmentCreateRequest request) {
        ClassEntity classEntity = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));

        classAccessService.assertCanManage(classEntity, teacherId);

        QuizVersion version = quizVersionRepository.findById(request.getQuizVersionId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        User actor = classAccessService.requireUser(teacherId);
        if (actor.getRole() != UserRole.ADMIN
                && !version.getQuiz().getOwnerTeacher().getUserId().equals(teacherId)) {
            throw new AccessDeniedException("You cannot assign another teacher's quiz");
        }

        // Bug 21 fix: validate that closeAt is after openAt
        if (request.getOpenAt() != null && request.getCloseAt() != null
                && !request.getCloseAt().isAfter(request.getOpenAt())) {
            throw new IllegalArgumentException("Thời hạn đóng bài phải sau thời gian mở bài");
        }
        if (request.getCloseAt() != null && !request.getCloseAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            throw new IllegalArgumentException("Thời hạn đóng bài phải ở trong tương lai");
        }

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
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));
        classAccessService.assertCanManage(classEntity, teacherId);
        
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
        
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

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

    @Override
    @Transactional
    public QuizAssignmentResponse updateAssignment(Integer actorId, Long id, com.example.demo.dto.request.QuizAssignmentUpdateRequest request) {
        QuizAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài giao"));
        classAccessService.assertCanManage(assignment.getClassEntity(), actorId);
        LocalDateTime open = request.openAtSpecified() ? request.getOpenAt() : assignment.getOpenAt();
        LocalDateTime close = request.closeAtSpecified() ? request.getCloseAt() : assignment.getCloseAt();
        if (open != null && close != null && !close.isAfter(open))
            throw new com.example.demo.common.exception.BadRequestException("Hạn nộp phải sau giờ mở bài");
        if (request.closeAtSpecified() && close != null && !close.isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)))
            throw new com.example.demo.common.exception.BadRequestException("Hạn nộp phải ở tương lai");
        List<Attempt> attempts = attemptRepository.findByAssignment_AssignmentId(id);
        if (!attempts.isEmpty() && (request.getDurationMinutes() != null || request.getMaxAttempts() != null))
            throw new com.example.demo.common.exception.ConflictException("Không thể đổi thời lượng hoặc số lượt khi đã có bài làm");
        if (request.getStatus() != null) assignment.setStatus(request.getStatus());
        assignment.setOpenAt(open);
        assignment.setCloseAt(close);
        if (request.getDurationMinutes() != null) assignment.setDurationMinutes(request.getDurationMinutes());
        if (request.getMaxAttempts() != null) assignment.setMaxAttempts(request.getMaxAttempts());
        if (request.getResultPolicy() != null) assignment.setResultPolicy(request.getResultPolicy());
        if (request.closeAtSpecified()) {
            for (Attempt attempt : attempts) {
                if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                    LocalDateTime deadline = assignment.getDurationMinutes() == null ? null
                            : attempt.getStartedAt().plusMinutes(assignment.getDurationMinutes());
                    if (close != null && (deadline == null || close.isBefore(deadline))) deadline = close;
                    attempt.setDeadlineAt(deadline);
                    attemptRepository.save(attempt);
                }
            }
        }
        return mapToResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public void deleteAssignment(Integer actorId, Long id) {
        QuizAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài giao"));
        classAccessService.assertCanManage(assignment.getClassEntity(), actorId);
        if (!attemptRepository.findByAssignment_AssignmentId(id).isEmpty())
            throw new com.example.demo.common.exception.ConflictException("Bài giao đã có bài làm; hãy đóng bài để giữ kết quả");
        assignmentRepository.delete(assignment);
    }

    private QuizAssignmentResponse mapToResponse(QuizAssignment assignment) {
        QuizAssignmentResponse res = new QuizAssignmentResponse();
        res.setAssignmentId(assignment.getAssignmentId());
        res.setQuizVersionId(assignment.getQuizVersion().getVersionId());
        res.setQuizId(assignment.getQuizVersion().getQuiz().getQuizId());
        res.setQuizTitle(assignment.getQuizVersion().getQuiz().getTitle());
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
    private final com.example.demo.service.AttemptService attemptService;

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.example.demo.dto.response.AttemptSummaryResponse> getAssignmentProgress(
            Integer teacherId, Long assignmentId, org.springframework.data.domain.Pageable pageable) {
        QuizAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
        classAccessService.assertCanManage(assignment.getClassEntity(), teacherId);
        
        return attemptRepository.findByAssignment_AssignmentId(assignmentId, pageable)
                .map(attemptService::toSummary);
    }
}
