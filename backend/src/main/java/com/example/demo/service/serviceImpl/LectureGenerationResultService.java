package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.GenerationJobCompletionRequest;
import com.example.demo.dto.request.GenerationJobFailureRequest;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.AiElement;
import com.example.demo.entity.LectureStatus;
import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.AiElementRepository;
import com.example.demo.repository.LectureVersionRepository;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.event.LectureGenerationQueuedEvent;
import com.example.demo.service.event.LectureVideoRequestedEvent;
import com.example.demo.dto.response.LectureGenerateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LectureGenerationResultService {
    private final LectureRepository lectureRepository;
    private final LectureVersionRepository lectureVersionRepository;
    private final AiElementRepository aiElementRepository;
    private final GenerationJobService generationJobService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void complete(
            LectureGenerationQueuedEvent event,
            String content,
            String slideContent,
            List<LectureGenerateResponse.QuizDto> quizzes) {
        Lecture lecture = lectureRepository.findById(event.lectureId())
                .orElseThrow(() -> new IllegalStateException("Lecture was removed during generation"));
        LectureVersion version = lectureVersionRepository.findById(lecture.getCurrentVersionId())
                .orElseThrow(() -> new IllegalStateException("Lecture draft version is missing"));

        generationJobService.complete(
                event.jobId(),
                event.attemptId(),
                new GenerationJobCompletionRequest(
                        "lecture-version:" + version.getLectureVersionId(),
                        "LECTURE",
                        event.lectureId().toString()));

        version.setContent(content);
        version.setSlideContent(slideContent);
        version.setAiGenerated(true);
        version.setStatus(LectureVersionStatus.READY);
        lectureVersionRepository.save(version);

        lecture.setOriginalSource(content);
        lecture.setStatus(LectureStatus.READY);
        lectureRepository.save(lecture);
        replaceLegacyQuizzes(lecture, quizzes);
        eventPublisher.publishEvent(new LectureVideoRequestedEvent(
                lecture.getLectureId(), slideContent));
    }

    @Transactional
    public void fail(LectureGenerationQueuedEvent event) {
        try {
            generationJobService.fail(
                    event.jobId(),
                    event.attemptId(),
                    new GenerationJobFailureRequest(
                            "LECTURE_GENERATION_FAILED",
                            "The lecture could not be generated. Please retry the job."));
        } finally {
            lectureRepository.findById(event.lectureId()).ifPresent(lecture -> {
                if (event.jobId().equals(lecture.getLatestGenerationJobId())
                        && lecture.getPublishedVersionId() == null) {
                    lecture.setStatus(LectureStatus.FAILED);
                    lectureRepository.save(lecture);
                }
            });
        }
    }

    private void replaceLegacyQuizzes(
            Lecture lecture,
            List<LectureGenerateResponse.QuizDto> quizzes) {
        aiElementRepository.deleteByLecture_LectureId(lecture.getLectureId());
        if (quizzes == null) {
            return;
        }
        for (int index = 0; index < quizzes.size(); index++) {
            LectureGenerateResponse.QuizDto quiz = quizzes.get(index);
            if (quiz == null || quiz.getQuestionText() == null || quiz.getQuestionText().isBlank()
                    || quiz.getOptions() == null || quiz.getOptions().size() < 2
                    || quiz.getCorrectAnswer() == null) {
                throw new IllegalStateException("Generated quiz payload is invalid");
            }
            String answer = quiz.getCorrectAnswer().trim().toUpperCase(Locale.ROOT);
            int answerIndex = answer.length() == 1 ? answer.charAt(0) - 'A' : -1;
            if (answerIndex < 0 || answerIndex >= quiz.getOptions().size()) {
                throw new IllegalStateException("Generated quiz correctAnswer is invalid");
            }
            AiElement element = new AiElement();
            element.setLecture(lecture);
            element.setQuestionText(quiz.getQuestionText().trim());
            element.setOptions(writeOptions(quiz.getOptions()));
            element.setCorrectAnswer(answer);
            element.setOrderIndex(index);
            aiElementRepository.save(element);
        }
    }

    private String writeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Generated quiz options could not be serialized", exception);
        }
    }
}
