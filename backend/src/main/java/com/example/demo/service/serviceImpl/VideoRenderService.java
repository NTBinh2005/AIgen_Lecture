package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.VideoStatus;
import com.example.demo.repository.LectureRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoRenderService {
    private final LectureRepository lectureRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${video.service.url:http://localhost:3001}")
    private String videoServiceUrl;

    @Value("${app.video.enabled:false}")
    private boolean videoEnabled;

    @Transactional
    public void requestRender(Long lectureId, String slideContent) {
        if (!videoEnabled) {
            return;
        }
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecture not found: " + lectureId));
        try {
            List<Map<String, Object>> slides = objectMapper.readValue(
                    slideContent, new TypeReference<>() {});
            if (slides == null || slides.isEmpty()) {
                markFailed(lecture, "Lecture has no slides to render");
                return;
            }
            GenerateVideoResponse response = restTemplate.postForObject(
                    endpoint("/generate-video"),
                    new GenerateVideoRequest(lectureId.toString(), slides),
                    GenerateVideoResponse.class);
            if (response == null || !StringUtils.hasText(response.jobId())) {
                markFailed(lecture, "Video service returned no jobId");
                return;
            }
            lecture.setVideoJobId(response.jobId());
            lecture.setVideoStatus(VideoStatus.PROCESSING);
            lecture.setVideoUrl(null);
            lecture.setVideoErrorMessage(null);
            lectureRepository.save(lecture);
        } catch (Exception exception) {
            markFailed(lecture, safeMessage(exception));
        }
    }

    @Scheduled(fixedDelayString = "${app.video.poll-interval-ms:15000}")
    @Transactional
    public void pollActiveRenders() {
        if (!videoEnabled) {
            return;
        }
        List<Lecture> lectures = lectureRepository.findByVideoStatusIn(
                List.of(VideoStatus.PROCESSING));
        for (Lecture lecture : lectures) {
            pollOne(lecture);
        }
    }

    private void pollOne(Lecture lecture) {
        try {
            ResponseEntity<VideoJobStatusResponse> response = restTemplate.getForEntity(
                    endpoint("/video-status/" + lecture.getVideoJobId()),
                    VideoJobStatusResponse.class);
            VideoJobStatusResponse body = response.getBody();
            if (body == null || !StringUtils.hasText(body.status())) {
                return;
            }
            switch (body.status().toLowerCase(Locale.ROOT)) {
                case "done" -> {
                    if (!StringUtils.hasText(body.videoUrl())) {
                        markFailed(lecture, "Video service completed without a videoUrl");
                        return;
                    }
                    lecture.setVideoStatus(VideoStatus.DONE);
                    lecture.setVideoUrl(body.videoUrl());
                    lecture.setVideoErrorMessage(null);
                    lectureRepository.save(lecture);
                }
                case "failed" -> markFailed(lecture,
                        StringUtils.hasText(body.error()) ? body.error() : "Video render failed");
                case "pending", "processing" -> {
                    // Keep polling.
                }
                default -> log.warn("Unknown video status '{}' for job {}",
                        body.status(), lecture.getVideoJobId());
            }
        } catch (HttpClientErrorException.NotFound exception) {
            markFailed(lecture, "Video render job no longer exists");
        } catch (RestClientException exception) {
            log.warn("Could not poll video job {}: {}",
                    lecture.getVideoJobId(), exception.getMessage());
        }
    }

    private void markFailed(Lecture lecture, String message) {
        lecture.setVideoStatus(VideoStatus.FAILED);
        lecture.setVideoErrorMessage(truncate(message, 1000));
        lectureRepository.save(lecture);
    }

    private String endpoint(String path) {
        return videoServiceUrl.replaceAll("/+$", "") + path;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message)
                ? "Could not start video render: " + message
                : "Could not start video render";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record GenerateVideoRequest(
            String lectureId,
            List<Map<String, Object>> slides) {
    }

    private record GenerateVideoResponse(String jobId, String status, String message) {
    }

    private record VideoJobStatusResponse(
            String jobId,
            String lectureId,
            String status,
            String videoUrl,
            String error) {
    }
}
