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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
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
    private final com.example.demo.service.StudyNotificationService studyNotifications;
    private final com.example.demo.service.PremiumAccessService premiumAccessService;
    
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private QuizService self;

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
            premiumAccessService.requireActivePlan(teacherId);
            if (request.getSourceLectureId() == null) {
                throw new IllegalArgumentException("sourceLectureId is required when sourceType is AI");
            }
            sourceLecture = lectureRepository.findById(request.getSourceLectureId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lecture not found"));
            if (teacher.getRole() != UserRole.ADMIN
                    && !sourceLecture.getTeacher().getUserId().equals(teacherId)) {
                throw new AccessDeniedException("You cannot generate a quiz from another teacher's lecture");
            }
            quiz.setSourceLecture(sourceLecture);
        }

        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuestionDto> savedQuestions = new ArrayList<>();
        if (request.getSourceType() == SourceType.MANUAL) {
            if (request.getQuestions() != null) {
                savedQuestions = saveQuestions(savedQuiz, request.getQuestions());
            }
        } else if (request.getSourceType() == SourceType.AI) {
            // Asynchronous AI generation
            final String documentText = sourceLecture.getOriginalSource();
            final Long quizId = savedQuiz.getQuizId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> {
                        try {
                            String jsonDraft = llmService.generateQuizDraft(documentText, null);
                            List<QuestionDto> aiQuestions = objectMapper.readValue(jsonDraft, new TypeReference<>() {});
                            self.saveAiQuestions(quizId, aiQuestions);
                        } catch (Exception e) {
                            log.error("Failed to generate AI quiz draft for quiz {}", quizId, e);
                            self.saveAiError(quizId, "AI Lỗi: " + e.getMessage());
                        }
                    }, taskExecutor);
                }
            });
        }

        return mapToDetailResponse(savedQuiz, savedQuestions);
    }
    
    @Override
    @Transactional
    public void saveAiQuestions(Long quizId, List<QuestionDto> questions) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        saveQuestions(quiz, questions);
        studyNotifications.notify(quiz.getOwnerTeacher().getUserId(), "Tạo quiz AI hoàn tất", "Bản nháp quiz đã sẵn sàng để kiểm tra.",
                NotificationType.AI_COMPLETED, "QUIZ", quizId);
    }

    @Override
    @Transactional
    public void saveAiError(Long quizId, String error) {
        quizRepository.findById(quizId).ifPresent(quiz -> {
            quiz.setAiErrorMessage(error);
            studyNotifications.notify(quiz.getOwnerTeacher().getUserId(), "Tạo quiz AI thất bại", "Hãy xem chi tiết lỗi trong bài kiểm tra.",
                    NotificationType.AI_FAILED, "QUIZ", quizId);
            quizRepository.save(quiz);
        });
    }

    private List<QuestionDto> saveQuestions(Quiz quiz, List<QuestionDto> dtos) {
        questionRepository.deleteAll(questionRepository.findByQuiz_QuizIdOrderByOrderIndexAsc(quiz.getQuizId()));
        List<QuestionDto> savedDtos = new ArrayList<>();
        int order = 0;
        for (QuestionDto dto : dtos) {
            validateQuestion(dto);
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
            q.setPoints(dto.getPoints() != null ? dto.getPoints() : 1.0);
            q.setExplanation(dto.getExplanation());
            q.setOrderIndex(order++);
            Question saved = questionRepository.save(q);
            savedDtos.add(mapQuestionToDto(saved));
        }
        return savedDtos;
    }

    private void validateQuestion(QuestionDto dto) {
        if (dto == null || dto.getQuestionType() == null
                || !org.springframework.util.StringUtils.hasText(dto.getQuestionText())) {
            throw new IllegalArgumentException("Mỗi câu hỏi phải có loại câu hỏi và nội dung");
        }
        if (dto.getPoints() == null || !Double.isFinite(dto.getPoints()) || dto.getPoints() <= 0) {
            throw new IllegalArgumentException("Điểm của câu hỏi phải lớn hơn 0");
        }
        QuestionType type = dto.getQuestionType();
        if (type == QuestionType.MCQ_SINGLE || type == QuestionType.MCQ_MULTI || type == QuestionType.TRUE_FALSE) {
            List<String> options = dto.getOptions();
            if (options == null || options.isEmpty()
                    || options.stream().anyMatch(opt -> !org.springframework.util.StringUtils.hasText(opt))) {
                throw new IllegalArgumentException("Câu hỏi trắc nghiệm phải có các lựa chọn");
            }
            if (!org.springframework.util.StringUtils.hasText(dto.getCorrectAnswer())) {
                throw new IllegalArgumentException("Đáp án đúng phải là một trong các lựa chọn");
            }
            if (type == QuestionType.MCQ_SINGLE) {
                if (!isValidChoiceOption(dto.getCorrectAnswer(), options, 26)) {
                    throw new IllegalArgumentException("Đáp án đúng phải là một trong các lựa chọn");
                }
            } else if (type == QuestionType.TRUE_FALSE) {
                if (!isValidChoiceOption(dto.getCorrectAnswer(), options, Math.min(2, options.size()))) {
                    throw new IllegalArgumentException("Đáp án đúng phải là một trong các lựa chọn");
                }
            } else if (type == QuestionType.MCQ_MULTI) {
                String[] parts = dto.getCorrectAnswer().split(",", -1);
                if (parts.length == 0) {
                    throw new IllegalArgumentException("Đáp án đúng phải là một trong các lựa chọn");
                }
                for (String part : parts) {
                    if (!isValidChoiceOption(part, options, 26)) {
                        throw new IllegalArgumentException("Đáp án đúng phải là một trong các lựa chọn");
                    }
                }
            }
        }
    }

    private boolean isValidChoiceOption(String rawAnswer, List<String> options, int maxLetterChoices) {
        if (!org.springframework.util.StringUtils.hasText(rawAnswer) || options == null || options.isEmpty()) {
            return false;
        }
        String trimmed = rawAnswer.trim();
        if (trimmed.length() == 1) {
            char ch = Character.toUpperCase(trimmed.charAt(0));
            if (ch >= 'A' && ch <= 'Z') {
                int index = ch - 'A';
                if (index >= 0 && index < Math.min(options.size(), maxLetterChoices)
                        && org.springframework.util.StringUtils.hasText(options.get(index))) {
                    return true;
                }
            }
        }
        return options.stream().anyMatch(option -> option != null && option.trim().equalsIgnoreCase(trimmed));
    }

    @Override
    @Transactional
    public QuizDetailResponse updateQuizDraft(Integer teacherId, Long quizId, QuizUpdateRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        checkOwnership(quiz, teacherId);

        if (quiz.getStatus() != QuizStatus.DRAFT && quiz.getStatus() != QuizStatus.REVIEWED) {
            throw new IllegalStateException("Chỉ có thể cập nhật bài kiểm tra ở trạng thái DRAFT hoặc REVIEWED");
        }

        quiz.setTitle(request.getTitle());
        List<QuestionDto> savedQuestions;
        if (request.getQuestions() != null) {
            savedQuestions = saveQuestions(quiz, request.getQuestions());
        } else {
            savedQuestions = questionRepository.findByQuiz_QuizIdOrderByOrderIndexAsc(quizId)
                    .stream().map(this::mapQuestionToDto).collect(Collectors.toList());
        }
        
        // Auto transition to REVIEWED if updated manually
        quiz.setStatus(QuizStatus.REVIEWED);
        quizRepository.save(quiz);

        return mapToDetailResponse(quiz, savedQuestions);
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
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        checkOwnership(quiz, teacherId);
        quiz.setStatus(QuizStatus.CLOSED);
        quizRepository.save(quiz);
    }

    @Override
    @Transactional
    public void archiveQuiz(Integer teacherId, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        checkOwnership(quiz, teacherId);
        quiz.setStatus(QuizStatus.ARCHIVED);
        quizRepository.save(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizVersionResponse> getQuizVersions(Long quizId, UserPrincipal principal) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUIZ_NOT_FOUND.getMessage()));
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            checkOwnership(quiz, principal.getUserId());
        }
        return quizVersionRepository.findByQuiz_QuizIdOrderByVersionNoDesc(quizId)
                .stream().map(this::mapVersionToResponse).collect(Collectors.toList());
    }

    private void checkOwnership(Quiz quiz, Integer teacherId) {
        if (!quiz.getOwnerTeacher().getUserId().equals(teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác với bài kiểm tra này");
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
        response.setAiErrorMessage(quiz.getAiErrorMessage());
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

    @Override
    @Transactional(readOnly = true)
    public Page<QuizDetailResponse> getQuizzes(Integer userId, boolean isAdmin, String title, QuizStatus status, SourceType sourceType, Pageable pageable) {
        Specification<Quiz> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (!isAdmin) {
                predicates.add(cb.equal(root.get("ownerTeacher").get("userId"), userId));
            }
            if (title != null && !title.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (sourceType != null) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Quiz> quizzes = quizRepository.findAll(spec, pageable);
        List<Long> quizIds = quizzes.stream().map(Quiz::getQuizId).collect(Collectors.toList());
        
        java.util.Map<Long, List<Question>> questionsByQuizId = new java.util.HashMap<>();
        if (!quizIds.isEmpty()) {
            List<Question> allQuestions = questionRepository.findByQuiz_QuizIdInOrderByOrderIndexAsc(quizIds);
            for (Question q : allQuestions) {
                questionsByQuizId.computeIfAbsent(q.getQuiz().getQuizId(), k -> new ArrayList<>()).add(q);
            }
        }
        
        return quizzes.map(quiz -> {
            List<Question> qs = questionsByQuizId.getOrDefault(quiz.getQuizId(), new ArrayList<>());
            List<QuestionDto> dtos = qs.stream().map(this::mapQuestionToDto).collect(Collectors.toList());
            return mapToDetailResponse(quiz, dtos);
        });
    }
}
