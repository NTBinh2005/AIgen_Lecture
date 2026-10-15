package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.AttemptAnswerSubmitRequest;
import com.example.demo.dto.request.QuestionDto;
import com.example.demo.dto.response.AttemptAnswerResponse;
import com.example.demo.dto.response.AttemptResponse;
import com.example.demo.dto.response.AttemptStartResponse;
import com.example.demo.entity.*;
import com.example.demo.event.QuizEventPublisher;
import com.example.demo.repository.*;
import com.example.demo.service.AttemptService;
import com.example.demo.service.GradingService;
import com.example.demo.service.ClassAccessService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttemptServiceImpl implements AttemptService {

    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final QuizAssignmentRepository assignmentRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final UserRepository userRepository;
    private final QuizAuditLogRepository auditLogRepository;
    private final GradingService gradingService;
    private final QuizEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ClassAccessService classAccessService;
    private final com.example.demo.service.PremiumAccessService premiumAccessService;
    private final com.example.demo.service.StudyNotificationService studyNotifications;

    @Value("${app.quiz.grace-period-seconds:0}")
    private int gracePeriodSeconds;

    @Override
    @Transactional
    public AttemptStartResponse startAttempt(Integer studentId, Long assignmentId) {
        premiumAccessService.requireActivePlan(studentId);
        QuizAssignment assignment = assignmentRepository.lockByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ASSIGNMENT_NOT_FOUND.getMessage()));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh: " + studentId));
        classAccessService.assertActiveStudent(assignment.getClassEntity(), studentId);

        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (assignment.getStatus() == AssignmentStatus.CLOSED || 
            (assignment.getCloseAt() != null && assignment.getCloseAt().isBefore(now)) ||
            (assignment.getOpenAt() != null && assignment.getOpenAt().isAfter(now))) {
            throw new IllegalStateException("Quiz assignment is not available");
        }

        List<Attempt> existingAttempts = attemptRepository.findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(assignmentId, studentId);
        
        if (!existingAttempts.isEmpty()) {
            Attempt last = existingAttempts.get(0);
            if (last.getStatus() == AttemptStatus.IN_PROGRESS) {
                return buildStartResponse(last);
            }
        }

        if (assignment.getMaxAttempts() != null && existingAttempts.size() >= assignment.getMaxAttempts()) {
            throw new IllegalStateException("Maximum attempts reached");
        }

        int attemptNo = existingAttempts.size() + 1;

        Attempt attempt = new Attempt();
        attempt.setAssignment(assignment);
        attempt.setStudent(student);
        attempt.setAttemptNo(attemptNo);
        attempt.setQuizVersion(assignment.getQuizVersion());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        
        if (assignment.getDurationMinutes() != null) {
            attempt.setDeadlineAt(now.plusMinutes(assignment.getDurationMinutes()));
        }
        if (assignment.getCloseAt() != null && (attempt.getDeadlineAt() == null
                || assignment.getCloseAt().isBefore(attempt.getDeadlineAt()))) {
            attempt.setDeadlineAt(assignment.getCloseAt());
        }

        Attempt saved = attemptRepository.save(attempt);
        return buildStartResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptResponse fetchAttempt(Integer userId, Long attemptId, boolean isTeacher) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));

        if (!isTeacher && !attempt.getStudent().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        
        if (isTeacher) {
            classAccessService.assertCanManage(attempt.getAssignment().getClassEntity(), userId);
        }

        List<AttemptAnswer> answers = attemptAnswerRepository.findByAttempt_AttemptId(attemptId);
        
        // Visibility logic based on ResultPolicy for Student
        boolean showCorrectAnswer = isTeacher;
        boolean showScore = isTeacher;
        
        if (!isTeacher) {
            showScore = studentCanSeeResults(attempt);
            showCorrectAnswer = showScore;
        }

        return buildResponse(attempt, answers, showCorrectAnswer, showScore);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptResponse getMyResult(Integer studentId, Long assignmentId) {
        QuizAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài giao"));
        classAccessService.assertCanView(assignment.getClassEntity(), studentId);
        List<Attempt> attempts = attemptRepository.findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(assignmentId, studentId);
        if (attempts.isEmpty()) {
            throw new ResourceNotFoundException("No attempt found");
        }
        return fetchAttempt(studentId, attempts.get(0).getAttemptId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.example.demo.dto.response.AttemptSummaryResponse> getStudentQuizHistory(
            Integer studentId, org.springframework.data.domain.Pageable pageable) {
        return attemptRepository.findByStudent_UserIdAndStatusNot(
                        studentId, AttemptStatus.IN_PROGRESS, pageable)
                .map(attempt -> summary(attempt, studentCanSeeResults(attempt)));
    }

    @Override
    @Transactional
    public AttemptAnswerResponse submitAnswer(Integer studentId, Long attemptId, AttemptAnswerSubmitRequest request) {
        Attempt attempt = attemptRepository.lockByAttemptId(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));

        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new AccessDeniedException("Access denied");
        }

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException(ErrorCode.ATTEMPT_SUBMITTED.getMessage());
        }

        if (attempt.getDeadlineAt() != null && LocalDateTime.now(java.time.ZoneOffset.UTC).isAfter(attempt.getDeadlineAt().plusSeconds(gracePeriodSeconds))) {
            throw new IllegalStateException(ErrorCode.ATTEMPT_DEADLINE_PASSED.getMessage());
        }
        if (attempt.getAssignment().getStatus() == AssignmentStatus.CLOSED
                || (attempt.getAssignment().getCloseAt() != null && LocalDateTime.now(java.time.ZoneOffset.UTC)
                .isAfter(attempt.getAssignment().getCloseAt().plusSeconds(gracePeriodSeconds)))) {
            throw new IllegalStateException("Bài giao đã đóng, không thể lưu đáp án");
        }
        List<QuestionDto> snapshot;
        try {
            snapshot = objectMapper.readValue(attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể đọc câu hỏi của lượt làm", ex);
        }
        if (request.getQuestionId() == null || snapshot.stream().noneMatch(q -> request.getQuestionId().equals(q.getQuestionId()))) {
            throw new com.example.demo.common.exception.BadRequestException("Câu hỏi không thuộc lượt làm này");
        }

        Optional<AttemptAnswer> existing = attemptAnswerRepository.findByAttempt_AttemptId(attemptId)
                .stream().filter(a -> a.getQuestionId().equals(request.getQuestionId())).findFirst();

        AttemptAnswer answer;
        if (existing.isPresent()) {
            answer = existing.get();
            // Optimistic lock: client must supply the current version.
            // Treat null as 0 so that a client omitting the field is handled gracefully for first save.
            long clientVersion = request.getAnswerVersion() != null ? request.getAnswerVersion() : 0L;
            if (!answer.getAnswerVersion().equals(clientVersion)) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(AttemptAnswer.class, answer.getAnswerId());
            }
        } else {
            answer = new AttemptAnswer();
            answer.setAttempt(attempt);
            answer.setQuestionId(request.getQuestionId());
        }

        answer.setResponse(request.getResponse());
        AttemptAnswer saved = attemptAnswerRepository.saveAndFlush(answer);

        // Return the updated answer so FE can read the new answerVersion for the next autosave.
        AttemptAnswerResponse res = new AttemptAnswerResponse();
        res.setAnswerId(saved.getAnswerId());
        res.setQuestionId(saved.getQuestionId());
        res.setResponse(saved.getResponse());
        res.setAnswerVersion(saved.getAnswerVersion());
        return res;
    }

    @Override
    @Transactional
    public AttemptResponse submitAttempt(Integer studentId, Long attemptId, SubmitType submitType) {
        Attempt attempt = attemptRepository.lockByAttemptId(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));
                
        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new AccessDeniedException("Access denied");
        }

        // Idempotency
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return fetchAttempt(studentId, attemptId, false);
        }
        
        attempt.setSubmittedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        attempt.setSubmitType(submitType);
        
        // Auto-grade objective questions
        gradingService.autoGradeObjectiveQuestions(attempt);
        
        // Check if there are essay questions to determine next status
        boolean hasEssay = false;
        try {
            List<QuestionDto> questions = objectMapper.readValue(attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
            hasEssay = questions.stream().anyMatch(q -> q.getQuestionType() == QuestionType.ESSAY);
        } catch (Exception ignored) { }
        
        if (hasEssay) {
            attempt.setStatus(AttemptStatus.REVIEW_REQUIRED);
        } else {
            attempt.setStatus(AttemptStatus.GRADED);
        }

        attemptRepository.save(attempt);
        
        eventPublisher.publishAttemptSubmitted(attemptId, studentId);
        if (!hasEssay) {
            eventPublisher.publishAttemptGraded(attemptId, studentId, attempt.getFinalScore());
        }
        
        return fetchAttempt(studentId, attemptId, false);
    }

    @Override
    @Transactional
    public void recordSignal(Integer studentId, Long attemptId) {
        Attempt attempt = attemptRepository.lockByAttemptId(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));
                
        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new AccessDeniedException("Access denied");
        }
        
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            attempt.setTabSwitchSignalCount(attempt.getTabSwitchSignalCount() + 1);
            attemptRepository.save(attempt);
        }
    }

    @Override
    @Transactional
    public void reopenAttempt(Integer teacherId, Long attemptId) {
        Attempt attempt = attemptRepository.lockByAttemptId(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));
        
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + teacherId));
        classAccessService.assertCanManage(attempt.getAssignment().getClassEntity(), teacherId);
        
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        studyNotifications.notify(attempt.getStudent().getUserId(), "Lượt làm đã được mở lại", "Giáo viên đã mở lại lượt làm của bạn.",
                NotificationType.ATTEMPT_REOPENED, "ATTEMPT", attemptId + "/reopened/" + java.time.Instant.now().toEpochMilli());
        attempt.setSubmittedAt(null);
        attempt.setSubmitType(null);
        attempt.setFinalScore(null);
        attempt.setObjectiveScore(null);
        for (AttemptAnswer answer : attemptAnswerRepository.findByAttempt_AttemptId(attemptId)) {
            answer.setIsCorrect(null); answer.setPointsAwarded(null); answer.setTeacherFinalScore(null);
            answer.setAiSuggestedScore(null); answer.setGradedAt(null); answer.setGradedBy(null);
        }
        attempt.setDeadlineAt(attempt.getAssignment().getDurationMinutes() == null
                ? null
                : LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(attempt.getAssignment().getDurationMinutes()));
        if (attempt.getAssignment().getCloseAt() != null && (attempt.getDeadlineAt() == null
                || attempt.getAssignment().getCloseAt().isBefore(attempt.getDeadlineAt()))) {
            attempt.setDeadlineAt(attempt.getAssignment().getCloseAt());
        }
        attemptRepository.save(attempt);
        
        QuizAuditLog logEntry = new QuizAuditLog();
        logEntry.setActionType(QuizAuditActionType.REOPEN);
        logEntry.setActor(teacher);
        logEntry.setEntityType("ATTEMPT");
        logEntry.setEntityId(attemptId);
        auditLogRepository.save(logEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptStartResponse startPreview(Integer teacherId, Long assignmentId) {
        QuizAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ASSIGNMENT_NOT_FOUND.getMessage()));
        classAccessService.assertCanManage(assignment.getClassEntity(), teacherId);

        // Return a mock AttemptStartResponse without saving to DB
        AttemptStartResponse res = new AttemptStartResponse();
        res.setAttemptId(-1L);
        res.setAssignmentId(assignmentId);
        res.setAttemptNo(0);
        res.setStatus(AttemptStatus.IN_PROGRESS);
        res.setStartedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        
        if (assignment.getDurationMinutes() != null) {
            res.setDeadlineAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(assignment.getDurationMinutes()));
        }
        
        try {
            List<QuestionDto> questions = objectMapper.readValue(assignment.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
            res.setQuestions(questions);
        } catch (Exception e) {
            res.setQuestions(new ArrayList<>());
        }
        
        return res;
    }

    private AttemptStartResponse buildStartResponse(Attempt attempt) {
        AttemptStartResponse res = new AttemptStartResponse();
        res.setAttemptId(attempt.getAttemptId());
        res.setAssignmentId(attempt.getAssignment().getAssignmentId());
        res.setAttemptNo(attempt.getAttemptNo());
        res.setStatus(attempt.getStatus());
        res.setStartedAt(attempt.getStartedAt());
        res.setDeadlineAt(attempt.getDeadlineAt());
        
        try {
            List<QuestionDto> questions = objectMapper.readValue(attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
            // Strip correct answers for students
            for (QuestionDto q : questions) {
                q.setCorrectAnswer(null);
                q.setExplanation(null);
            }
            res.setQuestions(questions);
        } catch (Exception e) {
            res.setQuestions(new ArrayList<>());
        }
        
        return res;
    }

    private AttemptResponse buildResponse(Attempt attempt, List<AttemptAnswer> answers, boolean showCorrectAnswer, boolean showScore) {
        AttemptResponse res = new AttemptResponse();
        res.setAttemptId(attempt.getAttemptId());
        res.setAssignmentId(attempt.getAssignment().getAssignmentId());
        res.setQuizTitle(attempt.getQuizVersion().getQuiz().getTitle());
        res.setClassId(attempt.getAssignment().getClassEntity().getClassId());
        res.setClassName(attempt.getAssignment().getClassEntity().getClassName());
        res.setStudentId(attempt.getStudent().getUserId());
        res.setStudentName(attempt.getStudent().getName());
        res.setAttemptNo(attempt.getAttemptNo());
        res.setStatus(attempt.getStatus());
        res.setStartedAt(attempt.getStartedAt());
        res.setDeadlineAt(attempt.getDeadlineAt());
        res.setSubmittedAt(attempt.getSubmittedAt());
        
        if (showScore) {
            res.setFinalScore(attempt.getFinalScore());
            res.setObjectiveScore(attempt.getObjectiveScore());
        }

        List<QuestionDto> questions = new ArrayList<>();
        try {
            questions = objectMapper.readValue(attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
        } catch (Exception ignored) {}

        List<AttemptAnswerResponse> answerResponses = new ArrayList<>();
        for (AttemptAnswer ans : answers) {
            AttemptAnswerResponse ar = new AttemptAnswerResponse();
            ar.setAnswerId(ans.getAnswerId());
            ar.setQuestionId(ans.getQuestionId());
            ar.setResponse(ans.getResponse());
            ar.setAnswerVersion(ans.getAnswerVersion());
            
            if (showScore) {
                ar.setIsCorrect(ans.getIsCorrect());
                ar.setPointsAwarded(ans.getPointsAwarded());
                ar.setTeacherFinalScore(ans.getTeacherFinalScore());
                ar.setAiSuggestedScore(ans.getAiSuggestedScore());
            }
            
            if (showCorrectAnswer) {
                questions.stream().filter(q -> q.getQuestionId().equals(ans.getQuestionId())).findFirst().ifPresent(q -> {
                    ar.setCorrectAnswer(q.getCorrectAnswer());
                    ar.setExplanation(q.getExplanation());
                });
            }
            answerResponses.add(ar);
        }
        res.setAnswers(answerResponses);
        return res;
    }

    @Override
    public com.example.demo.dto.response.AttemptSummaryResponse toSummary(Attempt attempt) {
        return summary(attempt, true);
    }

    private boolean studentCanSeeResults(Attempt attempt) {
        return switch (attempt.getAssignment().getResultPolicy()) {
            case NEVER -> false;
            case AFTER_SUBMISSION -> attempt.getStatus() != AttemptStatus.IN_PROGRESS;
            case AFTER_GRADING -> attempt.getStatus() == AttemptStatus.GRADED;
            case AFTER_CLOSE_AT -> attempt.getStatus() != AttemptStatus.IN_PROGRESS
                    && attempt.getAssignment().getCloseAt() != null
                    && !attempt.getAssignment().getCloseAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC));
        };
    }

    private com.example.demo.dto.response.AttemptSummaryResponse summary(Attempt attempt, boolean showScore) {
        return new com.example.demo.dto.response.AttemptSummaryResponse(
                attempt.getAttemptId(),
                attempt.getAssignment().getAssignmentId(),
                attempt.getQuizVersion().getQuiz().getTitle(),
                attempt.getAssignment().getClassEntity().getClassId(),
                attempt.getAssignment().getClassEntity().getClassName(),
                attempt.getStudent().getUserId(),
                attempt.getStudent().getName(),
                attempt.getAttemptNo(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getDeadlineAt(),
                attempt.getSubmittedAt(),
                showScore ? attempt.getFinalScore() : null,
                showScore ? attempt.getObjectiveScore() : null);
    }
}
