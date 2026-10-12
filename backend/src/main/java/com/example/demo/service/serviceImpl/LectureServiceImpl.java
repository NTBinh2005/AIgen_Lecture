package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.request.LectureUpdateRequest;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.dto.response.LectureVersionResponse;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.AiElement;
import com.example.demo.entity.LectureAccessScope;
import com.example.demo.entity.LectureCollaborator;
import com.example.demo.entity.LectureStatus;
import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.VideoStatus;
import com.example.demo.repository.LectureCollaboratorRepository;
import com.example.demo.repository.AiElementRepository;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.LectureVersionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.LectureAccessGrantVerifier;
import com.example.demo.service.LectureService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.demo.entity.Question;
import com.example.demo.entity.QuestionType;
import com.example.demo.entity.Quiz;
import com.example.demo.entity.QuizStatus;
import com.example.demo.entity.SourceType;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.QuizRepository;

@Service
@RequiredArgsConstructor
public class LectureServiceImpl implements LectureService {
    private final LectureRepository lectureRepository;
    private final LectureVersionRepository lectureVersionRepository;
    private final LectureCollaboratorRepository lectureCollaboratorRepository;
    private final UserRepository userRepository;
    private final AiElementRepository aiElementRepository;
    private final LectureAccessGrantVerifier accessGrantVerifier;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public LectureResponse createLecture(Integer teacherId, LectureCreateRequest request) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
        if (teacher.getRole() != UserRole.TEACHER && teacher.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Only a teacher or admin can own a lecture");
        }

        Lecture lecture = new Lecture();
        lecture.setTeacher(teacher);
        lecture.setTitle(requireText(request.getTitle(), "title"));
        lecture.setOriginalSource(trimToNull(request.getOriginalSource()));
        lecture.setAccessScope(request.getAccessScope() == null
                ? LectureAccessScope.PRIVATE
                : request.getAccessScope());
        lecture.setStatus(LectureStatus.DRAFT);
        lecture.setVideoStatus(VideoStatus.PENDING);
        lecture.setCurrentVersionNumber(1);
        Lecture saved = lectureRepository.save(lecture);

        LectureVersion version = new LectureVersion();
        version.setLecture(saved);
        version.setVersionNumber(1);
        version.setTitle(saved.getTitle());
        version.setContent(saved.getOriginalSource());
        version.setSlideContent(writeSlides(request.getSlides()));
        version.setStatus(LectureVersionStatus.DRAFT);
        version.setAiGenerated(false);
        version.setCreatedBy(teacherId);
        version = lectureVersionRepository.save(version);

        saved.setCurrentVersionId(version.getLectureVersionId());
        lectureRepository.save(saved);

        if (request.getQuizzes() != null && !request.getQuizzes().isEmpty()) {
            saveQuizzesForLecture(saved, teacher, request.getQuizzes());
        }
        saveLegacyQuizzes(saved, request.getQuizzes());
        // Video render is dispatched by LectureVideoRenderCoordinator, which picks up
        // PENDING lectures without a job id; publishing an event here would render twice.

        return toResponse(saved, version, teacherId, teacher.getRole() == UserRole.ADMIN);
    }

    private void saveQuizzesForLecture(Lecture lecture, User teacher, List<LectureCreateRequest.QuizDto> quizDtos) {
        Quiz quiz = new Quiz();
        quiz.setOwnerTeacher(teacher);
        quiz.setTitle("Trắc nghiệm - " + lecture.getTitle());
        quiz.setSourceType(SourceType.MANUAL);
        quiz.setSourceLecture(lecture);
        quiz.setStatus(QuizStatus.DRAFT);
        Quiz savedQuiz = quizRepository.save(quiz);

        int order = 0;
        for (LectureCreateRequest.QuizDto dto : quizDtos) {
            if (!StringUtils.hasText(dto.getQuestionText())) continue;
            Question q = new Question();
            q.setQuiz(savedQuiz);
            q.setQuestionType(QuestionType.MCQ_SINGLE);
            q.setQuestionText(dto.getQuestionText().trim());
            try {
                q.setOptions(dto.getOptions() != null ? objectMapper.writeValueAsString(dto.getOptions()) : "[]");
            } catch (JsonProcessingException e) {
                q.setOptions("[]");
            }
            q.setCorrectAnswer(dto.getCorrectAnswer() != null ? dto.getCorrectAnswer().trim() : "A");
            q.setPoints(1.0);
            q.setOrderIndex(order++);
            questionRepository.save(q);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LectureResponse> getLecturesByTeacher(
            Integer teacherId,
            boolean isAdmin,
            String titleKeyword,
            Pageable pageable) {
        Page<Lecture> lectures;
        if (isAdmin) {
            lectures = StringUtils.hasText(titleKeyword)
                    ? lectureRepository.findByTitleContainingIgnoreCase(titleKeyword.trim(), pageable)
                    : lectureRepository.findAll(pageable);
        } else {
            lectures = StringUtils.hasText(titleKeyword)
                    ? lectureRepository.findOwnedOrSharedByTitle(teacherId, titleKeyword.trim(), pageable)
                    : lectureRepository.findOwnedOrShared(teacherId, pageable);
        }
        return lectures.map(lecture -> toResponse(
                lecture,
                findVersionQuietly(lecture.getCurrentVersionId()),
                teacherId,
                false));
    }

    /**
     * FIX #1: Bài giảng học sinh được xem — đã PUBLISHED, phạm vi CLASS, và được giao
     * cho một lớp mà học sinh đang có enrollment ACTIVE.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<LectureResponse> getAllLecturesForStudent(
            Integer studentId, String titleKeyword, Pageable pageable) {
        String title = StringUtils.hasText(titleKeyword) ? titleKeyword.trim() : "";
        return lectureRepository.findPublishedForStudent(studentId, title, pageable)
                .map(lecture -> toResponse(
                        lecture,
                        findVersionQuietly(lecture.getPublishedVersionId()),
                        studentId,
                        false));
    }

    @Override
    @Transactional(readOnly = true)
    public LectureResponse getLecture(Long lectureId, Integer requesterId, UserPrincipal principal) {
        Lecture lecture = findLectureOrThrow(lectureId);
        boolean admin = hasRole(principal, "ROLE_ADMIN");
        boolean student = hasRole(principal, "ROLE_STUDENT");

        if (student) {
            assertStudentGrant(lecture, requesterId);
            LectureVersion published = requireVersion(lecture.getPublishedVersionId());
            return toResponse(lecture, published, requesterId, false);
        }

        assertCanReadOrEdit(lecture, requesterId, admin);
        return toResponse(
                lecture,
                findVersionQuietly(lecture.getCurrentVersionId()),
                requesterId,
                admin);
    }

    /**
     * FIX #8: Áp dụng cùng điều kiện quyền như GET /lectures/{id} cho các API phụ thuộc
     * (comment, quiz, video-status). Ném AccessDeniedException nếu không có quyền.
     */
    @Override
    @Transactional(readOnly = true)
    public void assertCanAccessLecture(Long lectureId, Integer requesterId, UserPrincipal principal) {
        Lecture lecture = findLectureOrThrow(lectureId);
        if (hasRole(principal, "ROLE_STUDENT")) {
            assertStudentGrant(lecture, requesterId);
        } else {
            assertCanReadOrEdit(lecture, requesterId, hasRole(principal, "ROLE_ADMIN"));
        }
    }

    @Override
    @Transactional
    public LectureResponse updateLectureTitle(Long lectureId, Integer requesterId, String newTitle) {
        LectureUpdateRequest request = new LectureUpdateRequest();
        request.setTitle(newTitle);
        return updateLecture(lectureId, requesterId, false, request);
    }

    @Override
    @Transactional
    public LectureResponse updateLecture(
            Long lectureId,
            Integer requesterId,
            boolean admin,
            LectureUpdateRequest request) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertCanReadOrEdit(lecture, requesterId, admin);
        assertNotArchived(lecture);

        LectureVersion current = ensureCurrentVersion(lecture, requesterId);
        String nextTitle = request.getTitle() == null
                ? current.getTitle()
                : requireText(request.getTitle(), "title");
        String nextContent = request.getContent() == null
                ? current.getContent()
                : trimToNull(request.getContent());

        boolean immutable = current.getStatus() == LectureVersionStatus.PUBLISHED
                || current.getStatus() == LectureVersionStatus.SUPERSEDED;
        if (immutable) {
            current = copyAsNewDraft(lecture, current, requesterId);
        }

        current.setTitle(nextTitle);
        current.setContent(nextContent);
        if (request.getSlides() != null) {
            current.setSlideContent(writeSlides(request.getSlides()));
        }
        current.setStatus(LectureVersionStatus.DRAFT);
        current = lectureVersionRepository.save(current);

        lecture.setTitle(nextTitle);
        lecture.setOriginalSource(nextContent);
        if (request.getAccessScope() != null) {
            lecture.setAccessScope(request.getAccessScope());
        }
        if (lecture.getStatus() != LectureStatus.PUBLISHED) {
            lecture.setStatus(LectureStatus.DRAFT);
        }
        lecture.setCurrentVersionId(current.getLectureVersionId());
        lecture.setCurrentVersionNumber(current.getVersionNumber());
        if (request.getSlides() != null) {
            lecture.setVideoJobId(null);
            lecture.setVideoUrl(null);
            lecture.setVideoErrorMessage(null);
            lecture.setVideoStatus(VideoStatus.PENDING);
        }
        lectureRepository.save(lecture);
        return toResponse(lecture, current, requesterId, admin);
    }

    @Override
    @Transactional
    public LectureResponse publishLecture(
            Long lectureId,
            UUID versionId,
            Integer requesterId,
            boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertOwnerOrAdmin(lecture, requesterId, admin);
        assertNotArchived(lecture);

        LectureVersion selected = versionId == null
                ? ensureCurrentVersion(lecture, requesterId)
                : lectureVersionRepository.findById(versionId)
                        .filter(version -> version.getLecture().getLectureId().equals(lectureId))
                        .orElseThrow(() -> new ResourceNotFoundException("Lecture version not found: " + versionId));

        if (selected.getStatus() == LectureVersionStatus.SUPERSEDED) {
            throw new ConflictException("A superseded version cannot be published again; restore it first");
        }
        validatePublishable(lecture, selected);

        if (lecture.getPublishedVersionId() != null
                && !lecture.getPublishedVersionId().equals(selected.getLectureVersionId())) {
            lectureVersionRepository.findById(lecture.getPublishedVersionId()).ifPresent(previous -> {
                previous.setStatus(LectureVersionStatus.SUPERSEDED);
                lectureVersionRepository.save(previous);
            });
        }

        Instant now = Instant.now();
        selected.setStatus(LectureVersionStatus.PUBLISHED);
        selected.setPublishedAt(now);
        selected = lectureVersionRepository.save(selected);

        lecture.setTitle(selected.getTitle());
        lecture.setOriginalSource(selected.getContent());
        lecture.setStatus(LectureStatus.PUBLISHED);
        lecture.setPublishedVersionId(selected.getLectureVersionId());
        lecture.setCurrentVersionId(selected.getLectureVersionId());
        lecture.setCurrentVersionNumber(selected.getVersionNumber());
        lecture.setPublishedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        lectureRepository.save(lecture);
        return toResponse(lecture, selected, requesterId, admin);
    }

    @Override
    @Transactional
    public void deleteLecture(Long lectureId, Integer requesterId) {
        archiveLecture(lectureId, requesterId, false);
    }

    @Override
    @Transactional
    public void archiveLecture(Long lectureId, Integer requesterId, boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertOwnerOrAdmin(lecture, requesterId, admin);
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            return;
        }
        lecture.setStatus(LectureStatus.ARCHIVED);
        lecture.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        lectureRepository.save(lecture);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LectureVersionResponse> getVersions(
            Long lectureId,
            Integer requesterId,
            boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertCanReadOrEdit(lecture, requesterId, admin);
        return lectureVersionRepository.findByLecture_LectureIdOrderByVersionNumberDesc(lectureId)
                .stream()
                .map(LectureVersionResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LectureVersionResponse getVersion(
            Long lectureId,
            UUID versionId,
            Integer requesterId,
            UserPrincipal principal) {
        Lecture lecture = findLectureOrThrow(lectureId);
        LectureVersion version = lectureVersionRepository.findById(versionId)
                .filter(candidate -> candidate.getLecture().getLectureId().equals(lectureId))
                .orElseThrow(() -> new ResourceNotFoundException("Lecture version not found: " + versionId));
        if (hasRole(principal, "ROLE_STUDENT")) {
            if (version.getStatus() != LectureVersionStatus.PUBLISHED) {
                throw new AccessDeniedException("Only the published version is available to students");
            }
            assertStudentGrant(lecture, requesterId);
        } else {
            assertCanReadOrEdit(lecture, requesterId, hasRole(principal, "ROLE_ADMIN"));
        }
        return LectureVersionResponse.from(version);
    }

    @Override
    @Transactional(readOnly = true)
    public LectureVersionResponse getPublishedVersionContract(UUID versionId) {
        LectureVersion version = requireVersion(versionId);
        if (version.getStatus() != LectureVersionStatus.PUBLISHED) {
            throw new ConflictException("Content version is not published");
        }
        return LectureVersionResponse.from(version);
    }

    @Override
    @Transactional(readOnly = true)
    public LectureVersionResponse getSourceVersionForPresentation(
            UUID versionId,
            Integer requesterId,
            boolean admin) {
        LectureVersion version = requireVersion(versionId);
        assertCanReadOrEdit(version.getLecture(), requesterId, admin);
        if (version.getStatus() != LectureVersionStatus.PUBLISHED) {
            throw new ConflictException("A presentation can only use a published LectureVersion");
        }
        return LectureVersionResponse.from(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> getCollaborators(Long lectureId, Integer requesterId, boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertCanReadOrEdit(lecture, requesterId, admin);
        return lectureCollaboratorRepository.findByLecture_LectureIdOrderByCreatedAtAsc(lectureId)
                .stream()
                .map(LectureCollaborator::getCollaboratorUserId)
                .toList();
    }

    @Override
    @Transactional
    public void addCollaborator(
            Long lectureId,
            Integer collaboratorId,
            Integer requesterId,
            boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertOwnerOrAdmin(lecture, requesterId, admin);
        // FIX #22: Chỉ TEACHER (hoặc ADMIN) mới được làm cộng tác viên biên tập bài giảng —
        // không cho thêm STUDENT vì sẽ cấp quyền sửa bài cho học sinh.
        User collaboratorUser = userRepository.findById(collaboratorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + collaboratorId));
        if (collaboratorUser.getRole() != UserRole.TEACHER
                && collaboratorUser.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Chỉ giáo viên mới có thể là cộng tác viên bài giảng");
        }
        if (Objects.equals(lecture.getTeacher().getUserId(), collaboratorId)) {
            throw new ConflictException("The owner is already an editor");
        }
        if (lectureCollaboratorRepository
                .existsByLecture_LectureIdAndCollaboratorUserId(lectureId, collaboratorId)) {
            return;
        }
        LectureCollaborator collaborator = new LectureCollaborator();
        collaborator.setLecture(lecture);
        collaborator.setCollaboratorUserId(collaboratorId);
        collaborator.setCreatedBy(requesterId);
        lectureCollaboratorRepository.save(collaborator);
    }

    @Override
    @Transactional
    public void removeCollaborator(
            Long lectureId,
            Integer collaboratorId,
            Integer requesterId,
            boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertOwnerOrAdmin(lecture, requesterId, admin);
        lectureCollaboratorRepository.deleteByLecture_LectureIdAndCollaboratorUserId(
                lectureId, collaboratorId);
    }

    @Override
    @Transactional(readOnly = true)
    public Lecture getVideoStatus(Long lectureId) {
        return findLectureOrThrow(lectureId);
    }

    @Override
    @Transactional
    public Lecture requestVideoRender(Long lectureId, Integer requesterId, boolean admin) {
        Lecture lecture = findLectureOrThrow(lectureId);
        assertCanReadOrEdit(lecture, requesterId, admin);
        assertNotArchived(lecture);

        LectureVersion current = requireVersion(lecture.getCurrentVersionId());
        if (!StringUtils.hasText(current.getSlideContent())) {
            throw new BadRequestException("Lecture has no slides to render");
        }

        lecture.setVideoJobId(null);
        lecture.setVideoUrl(null);
        lecture.setVideoErrorMessage(null);
        lecture.setVideoStatus(VideoStatus.PENDING);
        Lecture saved = lectureRepository.save(lecture);
        return saved;
    }

    private LectureVersion ensureCurrentVersion(Lecture lecture, Integer actorId) {
        LectureVersion current = findVersionQuietly(lecture.getCurrentVersionId());
        if (current != null) {
            return current;
        }
        int nextNumber = Math.max(1, lecture.getCurrentVersionNumber());
        LectureVersion version = new LectureVersion();
        version.setLecture(lecture);
        version.setVersionNumber(nextNumber);
        version.setTitle(lecture.getTitle());
        version.setContent(lecture.getOriginalSource());
        version.setStatus(LectureVersionStatus.DRAFT);
        version.setCreatedBy(actorId);
        version = lectureVersionRepository.save(version);
        lecture.setCurrentVersionId(version.getLectureVersionId());
        lecture.setCurrentVersionNumber(nextNumber);
        return version;
    }

    private LectureVersion copyAsNewDraft(
            Lecture lecture,
            LectureVersion source,
            Integer actorId) {
        LectureVersion draft = new LectureVersion();
        draft.setLecture(lecture);
        draft.setVersionNumber(Math.max(lecture.getCurrentVersionNumber(), source.getVersionNumber()) + 1);
        draft.setTitle(source.getTitle());
        draft.setContent(source.getContent());
        draft.setSlideContent(source.getSlideContent());
        draft.setSourceAssetId(source.getSourceAssetId());
        draft.setAiGenerated(source.isAiGenerated());
        draft.setStatus(LectureVersionStatus.DRAFT);
        draft.setCreatedBy(actorId);
        return lectureVersionRepository.save(draft);
    }

    private LectureResponse toResponse(
            Lecture lecture,
            LectureVersion version,
            Integer requesterId,
            boolean admin) {
        LectureResponse response = LectureResponse.from(lecture);
        if (version != null) {
            response.setTitle(version.getTitle());
            response.setOriginalSource(version.getContent());
        }
        boolean editor = admin || isOwner(lecture, requesterId)
                || lectureCollaboratorRepository
                        .existsByLecture_LectureIdAndCollaboratorUserId(
                                lecture.getLectureId(), requesterId);
        response.setCanEdit(editor && lecture.getStatus() != LectureStatus.ARCHIVED);
        response.setCanPublish((admin || isOwner(lecture, requesterId))
                && lecture.getStatus() != LectureStatus.ARCHIVED);
        response.setCanArchive((admin || isOwner(lecture, requesterId))
                && lecture.getStatus() != LectureStatus.ARCHIVED);
        return response;
    }

    private void validatePublishable(Lecture lecture, LectureVersion version) {
        if (!StringUtils.hasText(version.getTitle())) {
            throw new BadRequestException("A lecture title is required before publishing");
        }
        if (!StringUtils.hasText(version.getContent())) {
            throw new BadRequestException("Lecture content is required before publishing");
        }
        if (lecture.getAccessScope() == null) {
            throw new BadRequestException("An access scope is required before publishing");
        }
    }

    private void assertStudentGrant(Lecture lecture, Integer studentId) {
        if (lecture.getStatus() != LectureStatus.PUBLISHED
                || lecture.getAccessScope() != LectureAccessScope.CLASS
                || lecture.getPublishedVersionId() == null
                || !accessGrantVerifier.canReadPublishedLecture(
                        studentId,
                        lecture.getLectureId(),
                        lecture.getPublishedVersionId())) {
            throw new AccessDeniedException("The lecture is not assigned to an active enrollment");
        }
    }

    private void assertCanReadOrEdit(Lecture lecture, Integer requesterId, boolean admin) {
        if (admin || isOwner(lecture, requesterId)
                || lectureCollaboratorRepository
                        .existsByLecture_LectureIdAndCollaboratorUserId(
                                lecture.getLectureId(), requesterId)) {
            return;
        }
        throw new AccessDeniedException("You cannot access this lecture");
    }

    private void assertOwnerOrAdmin(Lecture lecture, Integer requesterId, boolean admin) {
        if (!admin && !isOwner(lecture, requesterId)) {
            throw new AccessDeniedException("Only the lecture owner can perform this action");
        }
    }

    private boolean isOwner(Lecture lecture, Integer requesterId) {
        return Objects.equals(lecture.getTeacher().getUserId(), requesterId);
    }

    private void assertNotArchived(Lecture lecture) {
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new ConflictException("Archived lectures are read-only");
        }
    }

    private Lecture findLectureOrThrow(Long lectureId) {
        return lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecture not found: " + lectureId));
    }

    private LectureVersion requireVersion(UUID versionId) {
        if (versionId == null) {
            throw new ResourceNotFoundException("Lecture version is not available");
        }
        return lectureVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecture version not found: " + versionId));
    }

    private LectureVersion findVersionQuietly(UUID versionId) {
        return versionId == null ? null : lectureVersionRepository.findById(versionId).orElse(null);
    }

    private boolean hasRole(UserPrincipal principal, String role) {
        return principal != null && principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String writeSlides(List<LectureCreateRequest.SlideDto> slides) {
        if (slides == null || slides.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(slides);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Slides could not be serialized");
        }
    }

    private void saveLegacyQuizzes(
            Lecture lecture,
            List<LectureCreateRequest.QuizDto> quizzes) {
        if (quizzes == null || quizzes.isEmpty()) {
            return;
        }
        for (int index = 0; index < quizzes.size(); index++) {
            LectureCreateRequest.QuizDto quiz = quizzes.get(index);
            AiElement element = new AiElement();
            element.setLecture(lecture);
            element.setQuestionText(requireText(quiz.getQuestionText(), "quiz.questionText"));
            element.setOptions(writeQuizOptions(quiz.getOptions()));
            element.setCorrectAnswer(normalizeCorrectAnswer(
                    quiz.getCorrectAnswer(), quiz.getOptions()));
            element.setOrderIndex(index);
            aiElementRepository.save(element);
        }
    }

    private String writeQuizOptions(List<String> options) {
        if (options == null || options.size() < 2) {
            throw new BadRequestException("A quiz needs at least two options");
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Quiz options could not be serialized");
        }
    }

    private String normalizeCorrectAnswer(String answer, List<String> options) {
        String normalized = requireText(answer, "quiz.correctAnswer")
                .toUpperCase(Locale.ROOT);
        int optionIndex = normalized.charAt(0) - 'A';
        if (normalized.length() != 1 || optionIndex < 0
                || options == null || optionIndex >= options.size()) {
            throw new BadRequestException("Quiz correctAnswer must reference an existing option");
        }
        return normalized;
    }
}
