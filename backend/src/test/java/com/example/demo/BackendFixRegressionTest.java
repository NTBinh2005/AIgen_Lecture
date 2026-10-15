package com.example.demo;

import com.example.demo.common.security.*;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.dto.request.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import com.example.demo.service.serviceImpl.ExportServiceImpl;
import com.example.demo.service.serviceImpl.LiveSessionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:backend-fix-regression;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
    "app.subscriptions.enforce-premium=true", "app.video.enabled=false", "spring.jpa.show-sql=false",
    "spring.config.import=", "gemini.api.key=", "google.oauth.client-id="
})
@AutoConfigureMockMvc
class BackendFixRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider jwt;
    @Autowired SubscriptionPlanRepository plans;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired SubscriptionCreditRepository credits;
    @Autowired SubscriptionService subscriptionService;
    @Autowired PremiumAccessService premium;
    @Autowired LearningAccessService access;
    @Autowired PaymentRepository payments;
    @Autowired InvoiceRepository invoices;
    @Autowired InvoiceService invoiceService;
    @Autowired ClassRepository classes;
    @Autowired ClassService classService;
    @Autowired ClassStudentRepository enrollments;
    @Autowired QuizRepository quizzes;
    @Autowired QuizVersionRepository versions;
    @Autowired QuizAssignmentRepository assignments;
    @Autowired QuizAssignmentService assignmentService;
    @Autowired AttemptRepository attempts;
    @Autowired AttemptAnswerRepository answers;
    @Autowired NotificationRepository notifications;
    @Autowired ExportServiceImpl exports;
    @Autowired LiveSessionServiceImpl live;
    @Autowired LiveSessionRepository sessions;
    @Autowired LectureService lectures;
    @Autowired StatisticsService statistics;
    @Autowired StudyReminderScheduler reminders;
    @Autowired NotificationStreamService streams;
    @Autowired UserService userService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    User student, teacher, admin, otherTeacher;
    @BeforeEach void setup() {
        student = user(UserRole.STUDENT); teacher = user(UserRole.TEACHER);
        admin = user(UserRole.ADMIN); otherTeacher = user(UserRole.TEACHER);
    }

    User user(UserRole role) {
        User user = new User(); user.setName("Nguyễn Ánh " + role);
        user.setEmail(UUID.randomUUID() + "@example.com"); user.setRole(role);
        user.setPasswordHash(passwords.encode("Password123!")); user.setStatus(UserStatus.ACTIVE);
        return users.save(user);
    }
    SubscriptionPlan plan(UserRole role, int days) {
        SubscriptionPlan plan = new SubscriptionPlan(); plan.setCode("TEST_" + UUID.randomUUID().toString().replace("-", ""));
        plan.setName("Original plan"); plan.setPrice(new BigDecimal("150000"));
        plan.setBillingCycle(days == 365 ? BillingCycle.YEARLY : BillingCycle.MONTHLY);
        plan.setDurationDays(days); plan.setTargetRole(role); return plans.save(plan);
    }
    Payment buy(User user, SubscriptionPlan plan) {
        Payment payment = new Payment(); payment.setUser(user); payment.setPlan(plan);
        payment.setPlanSnapshotJson(subscriptionService.snapshotPlan(plan)); payment.setProductId(plan.getPlanId());
        payment.setTransactionId(UUID.randomUUID().toString()); payment.setAmount(plan.getPrice());
        payment.setPaymentMethod(PaymentMethod.VNPAY); payment.setStatus(PaymentStatus.SUCCESS);
        payment = payments.save(payment); subscriptionService.activateFromPayment(payment); return payment;
    }
    ClassEntity classroom() {
        ClassEntity classroom = new ClassEntity(); classroom.setTeacher(teacher);
        classroom.setClassName("Regression " + UUID.randomUUID()); classroom.setClassCode(UUID.randomUUID().toString().substring(0, 12));
        classroom.setStatus(ClassStatus.ACTIVE); classroom.setSemester("HK2"); classroom.setMaxStudents(20);
        classroom.setStartsAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1)); classroom.setEndsAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(10));
        return classes.save(classroom);
    }
    QuizAssignment assignment(ClassEntity classroom) {
        Quiz quiz = new Quiz(); quiz.setOwnerTeacher(teacher); quiz.setTitle("Real quiz");
        quiz.setSourceType(SourceType.MANUAL); quiz.setStatus(QuizStatus.PUBLISHED); quiz = quizzes.save(quiz);
        QuizVersion version = new QuizVersion(); version.setQuiz(quiz); version.setVersionNo(1);
        version.setPublishedBy(teacher); version.setQuestionsSnapshot("[]"); version = versions.save(version);
        QuizAssignmentCreateRequest request = new QuizAssignmentCreateRequest(); request.setClassId(classroom.getClassId());
        request.setQuizVersionId(version.getVersionId()); request.setCloseAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(2));
        request.setMaxAttempts(3); request.setDurationMinutes(30); request.setResultPolicy(ResultPolicy.values()[0]);
        Long id = assignmentService.createAssignment(teacher.getUserId(), request).getAssignmentId();
        return assignments.findById(id).orElseThrow();
    }

    @Test void wrongPasswordsFromOnePeerCannotLockAnotherPeerAndLogoutRevokesAccess() throws Exception {
        String wrong = mapper.writeValueAsString(java.util.Map.of("email", student.getEmail(), "password", "WrongPassword"));
        String correct = mapper.writeValueAsString(java.util.Map.of("email", student.getEmail(), "password", "Password123!"));
        for (int i = 0; i < 5; i++) mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.1"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(wrong)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.1"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(correct)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "198.51.100.2")
                .with(r -> { r.setRemoteAddr("198.51.100.1"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(correct)).andExpect(status().isUnauthorized());
        String response = mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.2"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(correct)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tokens = mapper.readTree(response);
        assertThat(tokens.path("token").asText()).isNotBlank();
        mvc.perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + tokens.path("token").asText()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", tokens.path("refreshToken").asText())))).andExpect(status().isNoContent());
        mvc.perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + tokens.path("token").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("token", tokens.path("token").asText())))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void requiresActiveRoleSpecificPlansForStudentsAndTeachers() {
        assertThatThrownBy(() -> premium.requireActivePlan(student.getUserId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> premium.requireActivePlan(teacher.getUserId())).isInstanceOf(AccessDeniedException.class);
        premium.requireActivePlan(admin.getUserId());
        SubscriptionPlan studentPlan = plan(UserRole.STUDENT, 30);
        buy(student, studentPlan); buy(teacher, plan(UserRole.TEACHER, 30));
        studentPlan.setTargetRole(UserRole.TEACHER); plans.save(studentPlan);
        premium.requireActivePlan(student.getUserId()); premium.requireActivePlan(teacher.getUserId());
        Subscription active = subscriptions.findByUserUserIdAndStatus(student.getUserId(), SubscriptionStatus.ACTIVE).getFirst();
        active.setExpiresAt(Instant.now().minusSeconds(1)); subscriptions.save(active);
        assertThatThrownBy(() -> premium.requireActivePlan(student.getUserId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test void premiumEndpointsReturnStableSubscriptionRequiredError() throws Exception {
        String token = jwt.generateToken(new UserPrincipal(student));
        mvc.perform(get("/api/lectures/student").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"))
                .andExpect(jsonPath("$.message").value(
                        "Bạn cần gói đăng ký còn hiệu lực để sử dụng tính năng này"));
    }

    @Test void directErrorEndpointIsNotReportedAsInternalServerFailure() throws Exception {
        mvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy endpoint"));
    }

    @Test void repeatedPurchasesRefundAndUpgradePreserveHistoryAndEntitlements() {
        SubscriptionPlan month = plan(UserRole.STUDENT, 30);
        Payment first = buy(student, month); Payment second = buy(student, month);
        var accumulated = subscriptionService.getMySubscriptions(student.getUserId()).getFirst();
        assertThat(Duration.between(Instant.now(), accumulated.expiresAt()).toDays()).isBetween(59L, 60L);
        month.setPrice(new BigDecimal("99000")); month.setName("Changed name"); plans.save(month);
        assertThat(subscriptionService.getMySubscriptions(student.getUserId()).getFirst().plan().price()).isEqualByComparingTo("150000");
        assertThat(subscriptionService.getMySubscriptions(student.getUserId()).getFirst().plan().name()).isEqualTo("Original plan");
        subscriptionService.revokePayment(first);
        var refunded = subscriptionService.getMySubscriptions(student.getUserId()).getFirst();
        assertThat(Duration.between(refunded.expiresAt(), accumulated.expiresAt()).toDays()).isEqualTo(30);
        subscriptionService.revokePayment(first);
        assertThat(subscriptionService.getMySubscriptions(student.getUserId()).getFirst().expiresAt()).isEqualTo(refunded.expiresAt());
        SubscriptionPlan year = plan(UserRole.STUDENT, 365); Payment upgrade = buy(student, year);
        var active = subscriptionService.getMySubscriptions(student.getUserId()).stream().filter(s -> s.status() == SubscriptionStatus.ACTIVE).findFirst().orElseThrow();
        assertThat(Duration.between(Instant.now(), active.expiresAt()).toDays()).isGreaterThanOrEqualTo(394);
        assertThat(access.hasAccess(student.getUserId(), month.getPlanId())).isFalse();
        assertThat(access.hasAccess(student.getUserId(), year.getPlanId())).isTrue();
        subscriptionService.activateFromPayment(upgrade);
        assertThat(subscriptionService.getMySubscriptions(student.getUserId()).stream().filter(s -> s.status() == SubscriptionStatus.ACTIVE).count()).isEqualTo(1);
        subscriptionService.revokePayment(second);
        var afterRefund = subscriptionService.getMySubscriptions(student.getUserId()).stream().filter(s -> s.status() == SubscriptionStatus.ACTIVE).findFirst().orElseThrow();
        assertThat(Duration.between(afterRefund.expiresAt(), active.expiresAt()).toDays()).isEqualTo(30);
        assertThat(credits.findById(second.getPaymentId()).orElseThrow().isRefunded()).isTrue();
    }

    @Test void adminCanGrantExtendCancelAndInspectInvoicesButOtherUsersCannot() {
        var granted = subscriptionService.grant(admin.getUserId(), new AdminSubscriptionGrantRequest(student.getUserId(), plan(UserRole.STUDENT, 30).getPlanId(), 7));
        premium.requireActivePlan(student.getUserId());
        var extended = subscriptionService.extend(admin.getUserId(), granted.subscriptionId(), 10);
        assertThat(Duration.between(granted.expiresAt(), extended.expiresAt()).toMillis()).isCloseTo(864000000L, within(1L));
        assertThatThrownBy(() -> subscriptionService.extend(student.getUserId(), granted.subscriptionId(), 10)).isInstanceOf(AccessDeniedException.class);
        subscriptionService.cancelImmediately(admin.getUserId(), granted.subscriptionId());
        assertThatThrownBy(() -> premium.requireActivePlan(student.getUserId())).isInstanceOf(AccessDeniedException.class);
        Payment payment = buy(student, plan(UserRole.STUDENT, 30));
        Invoice invoice = new Invoice(); invoice.setInvoiceNumber(UUID.randomUUID().toString()); invoice.setUser(student);
        invoice.setPayment(payment); invoice.setAmount(payment.getAmount()); invoice.setStatus(InvoiceStatus.ISSUED); invoice.setIssuedAt(Instant.now());
        invoice = invoices.save(invoice); Integer id = invoice.getId();
        assertThat(invoiceService.getMyInvoices(admin.getUserId(), org.springframework.data.domain.Pageable.unpaged()).getContent())
                .extracting(i -> i.id()).contains(id);
        assertThat(invoiceService.getInvoiceById(admin.getUserId(), id).userId()).isEqualTo(student.getUserId());
        assertThatThrownBy(() -> invoiceService.getInvoiceById(teacher.getUserId(), id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void classPatchClearsOptionalValuesAndLiveSupportsStartingNowAndExpiresScheduledSessions() throws Exception {
        ClassEntity classroom = classroom();
        ClassUpdateRequest patch = mapper.readValue("{\"semester\":null,\"endsAt\":null,\"maxStudents\":null}", ClassUpdateRequest.class);
        classService.update(classroom.getClassId(), patch, teacher.getUserId());
        ClassEntity updated = classes.findById(classroom.getClassId()).orElseThrow();
        assertThat(updated.getSemester()).isNull(); assertThat(updated.getEndsAt()).isNull(); assertThat(updated.getMaxStudents()).isNull();
        var created = live.create(new LiveSessionCreateRequest(classroom.getClassId(), "Starting now", LiveSessionType.ONLINE,
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(5), LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(30), null, "https://example.com/meeting", null), teacher.getUserId());
        LiveSession session = sessions.findById(created.sessionId()).orElseThrow();
        session.setEndsAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1)); sessions.save(session);
        live.endExpiredSessions(); assertThat(sessions.findById(session.getSessionId()).orElseThrow().getStatus()).isEqualTo(LiveSessionStatus.ENDED);
    }

    @Test void assignmentAuthorizationAndRealExportsIncludeStudentScoresAndAnswers() throws Exception {
        ClassEntity classroom = classroom();
        ClassStudent enrollment = new ClassStudent(); enrollment.setId(new ClassStudentId(classroom.getClassId(), student.getUserId()));
        enrollment.setClassEntity(classroom); enrollment.setStudent(student); enrollment.setStatus(EnrollmentStatus.ACTIVE); enrollments.save(enrollment);
        QuizAssignment assignment = assignment(classroom);
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(student.getUserId())).extracting(Notification::getType).contains(NotificationType.QUIZ_ASSIGNED);
        QuizAssignmentUpdateRequest patch = new QuizAssignmentUpdateRequest(); patch.setCloseAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(3));
        assertThatThrownBy(() -> assignmentService.updateAssignment(otherTeacher.getUserId(), assignment.getAssignmentId(), patch)).isInstanceOf(AccessDeniedException.class);
        assignmentService.updateAssignment(teacher.getUserId(), assignment.getAssignmentId(), patch);
        Attempt attempt = new Attempt(); attempt.setAssignment(assignment); attempt.setStudent(student); attempt.setQuizVersion(assignment.getQuizVersion());
        attempt.setAttemptNo(1); attempt.setStatus(AttemptStatus.GRADED); attempt.setFinalScore(8.5); attempt.setObjectiveScore(5.0);
        attempt.setSubmittedAt(LocalDateTime.now(java.time.ZoneOffset.UTC)); attempt = attempts.save(attempt);
        AttemptAnswer answer = new AttemptAnswer(); answer.setAttempt(attempt); answer.setQuestionId(12L); answer.setResponse("=HYPERLINK(\"bad\")");
        answer.setPointsAwarded(3.5); answers.save(answer);
        assertThatThrownBy(() -> assignmentService.deleteAssignment(teacher.getUserId(), assignment.getAssignmentId())).isInstanceOf(ConflictException.class);
        ExportRequest request = new ExportRequest(); request.setAssignmentId(assignment.getAssignmentId()); request.setType(ExportType.GRADEBOOK_XLSX);
        var job = exports.requestExport(teacher.getUserId(), request); exports.processExportJob(job.getJobId());
        assertThat(exports.getJobStatus(teacher.getUserId(), job.getJobId()).getStatus()).isEqualTo(ExportStatus.COMPLETED);
        try (var input = exports.downloadFile(teacher.getUserId(), job.getJobId()).getInputStream(); var workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getSheet("Results").getRow(1).getCell(1).getStringCellValue()).isEqualTo(student.getName());
            assertThat(workbook.getSheet("Results").getRow(1).getCell(10).getNumericCellValue()).isEqualTo(8.5);
            assertThat(workbook.getSheet("Answers").getRow(1).getCell(3).getStringCellValue()).startsWith("'=");
        }
        request.setType(ExportType.STUDENT_RESULT_PDF); var pdfJob = exports.requestExport(teacher.getUserId(), request); exports.processExportJob(pdfJob.getJobId());
        try (var input = exports.downloadFile(teacher.getUserId(), pdfJob.getJobId()).getInputStream(); var document = PDDocument.load(input)) {
            assertThat(new PDFTextStripper().getText(document)).contains(student.getName(), "8.5", "HYPERLINK");
        }
    }

    @Test void emptyOrOversizedTabSignalsAreRejectedBeforeUpdatingAttempts() throws Exception {
        String token = jwt.generateToken(new UserPrincipal(student));
        mvc.perform(post("/api/attempts/999999/signals").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/attempts/999999/signals").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("type", "A".repeat(5000)))))
                .andExpect(status().isBadRequest());
    }

    @Test void loginThrottleSurvivesServiceRecreationAndScopesAttemptsByPeerAddress() {
        LoginThrottleService firstInstance = new LoginThrottleService(jdbc);
        String key = firstInstance.key(student.getEmail(), "198.51.100.3");
        for (int i = 0; i < 5; i++) firstInstance.failed(key);
        LoginThrottleService secondInstance = new LoginThrottleService(jdbc);
        assertThatThrownBy(() -> secondInstance.assertAllowed(key))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        secondInstance.assertAllowed(secondInstance.key(student.getEmail(), "198.51.100.4"));
        secondInstance.succeeded(key); firstInstance.assertAllowed(key);
    }

    @Test void restoresLectureAsNewDraftAndKeepsPublishedSnapshotImmutable() {
        LectureCreateRequest request = new LectureCreateRequest(); request.setTitle("Original title"); request.setOriginalSource("Original source");
        var created = lectures.createLecture(teacher.getUserId(), request);
        var published = lectures.publishLecture(created.getLectureId(), created.getCurrentVersionId(), teacher.getUserId(), false);
        LectureUpdateRequest change = new LectureUpdateRequest(); change.setTitle("Changed title"); change.setContent("Changed source");
        lectures.updateLecture(created.getLectureId(), teacher.getUserId(), false, change);
        var restored = lectures.restoreVersion(created.getLectureId(), published.getPublishedVersionId(), teacher.getUserId(), false);
        assertThat(restored.getTitle()).isEqualTo("Original title");
        assertThat(restored.getCurrentVersionNumber()).isEqualTo(3);
        assertThat(restored.getPublishedVersionId()).isEqualTo(published.getPublishedVersionId());
        assertThat(restored.isHasUnpublishedChanges()).isTrue();
        assertThatThrownBy(() -> lectures.restoreVersion(created.getLectureId(), published.getPublishedVersionId(), otherTeacher.getUserId(), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void usersArePaginatedAndAmbiguousLegacyEmailsRequireAdminResolution() throws Exception {
        String adminToken = jwt.generateToken(new UserPrincipal(admin));
        mvc.perform(get("/api/users").param("size", "2").param("page", "0").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
        mvc.perform(get("/api/users/students").param("email", "s").header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(teacher))))
                .andExpect(status().isBadRequest());
        User duplicate = user(UserRole.STUDENT); duplicate.setEmail(student.getEmail().toUpperCase()); duplicate.setEmailAmbiguous(true); users.save(duplicate);
        student.setEmailAmbiguous(true); users.save(student);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", student.getEmail(), "password", "Password123!"))))
                .andExpect(status().isUnauthorized());
        userService.update(duplicate.getUserId(), new UserUpdateRequest(null, null, UUID.randomUUID() + "@example.com", null, null), admin.getUserId());
        userService.update(student.getUserId(), new UserUpdateRequest(null, null, student.getEmail(), null, null), admin.getUserId());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", student.getEmail(), "password", "Password123!"))))
                .andExpect(status().isOk());
    }

    @Test void dueRemindersAreDeduplicatedAndSkipStudentsWhoSubmitted() {
        ClassEntity classroom = classroom();
        User submittedStudent = user(UserRole.STUDENT);
        for (User user : java.util.List.of(student, submittedStudent)) {
            ClassStudent member = new ClassStudent(); member.setId(new ClassStudentId(classroom.getClassId(), user.getUserId()));
            member.setClassEntity(classroom); member.setStudent(user); member.setStatus(EnrollmentStatus.ACTIVE); enrollments.save(member);
        }
        QuizAssignment assignment = assignment(classroom);
        assignment.setCloseAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(1)); assignments.save(assignment);
        Attempt submitted = new Attempt(); submitted.setAssignment(assignment); submitted.setStudent(submittedStudent);
        submitted.setQuizVersion(assignment.getQuizVersion()); submitted.setAttemptNo(1); submitted.setStatus(AttemptStatus.GRADED); attempts.save(submitted);
        reminders.remind(); reminders.remind();
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(student.getUserId()).stream().filter(n -> n.getType() == NotificationType.QUIZ_DUE).count()).isEqualTo(1);
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(submittedStudent.getUserId()).stream().filter(n -> n.getType() == NotificationType.QUIZ_DUE).count()).isZero();
    }

    @Test void notificationStreamRequiresAuthenticationAndUsesCurrentUserChannel() throws Exception {
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
        var response = mvc.perform(get("/api/notifications/stream").header("Authorization", "Bearer " + jwt.generateToken(new UserPrincipal(student))))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        streams.afterCommit(teacher.getUserId(), java.util.Map.of("message", "Other user"));
        streams.afterCommit(student.getUserId(), java.util.Map.of("message", "My notice"));
        assertThat(response.getResponse().getContentAsString()).contains("event:ready", "My notice").doesNotContain("Other user");
        streams.closeUser(student.getUserId());
    }

    @Test void statisticsUseDatabaseCountsAndExposeUnknownBillingCostHonestly() {
        assertThat(statistics.getOverview().totalUsers()).isEqualTo(users.count());
        assertThat(statistics.getOverview().llmCostUsd()).isNull();
        assertThat(statistics.getOverview().serverUptime()).startsWith("PT");
        var charts = statistics.getCharts();
        assertThat(charts.userGrowth()).hasSize(12); assertThat(charts.lectureGrowth()).hasSize(12); assertThat(charts.weeklyInteractions()).hasSize(7);
        assertThat(charts.userGrowth().getLast().students()).isGreaterThanOrEqualTo(1);
    }
    @Test void paymentConfigurationHasNoDuplicateKeysOrDemoSigningSecrets() throws Exception {
        var lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of("src/main/resources/application.properties"));
        var keys = lines.stream().map(String::trim).filter(line -> !line.startsWith("#") && line.contains("="))
                .map(line -> line.substring(0, line.indexOf('='))).toList();
        assertThat(keys).doesNotHaveDuplicates();
        String config = String.join("\n", lines);
        assertThat(config).contains("vnpay.hash-secret=${VNPAY_HASH_SECRET:}", "momo.secret-key=${MOMO_SECRET_KEY:}");
        assertThat(config).doesNotContain("MOMOTEST", "DEMO0001", "vnpay-secret-key-for-dev-testing-only");
    }

    @Test void liveJoinTokensCannotAuthenticateApiRequests() throws Exception {
        javax.crypto.SecretKey key = (javax.crypto.SecretKey) org.springframework.test.util.ReflectionTestUtils.getField(jwt, "signingKey");
        String joinToken = io.jsonwebtoken.Jwts.builder().subject(student.getUserId().toString()).claim("type", "JOIN_TOKEN")
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        assertThat(jwt.validateToken(joinToken)).isFalse();
        mvc.perform(get("/api/subscriptions/me").header("Authorization", "Bearer " + joinToken)).andExpect(status().isUnauthorized());
    }

}
