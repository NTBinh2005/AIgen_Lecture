package com.example.demo;

import com.example.demo.common.security.JwtTokenProvider;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.*;
import com.example.demo.entity.*;
import com.example.demo.event.EssayScoreListener;
import com.example.demo.event.EssayScoreRequestedEvent;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_AUDIT_DB_URL:jdbc:h2:mem:backend-audit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${TEST_AUDIT_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${TEST_AUDIT_DB_USER:sa}", "spring.datasource.password=${TEST_AUDIT_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.config.import=", "app.video.enabled=false", "gemini.api.key=", "google.oauth.client-id=",
        "app.subscriptions.enforce-premium=false", "brevo.api-key="
})
@AutoConfigureMockMvc
class BackendAuditRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired ClassRepository classes;
    @Autowired ClassStudentRepository enrollments;
    @Autowired QuizRepository quizzes;
    @Autowired QuizVersionRepository versions;
    @Autowired QuizAssignmentRepository assignments;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptAnswerRepository answers;
    @Autowired AttemptService attemptService;
    @Autowired GradingService grading;
    @Autowired EnrollmentService enrollmentService;
    @Autowired AuthService auth;
    @Autowired RefreshTokenService refresh;
    @Autowired SettingsService settings;
    @Autowired UserService userService;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired LectureRepository lectures;
    @Autowired CommentRepository comments;
    @Autowired LiveSessionService live;
    @Autowired com.example.demo.service.serviceImpl.ExportServiceImpl exports;
    @Autowired AttendanceService attendance;
    @Autowired SessionAttendanceRepository attendanceRows;
    @Autowired NotificationStreamService streams;
    @org.springframework.test.context.bean.override.mockito.MockitoBean SmsService sms;
    User student, teacher, admin;
    ClassEntity classroom;
    QuizAssignment assignment;

    @BeforeEach void setup() {
        student = user(UserRole.STUDENT); teacher = user(UserRole.TEACHER); admin = user(UserRole.ADMIN);
        classroom = new ClassEntity(); classroom.setTeacher(teacher); classroom.setClassName("Audit " + UUID.randomUUID());
        classroom.setClassCode(UUID.randomUUID().toString().substring(0, 12)); classroom.setStatus(ClassStatus.ACTIVE);
        classroom.setMaxStudents(20); classroom = classes.save(classroom); enroll(student);
        Quiz quiz = new Quiz(); quiz.setOwnerTeacher(teacher); quiz.setTitle("Audit quiz");
        quiz.setSourceType(SourceType.MANUAL); quiz.setStatus(QuizStatus.PUBLISHED); quiz = quizzes.save(quiz);
        QuizVersion version = new QuizVersion(); version.setQuiz(quiz); version.setPublishedBy(teacher); version.setVersionNo(1);
        version.setQuestionsSnapshot("[{\"questionId\":101,\"questionType\":\"MCQ_SINGLE\",\"questionText\":\"Test\",\"options\":[\"A\",\"B\"],\"correctAnswer\":\"A\",\"points\":2}]");
        version = versions.save(version);
        assignment = new QuizAssignment(); assignment.setClassEntity(classroom); assignment.setQuizVersion(version);
        assignment.setDurationMinutes(30); assignment.setMaxAttempts(1); assignment.setResultPolicy(ResultPolicy.NEVER);
        assignment.setCloseAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)); assignment = assignments.save(assignment);
    }

    @ParameterizedTest @EnumSource(ResultPolicy.class)
    void studentHistoryHonoursResultPolicyButTeacherProgressShowsScores(ResultPolicy policy) throws Exception {
        assignment.setResultPolicy(policy); assignments.save(assignment);
        Attempt attempt = savedAttempt(AttemptStatus.REVIEW_REQUIRED); attempt.setObjectiveScore(2.0); attempt.setFinalScore(2.0); attempts.save(attempt);
        var history = attemptService.getStudentQuizHistory(student.getUserId(), PageRequest.of(0, 20)).getContent().getFirst();
        if (policy == ResultPolicy.AFTER_SUBMISSION) assertThat(history.finalScore()).isEqualTo(2.0);
        else { assertThat(history.finalScore()).isNull(); assertThat(history.objectiveScore()).isNull(); }
        assertThat(attemptService.toSummary(attempt).finalScore()).isEqualTo(2.0);
        if (policy == ResultPolicy.NEVER) {
            mvc.perform(get("/api/students/me/quiz-history").header("Authorization", bearer(student)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].finalScore").isEmpty());
        }
    }

    @Test void newAttemptDeadlineNeverExceedsAssignmentCloseAndUnknownAnswersAreRejected() {
        var started = attemptService.startAttempt(student.getUserId(), assignment.getAssignmentId());
        assertThat(started.getDeadlineAt()).isEqualTo(assignments.findById(assignment.getAssignmentId()).orElseThrow().getCloseAt());
        AttemptAnswerSubmitRequest request = new AttemptAnswerSubmitRequest(); request.setQuestionId(999L); request.setResponse("A");
        assertThatThrownBy(() -> attemptService.submitAnswer(student.getUserId(), started.getAttemptId(), request))
                .hasMessageContaining("không thuộc lượt làm");
        assertThat(answers.findByAttempt_AttemptId(started.getAttemptId())).isEmpty();
    }

    @Test void closedAssignmentCannotAcceptAutosavesEvenForLegacyUnboundedAttempt() {
        Attempt attempt = savedAttempt(AttemptStatus.IN_PROGRESS);
        assignment.setStatus(AssignmentStatus.CLOSED); assignments.save(assignment);
        AttemptAnswerSubmitRequest request = new AttemptAnswerSubmitRequest(); request.setQuestionId(101L); request.setResponse("A");
        assertThatThrownBy(() -> attemptService.submitAnswer(student.getUserId(), attempt.getAttemptId(), request))
                .hasMessageContaining("đã đóng");
    }

    @Test void teacherCanDeleteOwnedDraftButNotAnotherTeachersOrPublishedQuiz() throws Exception {
        Quiz draft = new Quiz(); draft.setOwnerTeacher(teacher); draft.setTitle("Draft to delete " + UUID.randomUUID());
        draft.setSourceType(SourceType.MANUAL); draft.setStatus(QuizStatus.DRAFT); draft = quizzes.save(draft);

        User otherTeacher = user(UserRole.TEACHER);
        mvc.perform(delete("/api/quizzes/{id}", draft.getQuizId()).header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/quizzes/{id}", draft.getQuizId()).header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/quizzes/{id}", draft.getQuizId()).header("Authorization", bearer(teacher)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/quizzes/{id}", draft.getQuizId()).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());

        Long publishedQuizId = assignment.getQuizVersion().getQuiz().getQuizId();
        mvc.perform(delete("/api/quizzes/{id}", publishedQuizId).header("Authorization", bearer(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ có thể xóa quiz nháp. Hãy lưu trữ quiz đã xuất bản hoặc đã đóng."));
        assertThat(quizzes.findById(publishedQuizId)).isPresent();
    }

    @Test void ownerCanDeleteEmptyClosedClassButCannotDeleteAnotherTeachersClass() throws Exception {
        ClassEntity empty = new ClassEntity();
        empty.setTeacher(teacher);
        empty.setClassName("Empty class " + UUID.randomUUID());
        empty.setClassCode(UUID.randomUUID().toString().substring(0, 12));
        empty.setStatus(ClassStatus.CLOSED);
        empty = classes.save(empty);

        User otherTeacher = user(UserRole.TEACHER);
        mvc.perform(delete("/api/classes/{id}/permanent", empty.getClassId())
                        .header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/classes/{id}/permanent", empty.getClassId())
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/classes/{id}", empty.getClassId())
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isNotFound());
    }

    @Test void classDeletionRejectsActiveOrClassesWithLinkedHistory() throws Exception {
        mvc.perform(delete("/api/classes/{id}/permanent", classroom.getClassId())
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hãy đóng lớp trước khi xóa."));

        classroom.setStatus(ClassStatus.CLOSED);
        classes.save(classroom);
        mvc.perform(delete("/api/classes/{id}/permanent", classroom.getClassId())
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không thể xóa lớp đã có dữ liệu. Hãy lưu trữ lớp để giữ lịch sử học tập."));
        assertThat(classes.findById(classroom.getClassId())).isPresent();
    }

    @Test void simultaneousStartsReturnSameAttemptInsteadOfViolatingUniqueConstraint() throws Exception {
        var results = concurrent(() -> attemptService.startAttempt(student.getUserId(), assignment.getAssignmentId()).getAttemptId());
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(attempts.findByAssignment_AssignmentId(assignment.getAssignmentId())).hasSize(1);
    }

    @Test void simultaneousAutosavesDoNotCreateTwoAnswersForOneQuestion() throws Exception {
        Attempt attempt = savedAttempt(AttemptStatus.IN_PROGRESS);
        var results = concurrent(() -> {
            AttemptAnswerSubmitRequest request = new AttemptAnswerSubmitRequest(); request.setQuestionId(101L); request.setResponse("A");
            return attemptService.submitAnswer(student.getUserId(), attempt.getAttemptId(), request).getAnswerId();
        });
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(answers.findByAttempt_AttemptId(attempt.getAttemptId())).hasSize(1);
    }

    @Test void cannotGradeAnUnsubmittedAttemptAndReopeningClearsOldGrades() {
        Attempt attempt = savedAttempt(AttemptStatus.IN_PROGRESS); saveAnswer(attempt);
        assertThatThrownBy(() -> grading.confirmGrade(teacher.getUserId(), attempt.getAttemptId(), Map.of(101L, 1.0)))
                .hasMessageContaining("đã nộp");
        attemptService.submitAttempt(student.getUserId(), attempt.getAttemptId(), SubmitType.MANUAL);
        attemptService.reopenAttempt(teacher.getUserId(), attempt.getAttemptId());
        Attempt reopened = attempts.findById(attempt.getAttemptId()).orElseThrow();
        assertThat(reopened.getFinalScore()).isNull(); assertThat(reopened.getObjectiveScore()).isNull();
        assertThat(answers.findByAttempt_AttemptId(attempt.getAttemptId()).getFirst().getPointsAwarded()).isNull();
    }

    @Test void reopeningIsRejectedWhileAttemptIsActiveOrAssignmentIsUnavailable() {
        Attempt active = savedAttempt(AttemptStatus.IN_PROGRESS);
        assertThatThrownBy(() -> attemptService.reopenAttempt(teacher.getUserId(), active.getAttemptId()))
                .hasMessageContaining("đang diễn ra");

        active.setStatus(AttemptStatus.GRADED);
        active.setFinalScore(2.0);
        active.setObjectiveScore(2.0);
        active.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        attempts.save(active);
        AttemptAnswer answer = saveAnswer(active);
        answer.setPointsAwarded(2.0);
        answers.save(answer);

        assignment.setCloseAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assignments.save(assignment);
        assertThatThrownBy(() -> attemptService.reopenAttempt(teacher.getUserId(), active.getAttemptId()))
                .hasMessageContaining("mở hoặc gia hạn");

        Attempt unchanged = attempts.findById(active.getAttemptId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(unchanged.getFinalScore()).isEqualTo(2.0);
        assertThat(answers.findById(answer.getAnswerId()).orElseThrow().getPointsAwarded()).isEqualTo(2.0);

        assignment.setCloseAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        assignment.setStatus(AssignmentStatus.CLOSED);
        assignments.save(assignment);
        assertThatThrownBy(() -> attemptService.reopenAttempt(teacher.getUserId(), active.getAttemptId()))
                .hasMessageContaining("mở hoặc gia hạn");
    }

    @Test void nanGradesCannotCorruptFinalScoreAndFinalizeStillAcceptsEmptyMap() {
        Attempt attempt = savedAttempt(AttemptStatus.REVIEW_REQUIRED); saveAnswer(attempt);
        assertThatThrownBy(() -> grading.confirmGrade(teacher.getUserId(), attempt.getAttemptId(), Map.of(101L, Double.NaN)))
                .hasMessageContaining("Điểm");
        grading.confirmGrade(teacher.getUserId(), attempt.getAttemptId(), Map.of(101L, 1.0));
        grading.confirmGrade(teacher.getUserId(), attempt.getAttemptId(), Map.of());
        assertThat(attempts.findById(attempt.getAttemptId()).orElseThrow().getFinalScore()).isEqualTo(1.0);
    }

    @Test void delayedAiSuggestionCannotOverwriteTeacherGrade() {
        Attempt attempt = savedAttempt(AttemptStatus.REVIEW_REQUIRED);
        AttemptAnswer answer = saveAnswer(attempt);
        var event = new EssayScoreRequestedEvent(attempt.getAttemptId(), answer.getAnswerId(), answer.getAnswerVersion(), "Essay", "A", null, 2);
        grading.confirmGrade(teacher.getUserId(), attempt.getAttemptId(), Map.of(101L, 1.0));
        LlmService llm = mock(LlmService.class); when(llm.aiSuggestScore(any(), any(), any())).thenReturn(2.0);
        new EssayScoreListener(llm, attempts, answers, transactions).suggest(event);
        AttemptAnswer current = answers.findById(answer.getAnswerId()).orElseThrow();
        assertThat(current.getTeacherFinalScore()).isEqualTo(1.0); assertThat(current.getAiSuggestedScore()).isNull();
        assertThat(attempts.findById(attempt.getAttemptId()).orElseThrow().getFinalScore()).isEqualTo(1.0);
    }

    @Test void concurrentRefreshConsumesOriginalTokenExactlyOnce() throws Exception {
        String original = refresh.createRefreshToken(student);
        var results = concurrent(() -> {
            try { auth.refresh(original); return true; }
            catch (com.example.demo.common.exception.BadRequestException ex) { return false; }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test void passwordChangeRevokesAccessAndRefreshTokens() throws Exception {
        String access = bearer(student); String original = refresh.createRefreshToken(student);
        var stream = mvc.perform(get("/api/notifications/stream").header("Authorization", access))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        settings.changePassword(student.getUserId(), new ChangePasswordRequest("Password123!", "NewPassword456!"));
        assertThat(stream.getAsyncResult(1000)).isNull();
        mvc.perform(get("/api/settings/profile").header("Authorization", access)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(original)).hasMessageContaining("thu hồi");
    }

    @Test void profileEmailCannotDuplicateAnotherUserWithDifferentCase() {
        assertThatThrownBy(() -> settings.updateProfile(student.getUserId(), new UpdateProfileRequest("Changed", teacher.getEmail().toUpperCase(Locale.ROOT))))
                .hasMessageContaining("đã được sử dụng");
    }

    @Test void cancelledEnrollmentCannotBypassCapacityViaStatusPatch() {
        classroom.setMaxStudents(1); classes.save(classroom); User another = user(UserRole.STUDENT);
        ClassStudent old = new ClassStudent(); old.setId(new ClassStudentId(classroom.getClassId(), another.getUserId()));
        old.setClassEntity(classroom); old.setStudent(another); old.setStatus(EnrollmentStatus.CANCELLED); enrollments.save(old);
        assertThatThrownBy(() -> enrollmentService.updateStatus(classroom.getClassId(), another.getUserId(),
                new EnrollmentStatusRequest(EnrollmentStatus.ACTIVE), teacher.getUserId())).hasMessageContaining("capacity");
    }

    @Test void concurrentSelfEnrollmentDoesNotExceedCapacity() throws Exception {
        classroom.setMaxStudents(2); classes.save(classroom);
        User first = user(UserRole.STUDENT), second = user(UserRole.STUDENT);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> enrollAfter(start, first)); var b = pool.submit(() -> enrollAfter(start, second)); start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(enrollments.countByClassEntity_ClassIdAndStatusIn(classroom.getClassId(), List.of(EnrollmentStatus.ACTIVE))).isEqualTo(2);
    }

    @Test void deletingCommentUnderWrongLectureReturns404AndKeepsComment() throws Exception {
        Lecture lecture = new Lecture(); lecture.setTeacher(teacher); lecture.setTitle("Comments"); lecture = lectures.save(lecture);
        Comment comment = new Comment(); comment.setLecture(lecture); comment.setUser(teacher); comment.setContent("Keep"); comment = comments.save(comment);
        mvc.perform(delete("/api/lectures/{lecture}/comments/{comment}", lecture.getLectureId() + 999, comment.getCommentId())
                        .header("Authorization", bearer(teacher))).andExpect(status().isNotFound());
        assertThat(comments.findById(comment.getCommentId()).orElseThrow().getIsDeleted()).isFalse();
    }

    @Test void adminListEndpointsWorkWithNoFiltersOnRealDatabase() throws Exception {
        for (String path : List.of("/api/lectures", "/api/classes", "/api/enrollments", "/api/users", "/api/audit-logs",
                "/api/invoices", "/api/payments/admin", "/api/refunds", "/api/subscriptions", "/api/notifications",
                "/api/schedules", "/api/live-sessions/my", "/api/exports", "/api/plans", "/api/admin/plans")) {
            mvc.perform(get(path).header("Authorization", bearer(admin))).andExpect(status().isOk());
        }
    }

    @Test void invalidSortPropertyReturnsBadRequestInsteadOfInternalServerError() throws Exception {
        mvc.perform(get("/api/lectures")
                        .param("sort", "fieldThatDoesNotExist,asc")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trường sắp xếp không hợp lệ"));
    }

    @Test void otpCanBeConsumedOnlyOnceAndCannotBeResentImmediately() throws Exception {
        String phone = "+849" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        doAnswer(invocation -> { captured.set(invocation.getArgument(1)); return null; }).when(sms).sendOtp(eq(phone), any());
        SmsOtpRequest request = new SmsOtpRequest(); request.setPhoneNumber(phone); auth.requestSmsOtp(request);
        assertThatThrownBy(() -> auth.requestSmsOtp(request)).hasMessageContaining("Vui lòng chờ");
        SmsLoginRequest login = new SmsLoginRequest(); login.setPhoneNumber(phone); login.setOtp(captured.get()); login.setName("OTP test");
        var results = concurrent(() -> {
            try { auth.verifySmsOtp(login); return true; }
            catch (org.springframework.security.authentication.BadCredentialsException ex) { return false; }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThatThrownBy(() -> auth.requestSmsOtp(request)).hasMessageContaining("Vui lòng chờ");
    }

    @Test void concurrentLiveCreationCannotBypassOverlapPolicy() throws Exception {
        var startAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        var request = new LiveSessionCreateRequest(classroom.getClassId(), "Overlap test", LiveSessionType.ONLINE,
                startAt, startAt.plusHours(1), null, "https://example.com/room", null);
        var results = concurrent(() -> {
            try { live.create(request, teacher.getUserId()); return true; }
            catch (com.example.demo.common.exception.BadRequestException ex) { return false; }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test void exportedScoresCannotBeDownloadedAfterTeacherLosesClassAccess() {
        ExportRequest request = new ExportRequest(); request.setClassId(classroom.getClassId()); request.setType(ExportType.GRADEBOOK_XLSX);
        var job = exports.requestExport(teacher.getUserId(), request); exports.processExportJob(job.getJobId());
        assertThat(exports.downloadFile(teacher.getUserId(), job.getJobId()).exists()).isTrue();
        classroom.setTeacher(user(UserRole.TEACHER)); classes.save(classroom);
        assertThatThrownBy(() -> exports.downloadFile(teacher.getUserId(), job.getJobId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void updatingLiveToOfflineRequiresLocation() {
        var startAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        var session = live.create(new LiveSessionCreateRequest(classroom.getClassId(), "Update test", LiveSessionType.ONLINE,
                startAt, startAt.plusHours(1), null, "https://example.com/room", null), teacher.getUserId());
        assertThatThrownBy(() -> live.update(session.sessionId(),
                new LiveSessionUpdateRequest(null, LiveSessionType.OFFLINE, null, null, null, null, null), teacher.getUserId()))
                .hasMessageContaining("Địa điểm");
    }

    @Test void adminPasswordResetAlsoRevokesExistingSessions() throws Exception {
        String access = bearer(student); String original = refresh.createRefreshToken(student);
        userService.update(student.getUserId(), new UserUpdateRequest(null, null, null, "ResetPassword123!", null), admin.getUserId());
        mvc.perform(get("/api/settings/profile").header("Authorization", access)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(original)).hasMessageContaining("thu hồi");
    }

    @Test void concurrentManualAttendanceCannotCreateDuplicates() throws Exception {
        var starts = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        var session = live.create(new LiveSessionCreateRequest(classroom.getClassId(), "Attendance test", LiveSessionType.OFFLINE,
                starts, starts.plusHours(1), "Room 1", null, null), teacher.getUserId());
        live.open(session.sessionId(), teacher.getUserId());
        var results = concurrent(() -> {
            try { attendance.markManual(session.sessionId(), student.getUserId(), teacher.getUserId()); return true; }
            catch (com.example.demo.common.exception.BadRequestException ex) { return false; }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(attendanceRows.findBySession_SessionId(session.sessionId())).hasSize(1);
    }

    @Test void missingSmsConfigurationCannotPretendAnOtpWasSent() {
        var provider = new com.example.demo.service.serviceImpl.SmsServiceImpl("", "", "");
        assertThatThrownBy(() -> provider.sendOtp("+84901234567", "123456"))
                .hasMessageContaining("chưa được cấu hình");
    }

    @Test void missingEmailConfigurationReturns503OnRegistrationInsteadOfPretendingOtpWasSent() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Mail User","email":"nomail@example.com",
                                 "password":"Password123!","role":"STUDENT"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Hệ thống chưa cấu hình gửi email"));

        mvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nomail@example.com\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP đã hết hạn hoặc không tồn tại"));
    }

    @Test void quizAuthoringAcceptsLetterAndAiOptionAnswersAndAdminCanReadQuizVersions() throws Exception {
        String createPayload = """
                {
                  "title": "Quiz kiểm tra #82",
                  "sourceType": "MANUAL",
                  "questions": [
                    {
                      "questionType": "MCQ_SINGLE",
                      "questionText": "Số chẵn là?",
                      "options": ["3", "4", "5", "6"],
                      "correctAnswer": "B",
                      "points": 1.0
                    },
                    {
                      "questionType": "MCQ_SINGLE",
                      "questionText": "Câu hỏi từ AI?",
                      "options": ["A. Đáp án 1", "B. Đáp án 2", "C. Đáp án 3", "D. Đáp án 4"],
                      "correctAnswer": "A",
                      "points": 1.5
                    },
                    {
                      "questionType": "TRUE_FALSE",
                      "questionText": "Trái đất quay quanh mặt trời?",
                      "options": ["Đúng", "Sai"],
                      "correctAnswer": "A",
                      "points": 1.0
                    },
                    {
                      "questionType": "MCQ_MULTI",
                      "questionText": "Chọn các số nguyên tố",
                      "options": ["2", "3", "4", "5"],
                      "correctAnswer": "A,B,D",
                      "points": 2.0
                    }
                  ]
                }
                """;
        String createdJson = mvc.perform(post("/api/quizzes")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        long quizId = mapper.readTree(createdJson).get("quizId").asLong();

        // Update via PUT and PATCH both work with letter answers
        String updatePayload = """
                {
                  "title": "Quiz đã cập nhật",
                  "questions": [
                    {
                      "questionType": "MCQ_SINGLE",
                      "questionText": "3 + 1 = ?",
                      "options": ["3", "4", "5", "6"],
                      "correctAnswer": "B",
                      "points": 2.0
                    }
                  ]
                }
                """;
        mvc.perform(put("/api/quizzes/{id}", quizId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Quiz đã cập nhật"))
                .andExpect(jsonPath("$.questions[0].correctAnswer").value("B"));

        // Invalid letter out of range -> 400
        String invalidSingle = """
                {
                  "title": "Quiz lỗi",
                  "questions": [
                    {
                      "questionType": "MCQ_SINGLE",
                      "questionText": "Chỉ có 2 lựa chọn nhưng chọn C?",
                      "options": ["3", "4"],
                      "correctAnswer": "C",
                      "points": 1.0
                    }
                  ]
                }
                """;
        mvc.perform(patch("/api/quizzes/{id}", quizId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidSingle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đáp án đúng phải là một trong các lựa chọn"));

        String invalidMulti = """
                {
                  "title": "Quiz lỗi multi",
                  "questions": [
                    {
                      "questionType": "MCQ_MULTI",
                      "questionText": "Chọn vượt quá số lựa chọn?",
                      "options": ["A", "B", "C"],
                      "correctAnswer": "A,D",
                      "points": 1.0
                    }
                  ]
                }
                """;
        mvc.perform(patch("/api/quizzes/{id}", quizId)
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMulti))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đáp án đúng phải là một trong các lựa chọn"));

        // Publish and verify #84: ADMIN and owner TEACHER can read versions, other TEACHER gets 403
        mvc.perform(post("/api/quizzes/{id}/publish", quizId)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/quizzes/{id}/versions", quizId)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/quizzes/{id}/versions", quizId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        User otherTeacher = user(UserRole.TEACHER);
        mvc.perform(get("/api/quizzes/{id}/versions", quizId)
                        .header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isForbidden());
    }

    @Test void unrelatedUserUpdateCannotRestoreOldPasswordOrTokenVersion() throws Exception {
        var loaded = new CountDownLatch(1); var passwordChanged = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var update = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(tx -> {
                User earlier = users.findById(student.getUserId()).orElseThrow(); loaded.countDown();
                try { if (!passwordChanged.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                earlier.setGoogleSubject(UUID.randomUUID().toString());
                return null;
            }));
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
            try { settings.changePassword(student.getUserId(), new ChangePasswordRequest("Password123!", "NewPassword456!")); }
            finally { passwordChanged.countDown(); }
            update.get(15, TimeUnit.SECONDS);
        }
        User current = users.findById(student.getUserId()).orElseThrow();
        assertThat(passwords.matches("NewPassword456!", current.getPasswordHash())).isTrue();
        assertThat(current.getTokenVersion()).isEqualTo(1);
    }

    private boolean enrollAfter(CountDownLatch start, User user) throws Exception {
        start.await();
        try { enrollmentService.selfEnroll(classroom.getClassCode(), user.getUserId()); return true; }
        catch (com.example.demo.common.exception.BadRequestException ex) { return false; }
    }
    private <T> List<T> concurrent(Callable<T> action) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        Callable<T> work = () -> { ready.countDown(); start.await(); return action.call(); };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(work); var b = pool.submit(work);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
    private Attempt savedAttempt(AttemptStatus status) {
        Attempt attempt = new Attempt(); attempt.setAssignment(assignment); attempt.setQuizVersion(assignment.getQuizVersion());
        attempt.setStudent(student); attempt.setAttemptNo(1); attempt.setStatus(status);
        attempt.setStartedAt(LocalDateTime.now(ZoneOffset.UTC)); return attempts.save(attempt);
    }
    private AttemptAnswer saveAnswer(Attempt attempt) {
        AttemptAnswer answer = new AttemptAnswer(); answer.setAttempt(attempt); answer.setQuestionId(101L); answer.setResponse("A"); return answers.save(answer);
    }
    private void enroll(User user) {
        ClassStudent enrollment = new ClassStudent(); enrollment.setClassEntity(classroom); enrollment.setStudent(user);
        enrollment.setId(new ClassStudentId(classroom.getClassId(), user.getUserId())); enrollment.setStatus(EnrollmentStatus.ACTIVE); enrollments.save(enrollment);
    }
    private String bearer(User user) { return "Bearer " + jwt.generateToken(new UserPrincipal(user)); }
    private User user(UserRole role) {
        User user = new User(); user.setRole(role); user.setStatus(UserStatus.ACTIVE); user.setName("Audit " + role);
        user.setEmail(UUID.randomUUID() + "@example.com"); user.setPasswordHash(passwords.encode("Password123!")); return users.save(user);
    }
}
