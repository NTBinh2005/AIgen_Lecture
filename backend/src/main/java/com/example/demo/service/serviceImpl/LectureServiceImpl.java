package com.example.demo.service.serviceImpl;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.entity.AiElement;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.User;
import com.example.demo.entity.VideoStatus;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.repository.AiElementRepository;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.common.security.UserPrincipal;
import com.example.demo.service.LectureService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service xử lý logic Lecture.
 *
 * Flow chính (theo pivot):
 *   1. Teacher tạo lecture → lưu vào DB với videoStatus = PENDING
 *   2. Gọi POST /generate-video sang video-service → nhận jobId → lưu vào DB, set PROCESSING
 *   3. @Scheduled job poll GET /video-status/:jobId mỗi 15 giây
 *   4. Khi video-service trả về "done" → update videoUrl, set DONE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LectureServiceImpl implements LectureService {
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;
    private final AiElementRepository aiElementRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${video.service.url:http://localhost:3001}")
    private String videoServiceUrl;

    // ─── CRUD ────────────────────────────────────────────────────────────────────

    /**
     * Tạo mới một Lecture và trigger video generation bất đồng bộ.
     * Không block request — trả về ngay sau khi lưu DB.
     */
    @Transactional
    public LectureResponse createLecture(Integer teacherId, LectureCreateRequest request) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher không tồn tại: " + teacherId));

        Lecture lecture = new Lecture();
        lecture.setTeacher(teacher);
        lecture.setTitle(request.getTitle());
        lecture.setOriginalSource(request.getOriginalSource());
        lecture.setVideoStatus(VideoStatus.PENDING);

        Lecture saved = lectureRepository.save(lecture);

        // Lưu câu hỏi trắc nghiệm vào ai_elements (nếu có)
        if (request.getQuizzes() != null && !request.getQuizzes().isEmpty()) {
            for (int i = 0; i < request.getQuizzes().size(); i++) {
                LectureCreateRequest.QuizDto quizDto = request.getQuizzes().get(i);
                AiElement element = new AiElement();
                element.setLecture(saved);
                element.setQuestionText(quizDto.getQuestionText());
                element.setCorrectAnswer(quizDto.getCorrectAnswer().toUpperCase());
                element.setOrderIndex(i);
                try {
                    element.setOptions(objectMapper.writeValueAsString(quizDto.getOptions()));
                } catch (JsonProcessingException e) {
                    element.setOptions("[]");
                }
                aiElementRepository.save(element);
            }
        }

        // Trigger video render bất đồng bộ (không chờ kết quả)
        triggerVideoGeneration(saved.getLectureId(), request);

        return LectureResponse.from(saved);
    }

    /**
     * Lấy danh sách lecture của teacher (có phân trang, lọc theo title).
     * Hỗ trợ Search/Sort/Paging/Filtering theo chuẩn (task 23).
     */
    @Transactional(readOnly = true)
    public Page<LectureResponse> getLecturesByTeacher(Integer teacherId,
                                                      String titleKeyword,
                                                      Pageable pageable) {
        Page<Lecture> lectures;
        if (titleKeyword != null && !titleKeyword.isBlank()) {
            lectures = lectureRepository.findByTeacher_UserIdAndTitleContainingIgnoreCase(
                    teacherId, titleKeyword, pageable);
        } else {
            lectures = lectureRepository.findByTeacher_UserId(teacherId, pageable);
        }
        return lectures.map(LectureResponse::from);
    }

    /**
     * Lấy danh sách tất cả bài giảng cho Student (có phân trang, lọc theo title).
     */
    @Transactional(readOnly = true)
    public Page<LectureResponse> getAllLecturesForStudent(String titleKeyword, Pageable pageable) {
        Page<Lecture> lectures;
        if (titleKeyword != null && !titleKeyword.isBlank()) {
            lectures = lectureRepository.findByTitleContainingIgnoreCase(titleKeyword, pageable);
        } else {
            lectures = lectureRepository.findAll(pageable);
        }
        return lectures.map(LectureResponse::from);
    }

    /**
     * Lấy chi tiết một lecture.
     * - Teacher: kiểm tra ownership theo BR-03
     * - Student/Admin: có thể xem bất kỳ lecture nào
     */
    @Transactional(readOnly = true)
    public LectureResponse getLecture(Long lectureId, Integer requesterId, UserPrincipal principal) {
        Lecture lecture = findLectureOrThrow(lectureId);
        // Chỉ kiểm tra ownership nếu là TEACHER (BR-03)
        boolean isTeacher = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
        if (isTeacher) {
            checkOwnership(lecture, requesterId);
        }
        return LectureResponse.from(lecture);
    }

    /**
     * Cập nhật tiêu đề bài giảng.
     * Kiểm tra ownership theo BR-03.
     */
    @Transactional
    public LectureResponse updateLectureTitle(Long lectureId, Integer requesterId, String newTitle) {
        Lecture lecture = findLectureOrThrow(lectureId);
        checkOwnership(lecture, requesterId);
        lecture.setTitle(newTitle);
        Lecture saved = lectureRepository.save(lecture);
        return LectureResponse.from(saved);
    }

    /**
     * Xóa mềm (soft-delete) một lecture — @SQLDelete trong entity tự handle.
     * Kiểm tra ownership theo BR-03, BR-08.
     */
    @Transactional
    public void deleteLecture(Long lectureId, Integer requesterId) {
        Lecture lecture = findLectureOrThrow(lectureId);
        checkOwnership(lecture, requesterId);
        lectureRepository.delete(lecture); // triggers @SQLDelete
    }

    /**
     * Lấy trạng thái video của một lecture.
     */
    @Transactional(readOnly = true)
    public Lecture getVideoStatus(Long lectureId) {
        return findLectureOrThrow(lectureId);
    }

    // ─── VIDEO SERVICE INTEGRATION ────────────────────────────────────────────

    /**
     * Gọi video-service để bắt đầu render video (bất đồng bộ).
     * Chạy trong @Async thread để không block request thread.
     */
    @Async
    public void triggerVideoGeneration(Long lectureId, LectureCreateRequest request) {
        try {
            // Build request body cho video-service
            ObjectNode body = objectMapper.createObjectNode();
            body.put("lectureId", lectureId.toString());

            ArrayNode slidesArray = objectMapper.createArrayNode();
            for (LectureCreateRequest.SlideDto slide : request.getSlides()) {
                ObjectNode slideNode = objectMapper.createObjectNode();
                slideNode.put("title", slide.getTitle());
                slideNode.put("narrationText", slide.getNarrationText());
                if (slide.getImagePrompt() != null) {
                    slideNode.put("imagePrompt", slide.getImagePrompt());
                }
                ArrayNode bullets = objectMapper.createArrayNode();
                slide.getBulletPoints().forEach(bullets::add);
                slideNode.set("bulletPoints", bullets);
                slidesArray.add(slideNode);
            }
            body.set("slides", slidesArray);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> httpEntity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            String response = restTemplate.postForObject(
                    videoServiceUrl + "/generate-video", httpEntity, String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            String jobId = responseJson.get("jobId").asText();

            // Cập nhật lecture với jobId và đổi status sang PROCESSING
            lectureRepository.findById(lectureId).ifPresent(lecture -> {
                lecture.setVideoJobId(jobId);
                lecture.setVideoStatus(VideoStatus.PROCESSING);
                lectureRepository.save(lecture);
            });

            log.info("Lecture {} → video-service jobId: {}", lectureId, jobId);

        } catch (Exception e) {
            log.error("Không thể gọi video-service cho lecture {}: {}", lectureId, e.getMessage());
            // Đánh dấu FAILED nếu không gọi được video-service
            lectureRepository.findById(lectureId).ifPresent(lecture -> {
                lecture.setVideoStatus(VideoStatus.FAILED);
                lectureRepository.save(lecture);
            });
        }
    }

    @Value("${app.lecture.job-timeout-minutes:20}")
    private long jobTimeoutMinutes;

    /**
     * Scheduled job: poll video-service mỗi 15 giây để cập nhật trạng thái
     * cho các lecture đang PROCESSING.
     *
     * Không dùng BR-05 timeout 15s (áp dụng cho LLM call), video render
     * có thể mất vài phút — đây là cơ chế async riêng theo pivot spec.
     */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void pollVideoServiceStatus() {
        List<Lecture> processingLectures = lectureRepository.findByVideoStatusIn(
                List.of(VideoStatus.PROCESSING));

        if (processingLectures.isEmpty()) return;

        log.debug("Polling video-service cho {} lectures đang PROCESSING", processingLectures.size());

        java.time.LocalDateTime timeoutThreshold = java.time.LocalDateTime.now().minusMinutes(jobTimeoutMinutes);

        for (Lecture lecture : processingLectures) {
            // Check for timeout
            if (lecture.getCreatedAt() != null && lecture.getCreatedAt().isBefore(timeoutThreshold)) {
                lecture.setVideoStatus(VideoStatus.FAILED);
                lectureRepository.save(lecture);
                log.warn("Lecture {} → video FAILED. Reason: Video rendering timed out or processing container restarted", lecture.getLectureId());
                continue;
            }

            try {
                String url = videoServiceUrl + "/video-status/" + lecture.getVideoJobId();
                String response = restTemplate.getForObject(url, String.class);
                JsonNode json = objectMapper.readTree(response);
                String status = json.get("status").asText();

                switch (status) {
                    case "done" -> {
                        lecture.setVideoStatus(VideoStatus.DONE);
                        lecture.setVideoUrl(json.get("videoUrl").asText());
                        lectureRepository.save(lecture);
                        log.info("Lecture {} → video DONE: {}", lecture.getLectureId(), lecture.getVideoUrl());
                    }
                    case "failed" -> {
                        lecture.setVideoStatus(VideoStatus.FAILED);
                        lectureRepository.save(lecture);
                        log.warn("Lecture {} → video FAILED", lecture.getLectureId());
                    }
                    default -> log.debug("Lecture {} vẫn đang {}", lecture.getLectureId(), status);
                }
            } catch (Exception e) {
                log.error("Lỗi poll status cho lecture {}: {}", lecture.getLectureId(), e.getMessage());
            }
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    private Lecture findLectureOrThrow(Long lectureId) {
        return lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecture không tồn tại: " + lectureId));
    }

    /**
     * Kiểm tra ownership theo BR-03: Teacher chỉ được sửa/xóa lecture của chính mình.
     */
    private void checkOwnership(Lecture lecture, Integer requesterId) {
        if (!lecture.getTeacher().getUserId().equals(requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền thao tác với bài giảng này");
        }
    }
}
