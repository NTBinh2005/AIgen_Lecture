package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.QuizResponse;
import com.example.demo.dto.request.SubmitAnswerRequest;
import com.example.demo.dto.response.SubmitAnswerResponse;
import com.example.demo.entity.AiElement;
import com.example.demo.entity.InteractionLog;
import com.example.demo.entity.User;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.repository.AiElementRepository;
import com.example.demo.repository.InteractionLogRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.InteractionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    private final AiElementRepository aiElementRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Lấy danh sách câu hỏi trắc nghiệm của một lecture.
     *
     * @param lectureId  ID của lecture
     * @param isTeacher  true = trả về correctAnswer, false = ẩn (Student)
     */

    @Transactional(readOnly = true)
    public List<QuizResponse> getQuizzes(Long lectureId, boolean isTeacher) {
        List<AiElement> elements = aiElementRepository
                .findByLecture_LectureIdOrderByOrderIndexAsc(lectureId);

        return elements.stream().map(el -> {
            List<String> options = parseOptions(el.getOptions());
            if (isTeacher) {
                return QuizResponse.forTeacher(
                        el.getElementId(), el.getQuestionText(),
                        options, el.getOrderIndex(), el.getCorrectAnswer());
            } else {
                return QuizResponse.forStudent(
                        el.getElementId(), el.getQuestionText(),
                        options, el.getOrderIndex());
            }
        }).toList();
    }

    /**
     * Học sinh nộp câu trả lời.
     * Backend tự tính isCorrect — không tin client.
     * BR-06: Xác định isFirstAttempt bằng cách query DB.
     */
    @Transactional
    public SubmitAnswerResponse submitAnswer(Integer studentId, SubmitAnswerRequest request) {
        // Lấy câu hỏi — throw 404 nếu không tồn tại
        AiElement element = aiElementRepository.findById(request.getElementId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Câu hỏi không tồn tại: " + request.getElementId()));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Học sinh không tồn tại: " + studentId));

        // Backend tự kiểm tra đúng/sai (case-insensitive)
        boolean isCorrect = element.getCorrectAnswer()
                .equalsIgnoreCase(request.getSubmittedAnswer().trim());

        // BR-06: Kiểm tra có phải lần đầu tiên trả lời câu này không
        boolean isFirstAttempt = !interactionLogRepository
                .existsByStudent_UserIdAndAiElement_ElementId(studentId, element.getElementId());

        // Lưu log
        InteractionLog log = new InteractionLog();
        log.setStudent(student);
        log.setAiElement(element);
        log.setSubmittedAnswer(request.getSubmittedAnswer().trim().toUpperCase());
        log.setIsCorrect(isCorrect);
        log.setTimeSpent(request.getTimeSpent());
        log.setIsFirstAttempt(isFirstAttempt);

        InteractionLog saved = interactionLogRepository.save(log);

        return SubmitAnswerResponse.of(
                saved.getLogId(),
                element.getElementId(),
                saved.getSubmittedAnswer(),
                isCorrect,
                element.getCorrectAnswer(), // tiết lộ sau khi đã nộp
                isFirstAttempt
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private List<String> parseOptions(String optionsJson) {
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse options JSON: {}", optionsJson);
            return List.of();
        }
    }
}
