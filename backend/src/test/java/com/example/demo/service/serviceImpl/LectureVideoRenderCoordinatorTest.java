package com.example.demo.service.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.entity.Lecture;
import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.VideoStatus;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.LectureVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LectureVideoRenderCoordinatorTest {

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private LectureVersionRepository lectureVersionRepository;

    @Mock
    private RestTemplate restTemplate;

    private LectureVideoRenderCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new LectureVideoRenderCoordinator(
                lectureRepository, lectureVersionRepository, restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(coordinator, "videoServiceUrl", "http://localhost:3001");
        ReflectionTestUtils.setField(coordinator, "jobTimeoutMinutes", 20L);
    }

    @Test
    void dispatchPendingLectureStoresJobIdAndStartsProcessing() {
        UUID versionId = UUID.randomUUID();
        Lecture lecture = lecture(7L, VideoStatus.PENDING);
        lecture.setCurrentVersionId(versionId);

        LectureVersion version = new LectureVersion();
        version.setSlideContent("""
                [{"title":"Introduction","bulletPoints":["First point"],"narrationText":"Hello"}]
                """);

        when(lectureRepository.findTop10ByVideoStatusAndVideoJobIdIsNullOrderByCreatedAtAsc(
                VideoStatus.PENDING)).thenReturn(List.of(lecture));
        when(lectureVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(restTemplate.postForObject(
                eq("http://localhost:3001/generate-video"), any(), eq(String.class)))
                .thenReturn("{\"jobId\":\"job-123\",\"status\":\"pending\"}");

        coordinator.dispatchPendingLectures();

        assertThat(lecture.getVideoStatus()).isEqualTo(VideoStatus.PROCESSING);
        assertThat(lecture.getVideoJobId()).isEqualTo("job-123");
        verify(lectureRepository).save(lecture);
    }

    @Test
    void pollCompletedJobStoresVideoUrl() {
        Lecture lecture = lecture(7L, VideoStatus.PROCESSING);
        lecture.setVideoJobId("job-123");
        when(lectureRepository.findByVideoStatusIn(List.of(VideoStatus.PROCESSING)))
                .thenReturn(List.of(lecture));
        when(restTemplate.getForObject(
                "http://localhost:3001/video-status/job-123", String.class))
                .thenReturn("""
                        {"jobId":"job-123","status":"done","videoUrl":"http://localhost:3001/videos/7.mp4"}
                        """);

        coordinator.pollProcessingLectures();

        assertThat(lecture.getVideoStatus()).isEqualTo(VideoStatus.DONE);
        assertThat(lecture.getVideoUrl()).isEqualTo("http://localhost:3001/videos/7.mp4");
        verify(lectureRepository).save(lecture);
    }

    @Test
    void pollFailedJobStoresErrorMessage() {
        Lecture lecture = lecture(8L, VideoStatus.PROCESSING);
        lecture.setVideoJobId("job-failed");
        when(lectureRepository.findByVideoStatusIn(List.of(VideoStatus.PROCESSING)))
                .thenReturn(List.of(lecture));
        when(restTemplate.getForObject(
                "http://localhost:3001/video-status/job-failed", String.class))
                .thenReturn("""
                        {"jobId":"job-failed","status":"failed","error":"SadTalker is unavailable"}
                        """);

        coordinator.pollProcessingLectures();

        assertThat(lecture.getVideoStatus()).isEqualTo(VideoStatus.FAILED);
        assertThat(lecture.getVideoErrorMessage()).isEqualTo("SadTalker is unavailable");
        verify(lectureRepository).save(lecture);
    }

    private Lecture lecture(Long id, VideoStatus status) {
        Lecture lecture = new Lecture();
        lecture.setLectureId(id);
        lecture.setVideoStatus(status);
        lecture.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return lecture;
    }
}
