package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.VideoStatus;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.LectureVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/** Submits lecture render jobs and synchronizes their status with video-service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LectureVideoRenderCoordinator {

    private static final TypeReference<List<LectureCreateRequest.SlideDto>> SLIDE_LIST_TYPE =
            new TypeReference<>() {};

    private final LectureRepository lectureRepository;
    private final LectureVersionRepository lectureVersionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${video.service.url:http://localhost:3001}")
    private String videoServiceUrl;

    @Value("${app.lecture.job-timeout-minutes:20}")
    private long jobTimeoutMinutes;

    /**
     * Also recovers lectures left in PENDING after an application restart or a
     * temporary video-service outage.
     */
    @Scheduled(
            initialDelayString = "${app.lecture.video-dispatch-initial-delay-ms:1000}",
            fixedDelayString = "${app.lecture.video-dispatch-interval-ms:3000}")
    @Transactional
    public void dispatchPendingLectures() {
        List<Lecture> pending = lectureRepository
                .findTop10ByVideoStatusAndVideoJobIdIsNullOrderByCreatedAtAsc(VideoStatus.PENDING);
        for (Lecture lecture : pending) {
            dispatch(lecture);
        }
    }

    @Scheduled(
            initialDelayString = "${app.lecture.video-poll-initial-delay-ms:3000}",
            fixedDelayString = "${app.lecture.video-poll-interval-ms:3000}")
    @Transactional
    public void pollProcessingLectures() {
        List<Lecture> processing = lectureRepository.findByVideoStatusIn(List.of(VideoStatus.PROCESSING));
        LocalDateTime timeoutThreshold = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(jobTimeoutMinutes);

        for (Lecture lecture : processing) {
            if (lecture.getUpdatedAt() != null && lecture.getUpdatedAt().isBefore(timeoutThreshold)) {
                fail(lecture, "Video rendering exceeded " + jobTimeoutMinutes + " minutes");
                continue;
            }
            poll(lecture);
        }
    }

    private void dispatch(Lecture lecture) {
        try {
            LectureVersion version = lectureVersionRepository.findById(lecture.getCurrentVersionId())
                    .orElseThrow(() -> new IllegalStateException("Current lecture version is missing"));
            List<LectureCreateRequest.SlideDto> slides = readSlides(version.getSlideContent());
            if (slides.isEmpty()) {
                throw new IllegalStateException("Lecture has no slides to render");
            }

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("lectureId", lecture.getLectureId().toString());
            request.put("slides", slides);

            String response = restTemplate.postForObject(
                    videoServiceUrl + "/generate-video", request, String.class);
            JsonNode json = objectMapper.readTree(response);
            String jobId = json.path("jobId").asText();
            if (!StringUtils.hasText(jobId)) {
                throw new IllegalStateException("video-service returned no jobId");
            }

            lecture.setVideoJobId(jobId);
            lecture.setVideoStatus(VideoStatus.PROCESSING);
            lectureRepository.save(lecture);
            log.info("Lecture {} submitted to video-service as job {}", lecture.getLectureId(), jobId);
        } catch (Exception exception) {
            fail(lecture, "Could not submit render job: " + exception.getMessage());
        }
    }

    private void poll(Lecture lecture) {
        try {
            String response = restTemplate.getForObject(
                    videoServiceUrl + "/video-status/" + lecture.getVideoJobId(), String.class);
            JsonNode json = objectMapper.readTree(response);
            switch (json.path("status").asText()) {
                case "done" -> {
                    String videoUrl = json.path("videoUrl").asText();
                    if (!StringUtils.hasText(videoUrl)) {
                        fail(lecture, "video-service completed without a video URL");
                        return;
                    }
                    lecture.setVideoUrl(videoUrl);
                    lecture.setVideoStatus(VideoStatus.DONE);
                    lectureRepository.save(lecture);
                    log.info("Lecture {} video is ready at {}", lecture.getLectureId(), videoUrl);
                }
                case "failed" -> fail(lecture, json.path("error").asText("Video rendering failed"));
                case "pending", "processing" -> {
                    // The next scheduled poll will continue tracking the job.
                }
                default -> log.warn("Unknown video status for lecture {}: {}",
                        lecture.getLectureId(), json.path("status").asText());
            }
        } catch (HttpClientErrorException.NotFound exception) {
            fail(lecture, "Render job no longer exists in video-service");
        } catch (Exception exception) {
            log.warn("Could not poll video job {} for lecture {}: {}",
                    lecture.getVideoJobId(), lecture.getLectureId(), exception.getMessage());
        }
    }

    private List<LectureCreateRequest.SlideDto> readSlides(String slideContent) throws Exception {
        if (!StringUtils.hasText(slideContent)) {
            return List.of();
        }
        return objectMapper.readValue(slideContent, SLIDE_LIST_TYPE);
    }

    private void fail(Lecture lecture, String reason) {
        lecture.setVideoStatus(VideoStatus.FAILED);
        lectureRepository.save(lecture);
        log.error("Lecture {} video failed: {}", lecture.getLectureId(), reason);
    }
}
