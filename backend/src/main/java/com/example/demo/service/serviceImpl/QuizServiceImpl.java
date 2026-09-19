package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QuestionDto;
import com.example.demo.dto.request.QuizCreateRequest;
import com.example.demo.dto.request.QuizUpdateRequest;
import com.example.demo.dto.response.QuizDetailResponse;
import com.example.demo.dto.response.QuizVersionResponse;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.LlmService;
import com.example.demo.service.QuizService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final QuestionRepository questionRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;
    private final QuizAuditLogRepository auditLogRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @Override
    @Transactional
    public QuizDetailResponse createQuizDraft(Integer teacherId, QuizCreateRequest request) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND.getMessage()));

        Quiz quiz = new Quiz();
        quiz.setOwnerTeacher(teacher);
        quiz.setTitle(request.getTitle());
        quiz.setSourceType(request.getSourceType());
        quiz.setStatus(QuizStatus.DRAFT);

        Lecture sourceLecture = null;
        if (request.getSourceType() == SourceType.AI) {
            if (request.getSourceLectureId() == null) {
                throw new IllegalArgumentException("sourceLectureId is required when sourceType is AI");
            }
            sourceLecture = lectureRepository.findById(request.getSourceLectureId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lecture not found"));
            quiz.setSourceLecture(sourceLecture);
        }

        Quiz savedQuiz = quizRepository.save(quiz);

        if (request.getSourceType() == SourceType.MANUAL) {
            if (request.getQuestions() != null) {
                saveQuestions(savedQuiz, request.getQuestions());
            }
        } else if (request.getSourceType() == SourceType.AI) {
            // Asynchronous AI generation
            final String documentText = sourceLecture.getOriginalSource();
            final Long quizId = savedQuiz.getQuizId();
            CompletableFuture.runAsync(() -> {
                try {
                    String jsonDraft = llmService.generateQuizDraft(documentText, null);
                    List<QuestionDto> aiQuestions = objectMapper.readValue(jsonDraft, new TypeReference<>() {});
                    
                    // Run in a separate transaction or manual save (assuming single simple inserts for now)
                    // It's better to call a synchronized/transactional method, but we can do it directly:
                    saveAiQuestions(quizId, aiQuestions);
                } catch (Exception e) {
                    log.error("Failed to generate AI quiz draft for quiz {}", quizId, e);
                }
            }, taskExecutor);
        }

        return mapToDetailResponse(savedQuiz, new ArrayList<>());
    }
    
    @Transactional
    public void saveAiQuestions(Long quizId, List<QuestionDto> questions) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        saveQuestions(quiz, questions);
    }

    private void saveQuestions(Quiz quiz, List<QuestionDto> dtos) {
        questionRepository.deleteAll(questionRepository.findByQuiz_QuizIdOrderByOrderIndexAsc(quiz.getQuizId()));
        int order = 0;
        for (QuestionDto dto : dtos) {
            Question q = new Question();
            q.setQuiz(quiz);
            q.setQuestionType(dto.getQuestionType());
            q.setQuestionText(dto.getQuestionText());
            try {
                q.setOptions(dto.getOptions() != null ? objectMapper.writeValueAsString(dto.getOptions()) : "[]");
            } catch (JsonProcessingException e) {
                q.setOptions("[]");
            }
            q.setCorrectAnswer(dto.getCorrectAnswer());
            q.setPoints(dto.getPoints() != null ? dto.getPoints() : 1);
            q.setExplanation(dto.getExplanation());
            q.setOrderIndex(order++);
            questionRepository.save(q);
        }
    }

    @Override
    @Transactional
    public QuizDetailResponse updateQuizDraft(Integer teacherId, Long quizId, QuizUpdateRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        checkOwnership(quiz, teacherId);

        if (quiz.getStatus() != QuizStatus.DRAFT && quiz.getStatus() != QuizStatus.REVIEWED) {
            throw new IllegalStateException("Can only update quiz in DRAFT or REVIEWED status");
        }

        quiz.setTitle(request.getTitle());
        if (request.getQuestions() != null) {
            saveQuestions(quiz, request.getQuestions());
        }
        
        // Auto transition to REVIEWED if updated manually
        quiz.setStatus(QuizStatus.REVIEWED);
        quizRepository.save(quiz);

        return mapToDetailResponse(quiz, request.getQuestions());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizDetailResponse getQuiz(Long quizId, UserPrincipal principal) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        
        boolean isTeacher = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
        if (isTeacher) {
            checkOwnership(quiz, principal.getUserId());
        }

        List<Question> questions = questionRepository.findByQuiz_QuizIdOrderByOrderIndexAsc(quizId);
        List<QuestionDto> dtos = questions.stream().map(this::mapQuestionToDto).collect(Collectors.toList());
        return mapToDetailResponse(quiz, dtos);
    }

    @Override
    @Transactional
    public QuizVersionResponse publishQuiz(Integer teacherId, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        checkOwnership(quiz, teacherId);

        List<Question> questions = questionRepository.findByQuiz_QuizIdOrderByOrderIndexAsc(quizId);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException(ErrorCode.QUIZ_PUBLISH_VALIDATION.getMessage() + ": No questions");
        }

        int maxVersion = quizVersionRepository.findByQuiz_QuizIdOrderByVersionNoDesc(quizId)
                .stream().findFirst().map(QuizVersion::getVersionNo).orElse(0);

        List<QuestionDto> dtos = questions.stream().map(this::mapQuestionToDto).collect(Collectors.toList());
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(dtos);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize questions snapshot", e);
        }

        QuizVersion version = new QuizVersion();
        version.setQuiz(quiz);
        version.setVersionNo(maxVersion + 1);
        version.setQuestionsSnapshot(snapshot);
        version.setPublishedBy(quiz.getOwnerTeacher());
        
        QuizVersion savedVersion = quizVersionRepository.save(version);
        
        quiz.setStatus(QuizStatus.PUBLISHED);
        quizRepository.save(quiz);

        // Audit Log
        QuizAuditLog logEntry = new QuizAuditLog();
        logEntry.setActionType(QuizAuditActionType.PUBLISH);
        logEntry.setActor(quiz.getOwnerTeacher());
        logEntry.setEntityType("QUIZ");
        logEntry.setEntityId(quizId);
        logEntry.setDetailJson("{\"versionNo\": " + savedVersion.getVersionNo() + "}");
        auditLogRepository.save(logEntry);

        return mapVersionToResponse(savedVersion);
    }

    @Override
    @Transactional
    public void closeQuiz(Integer teacherId, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        checkOwnership(quiz, teacherId);
        quiz.setStatus(QuizStatus.CLOSED);
        quizRepository.save(quiz);
    }

    @Override
    @Transactional
    public void archiveQuiz(Integer teacherId, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        checkOwnership(quiz, teacherId);
        quiz.setStatus(QuizStatus.ARCHIVED);
        quizRepository.save(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizVersionResponse> getQuizVersions(Long quizId, UserPrincipal principal) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        checkOwnership(quiz, principal.getUserId());
        return quizVersionRepository.findByQuiz_QuizIdOrderByVersionNoDesc(quizId)
                .stream().map(this::mapVersionToResponse).collect(Collectors.toList());
    }

    private void checkOwnership(Quiz quiz, Integer teacherId) {
        if (!quiz.getOwnerTeacher().getUserId().equals(teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác với quiz này");
        }
    }

    private QuestionDto mapQuestionToDto(Question q) {
        QuestionDto dto = new QuestionDto();
        dto.setQuestionId(q.getQuestionId());
        dto.setQuestionType(q.getQuestionType());
        dto.setQuestionText(q.getQuestionText());
        try {
            dto.setOptions(objectMapper.readValue(q.getOptions(), new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException e) {
            dto.setOptions(new ArrayList<>());
        }
        dto.setCorrectAnswer(q.getCorrectAnswer());
        dto.setPoints(q.getPoints());
        dto.setExplanation(q.getExplanation());
        dto.setOrderIndex(q.getOrderIndex());
        return dto;
    }

    private QuizDetailResponse mapToDetailResponse(Quiz quiz, List<QuestionDto> questions) {
        QuizDetailResponse response = new QuizDetailResponse();
        response.setQuizId(quiz.getQuizId());
        response.setTitle(quiz.getTitle());
        response.setSourceType(quiz.getSourceType());
        if (quiz.getSourceLecture() != null) {
            response.setSourceLectureId(quiz.getSourceLecture().getLectureId());
        }
        response.setStatus(quiz.getStatus());
        response.setCreatedAt(quiz.getCreatedAt());
        response.setQuestions(questions);
        return response;
    }

    private QuizVersionResponse mapVersionToResponse(QuizVersion version) {
        QuizVersionResponse res = new QuizVersionResponse();
        res.setVersionId(version.getVersionId());
        res.setQuizId(version.getQuiz().getQuizId());
        res.setVersionNo(version.getVersionNo());
        res.setPublishedAt(version.getPublishedAt());
        res.setPublishedBy(version.getPublishedBy().getUserId());
        res.setQuestionsSnapshot(version.getQuestionsSnapshot());
        return res;
    }
}
