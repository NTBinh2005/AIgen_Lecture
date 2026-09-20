package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.GenerationJobCompletionRequest;
import com.example.demo.dto.request.GenerationJobFailureRequest;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.LectureStatus;
import com.example.demo.entity.LectureVersion;
import com.example.demo.entity.LectureVersionStatus;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.LectureVersionRepository;
import com.example.demo.service.GenerationJobService;
import com.example.demo.service.event.LectureGenerationQueuedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LectureGenerationResultService {
    private final LectureRepository lectureRepository;
    private final LectureVersionRepository lectureVersionRepository;
    private final GenerationJobService generationJobService;

    @Transactional
    public void complete(
            LectureGenerationQueuedEvent event,
            String content,
            String slideContent) {
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
}
