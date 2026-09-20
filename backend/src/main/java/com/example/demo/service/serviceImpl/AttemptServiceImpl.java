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

    @Value("${app.quiz.grace-period-seconds:0}")
    private int gracePeriodSeconds;

    @Override
    @Transactional
    public AttemptStartResponse startAttempt(Integer studentId, Long assignmentId) {
        QuizAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ASSIGNMENT_NOT_FOUND.getMessage()));

        User student = userRepository.findById(studentId).orElseThrow();

        LocalDateTime now = LocalDateTime.now();
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

        List<AttemptAnswer> answers = attemptAnswerRepository.findByAttempt_AttemptId(attemptId);
        
        // Visibility logic based on ResultPolicy for Student
        boolean showCorrectAnswer = isTeacher;
        boolean showScore = isTeacher;
        
        if (!isTeacher) {
            ResultPolicy policy = attempt.getAssignment().getResultPolicy();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime closeAt = attempt.getAssignment().getCloseAt();
            
            switch (policy) {
                case AFTER_SUBMISSION:
                    if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
                        showCorrectAnswer = true;
                        showScore = true;
                    }
                    break;
                case AFTER_GRADING:
                    if (attempt.getStatus() == AttemptStatus.GRADED) {
                        showCorrectAnswer = true;
                        showScore = true;
                    }
                    break;
                case AFTER_CLOSE_AT:
                    if (closeAt != null && closeAt.isBefore(now)) {
                        showCorrectAnswer = true;
                        showScore = true;
                    }
                    break;
                case NEVER:
                    showCorrectAnswer = false;
                    showScore = false;
                    break;
            }
        }

        return buildResponse(attempt, answers, showCorrectAnswer, showScore);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptResponse getMyResult(Integer studentId, Long assignmentId) {
        List<Attempt> attempts = attemptRepository.findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(assignmentId, studentId);
        if (attempts.isEmpty()) {
            throw new ResourceNotFoundException("No attempt found");
        }
        return fetchAttempt(studentId, attempts.get(0).getAttemptId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptResponse> getStudentQuizHistory(Integer studentId) {
        // Find all non-in-progress attempts for student
        // This is a simplified fetch, ideally fetch latest attempt per assignment
        return new ArrayList<>();
    }

    @Override
    @Transactional
    public void submitAnswer(Integer studentId, Long attemptId, AttemptAnswerSubmitRequest request) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));
                
        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new AccessDeniedException("Access denied");
        }
        
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException(ErrorCode.ATTEMPT_SUBMITTED.getMessage());
        }
        
        if (attempt.getDeadlineAt() != null && LocalDateTime.now().isAfter(attempt.getDeadlineAt().plusSeconds(gracePeriodSeconds))) {
            throw new IllegalStateException(ErrorCode.ATTEMPT_DEADLINE_PASSED.getMessage());
        }

        Optional<AttemptAnswer> existing = attemptAnswerRepository.findByAttempt_AttemptId(attemptId)
                .stream().filter(a -> a.getQuestionId().equals(request.getQuestionId())).findFirst();
                
        AttemptAnswer answer;
        if (existing.isPresent()) {
            answer = existing.get();
            if (!answer.getAnswerVersion().equals(request.getAnswerVersion())) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(AttemptAnswer.class, answer.getAnswerId());
            }
        } else {
            answer = new AttemptAnswer();
            answer.setAttempt(attempt);
            answer.setQuestionId(request.getQuestionId());
        }
        
        answer.setResponse(request.getResponse());
        attemptAnswerRepository.save(answer);
    }

    @Override
    @Transactional
    public AttemptResponse submitAttempt(Integer studentId, Long attemptId, SubmitType submitType) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));
                
        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new AccessDeniedException("Access denied");
        }

        // Idempotency
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return fetchAttempt(studentId, attemptId, false);
        }
        
        attempt.setSubmittedAt(LocalDateTime.now());
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
        Attempt attempt = attemptRepository.findById(attemptId)
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
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ATTEMPT_NOT_FOUND.getMessage()));
        
        User teacher = userRepository.findById(teacherId).orElseThrow();
        
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setSubmittedAt(null);
        attempt.setSubmitType(null);
        // Maybe recalculate deadline if needed, for simplicity we just reopen
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

        // Return a mock AttemptStartResponse without saving to DB
        AttemptStartResponse res = new AttemptStartResponse();
        res.setAttemptId(-1L);
        res.setAssignmentId(assignmentId);
        res.setAttemptNo(0);
        res.setStatus(AttemptStatus.IN_PROGRESS);
        res.setStartedAt(LocalDateTime.now());
        
        if (assignment.getDurationMinutes() != null) {
            res.setDeadlineAt(LocalDateTime.now().plusMinutes(assignment.getDurationMinutes()));
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
}
