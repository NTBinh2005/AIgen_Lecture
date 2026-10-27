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
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
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
    private final com.example.demo.service.StudyNotificationService studyNotifications;
    private final Map<Long, Double> progressByLecture = new ConcurrentHashMap<>();

    @Value("${video.service.url:http://localhost:3001}")
    private String videoServiceUrl;

    @Value("${app.lecture.job-timeout-minutes:120}")
    private long jobTimeoutMinutes;

    @Value("${app.video.enabled:false}")
    private boolean videoEnabled;

    /**
     * Also recovers lectures left in PENDING after an application restart or a
     * temporary video-service outage.
     */
    @Scheduled(
            initialDelayString = "${app.lecture.video-dispatch-initial-delay-ms:1000}",
            fixedDelayString = "${app.lecture.video-dispatch-interval-ms:3000}")
    @Transactional
    public void dispatchPendingLectures() {
        if (!videoEnabled) {
            return;
        }
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
        if (!videoEnabled) {
            return;
        }
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

    private String resolveVideoServiceUrl() {
        if (!StringUtils.hasText(videoServiceUrl)) {
            return null;
        }
        String clean = videoServiceUrl.trim();
        if (clean.startsWith("[") && clean.contains("]")) {
            int openParen = clean.indexOf("](");
            int closeParen = clean.lastIndexOf(")");
            if (openParen >= 0 && closeParen > openParen) {
                clean = clean.substring(openParen + 2, closeParen).trim();
            } else {
                int closeBracket = clean.indexOf("]");
                clean = clean.substring(1, closeBracket).trim();
            }
        }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://" + clean;
        }
        return clean.replaceAll("/+$", "");
    }

    private void dispatch(Lecture lecture) {
        String baseUrl = resolveVideoServiceUrl();
        if (!StringUtils.hasText(baseUrl)) {
            fail(lecture, "Chưa cấu hình VIDEO_SERVICE_URL hợp lệ (cần bắt đầu bằng http:// hoặc https://)");
            return;
        }

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
                    baseUrl + "/generate-video", request, String.class);
            JsonNode json = objectMapper.readTree(response);
            String jobId = json.path("jobId").asText();
            if (!StringUtils.hasText(jobId)) {
                throw new IllegalStateException("video-service returned no jobId");
            }

            lecture.setVideoJobId(jobId);
            lecture.setVideoStatus(VideoStatus.PROCESSING);
            lecture.setVideoErrorMessage(null);
            progressByLecture.put(lecture.getLectureId(), 0.03);
            lectureRepository.save(lecture);
            log.info("Lecture {} submitted to video-service as job {}", lecture.getLectureId(), jobId);
        } catch (HttpStatusCodeException exception) {
            int code = exception.getStatusCode().value();
            if ((code == 502 || code == 503 || code == 504)
                    && lecture.getCreatedAt() != null
                    && lecture.getCreatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2))) {
                log.warn("video-service đang khởi động trên Cloud (HTTP {}), sẽ tự động thử lại sau ít giây...", code);
                return;
            }
            fail(lecture, "video-service phản hồi lỗi HTTP " + code + " (" + exception.getStatusText() + "). Vui lòng thử lại sau.");
        } catch (org.springframework.web.client.ResourceAccessException exception) {
            if (lecture.getCreatedAt() != null
                    && lecture.getCreatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2))) {
                log.warn("Chưa thể kết nối tới video-service (có thể đang khởi động), sẽ tự động thử lại...");
                return;
            }
            fail(lecture, "Không thể kết nối đến video-service tại " + baseUrl + " (dịch vụ chưa sẵn sàng hoặc không thể truy cập)");
        } catch (Exception exception) {
            fail(lecture, "Could not submit render job: " + exception.getMessage());
        }
    }

    private void poll(Lecture lecture) {
        String baseUrl = resolveVideoServiceUrl();
        if (!StringUtils.hasText(baseUrl)) {
            fail(lecture, "Chưa cấu hình VIDEO_SERVICE_URL hợp lệ");
            return;
        }

        try {
            String response = restTemplate.getForObject(
                    baseUrl + "/video-status/" + lecture.getVideoJobId(), String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("progress") && json.path("progress").isNumber()) {
                progressByLecture.put(lecture.getLectureId(), json.path("progress").asDouble());
            }
            switch (json.path("status").asText()) {
                case "done" -> {
                    String videoUrl = json.path("videoUrl").asText();
                    if (!StringUtils.hasText(videoUrl)) {
                        fail(lecture, "video-service completed without a video URL");
                        return;
                    }
                    lecture.setVideoUrl(videoUrl);
                    if (lecture.getTeacher() != null)
                        studyNotifications.notify(lecture.getTeacher().getUserId(), "Video đã sẵn sàng", "Video bài giảng đã render xong.",
                                com.example.demo.entity.NotificationType.VIDEO_COMPLETED, "LECTURE", lecture.getLectureId() + "/jobs/" + lecture.getVideoJobId());
                    lecture.setVideoStatus(VideoStatus.DONE);
                    lecture.setVideoErrorMessage(null);
                    progressByLecture.put(lecture.getLectureId(), 1.0);
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
        if (lecture.getTeacher() != null)
            studyNotifications.notify(lecture.getTeacher().getUserId(), "Render video thất bại", "Không thể render video. Hãy xem chi tiết lỗi trong bài giảng.",
                    com.example.demo.entity.NotificationType.VIDEO_FAILED, "LECTURE", lecture.getLectureId() + "/jobs/" + lecture.getVideoJobId());
        lecture.setVideoStatus(VideoStatus.FAILED);
        lecture.setVideoErrorMessage(reason);
        progressByLecture.remove(lecture.getLectureId());
        lectureRepository.save(lecture);
        log.error("Lecture {} video failed: {}", lecture.getLectureId(), reason);
    }

    public Double getProgress(Long lectureId) {
        return progressByLecture.get(lectureId);
    }
}
