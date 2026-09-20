package com.example.demo.backend3;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.request.GenerationJobCreateRequest;
import com.example.demo.dto.request.GenerationJobFailureRequest;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.request.LectureUpdateRequest;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.request.PresentationUpdateRequest;
import com.example.demo.dto.response.AssetResponse;
import com.example.demo.dto.response.GenerationJobResponse;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.dto.response.PresentationExportResponse;
import com.example.demo.dto.response.PresentationResponse;
import com.example.demo.entity.AssetPurpose;
import com.example.demo.entity.AuthProvider;
import com.example.demo.entity.JobStatus;
import com.example.demo.entity.JobType;
import com.example.demo.entity.LectureAccessScope;
import com.example.demo.entity.LectureStatus;
import com.example.demo.entity.PresentationSourceType;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.UserStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AssetService;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.LectureService;
import com.example.demo.service.PresentationExportContent;
import com.example.demo.service.PresentationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend3-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "gemini.api.key=test-placeholder",
        "google.oauth.client-id=test-placeholder"
})
@Transactional
class Backend3ServiceIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private LectureService lectureService;
    @Autowired private AssetService assetService;
    @Autowired private GenerationJobService generationJobService;
    @Autowired private PresentationService presentationService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void editingPublishedLectureCreatesNewDraftVersionWithoutReplacingPublishedVersion() {
        User teacher = createTeacher();
        LectureCreateRequest create = new LectureCreateRequest();
        create.setTitle("Data structures");
        create.setOriginalSource("Version one content");
        create.setAccessScope(LectureAccessScope.CLASS);

        LectureResponse draft = lectureService.createLecture(teacher.getUserId(), create);
        LectureResponse published = lectureService.publishLecture(
                draft.getLectureId(), null, teacher.getUserId(), false);
        UUID publishedVersionId = published.getPublishedVersionId();

        LectureUpdateRequest update = new LectureUpdateRequest();
        update.setTitle("Data structures - revised");
        update.setContent("Version two content");
        LectureResponse revised = lectureService.updateLecture(
                draft.getLectureId(), teacher.getUserId(), false, update);

        assertThat(revised.getStatus()).isEqualTo(LectureStatus.PUBLISHED);
        assertThat(revised.getPublishedVersionId()).isEqualTo(publishedVersionId);
        assertThat(revised.getCurrentVersionId()).isNotEqualTo(publishedVersionId);
        assertThat(revised.getCurrentVersionNumber()).isEqualTo(2);
        assertThat(lectureService.getVersions(
                draft.getLectureId(), teacher.getUserId(), false)).hasSize(2);
    }

    @Test
    void generationJobIsIdempotentAndRetryPreservesFailedAttempt() {
        String key = "job-" + UUID.randomUUID();
        GenerationJobCreateRequest request = new GenerationJobCreateRequest(
                JobType.LECTURE_GENERATION,
                42,
                "ASSET",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                objectMapper.createObjectNode().put("language", "vi"),
                "LECTURE",
                "100",
                key);

        GenerationJobResponse first = generationJobService.createOrGet(request);
        GenerationJobResponse duplicate = generationJobService.createOrGet(request);
        assertThat(duplicate.jobId()).isEqualTo(first.jobId());

        generationJobService.start(first.jobId(), first.currentAttemptId(), "PROCESSING");
        generationJobService.fail(
                first.jobId(),
                first.currentAttemptId(),
                new GenerationJobFailureRequest("SAFE_FAILURE", "Generation failed safely"));
        GenerationJobResponse retried = generationJobService.retry(first.jobId(), 42, false);

        assertThat(retried.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(retried.attemptCount()).isEqualTo(2);
        assertThat(retried.attempts()).hasSize(2);
        assertThat(retried.attempts().get(0).status()).isEqualTo(JobStatus.FAILED);
        assertThat(retried.attempts().get(1).status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void assetUploadStoresValidatedImmutableBytes() {
        byte[] pdf = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", pdf);

        AssetResponse uploaded = assetService.upload(
                77, AssetPurpose.LECTURE_SOURCE, file, null, null);

        assertThat(uploaded.sha256()).hasSize(64);
        assertThat(assetService.getContentForProcessing(uploaded.assetId(), 77).getBytes())
                .containsExactly(pdf);
    }

    @Test
    void presentationExportRemainsPinnedToSelectedVersion() {
        PresentationResponse generated;
        var presentation = presentationService.createGenerating(
                55,
                "Algorithms",
                PresentationSourceType.ASSET,
                null,
                UUID.randomUUID(),
                "standard",
                objectMapper.createObjectNode());
        generated = presentationService.completeGeneration(
                presentation.getPresentationId(),
                55,
                "Algorithms",
                List.of(slide("Introduction", "First content", 0)));
        UUID exportedVersion = generated.currentVersionId();
        PresentationExportResponse firstExport = presentationService.export(
                presentation.getPresentationId(), exportedVersion, 55, false);

        presentationService.update(
                presentation.getPresentationId(),
                new PresentationUpdateRequest(
                        "Algorithms revised",
                        List.of(slide("Revised", "Second content", 0))),
                55,
                false);
        PresentationExportResponse repeatedExport = presentationService.export(
                presentation.getPresentationId(), exportedVersion, 55, false);
        PresentationExportContent content = presentationService.downloadExport(
                presentation.getPresentationId(), firstExport.exportId(), 55, false);

        assertThat(repeatedExport.exportId()).isEqualTo(firstExport.exportId());
        assertThat(content.getBytes()).startsWith((byte) 'P', (byte) 'K');
    }

    private PresentationSlideRequest slide(String title, String content, int order) {
        return new PresentationSlideRequest(
                title,
                List.of(content),
                "Speaker notes",
                "TITLE_AND_CONTENT",
                order);
    }

    private User createTeacher() {
        User teacher = new User();
        teacher.setRole(UserRole.TEACHER);
        teacher.setName("Backend 3 Teacher");
        teacher.setEmail("backend3-" + UUID.randomUUID() + "@example.test");
        teacher.setPasswordHash("not-used-in-test");
        teacher.setStatus(UserStatus.ACTIVE);
        teacher.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.saveAndFlush(teacher);
    }
}
