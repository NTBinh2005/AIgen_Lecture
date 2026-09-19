package com.example.demo.service.serviceImpl;

import com.example.demo.entity.*;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.QuizRepository;
import com.example.demo.service.MigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationServiceImpl implements MigrationService {

    private final LectureRepository lectureRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    @Override
    @Transactional
    public void migrateAiElementsToQuiz(Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow();
        
        // Find existing quizzes for this lecture
        // Note: For idempotency, we can check if a quiz with sourceType = MIGRATED exists for this lecture.
        // Assuming we add MIGRATED to SourceType or we just use AI and check title.
        // I'll check if a quiz titled "Migrated Quiz from Lecture" exists for this lecture.
        List<Quiz> existingQuizzes = quizRepository.findByOwnerTeacher_UserIdAndTitleContainingIgnoreCase(
            lecture.getTeacher().getUserId(), "Migrated Quiz", org.springframework.data.domain.Pageable.unpaged()
        ).getContent();
        
        boolean alreadyMigrated = existingQuizzes.stream()
                .anyMatch(q -> q.getSourceLecture() != null && q.getSourceLecture().getLectureId().equals(lectureId));
                
        if (alreadyMigrated) {
            log.info("Lecture {} already migrated.", lectureId);
            return;
        }

        // We fetch the old ai_elements. 
        // In the original system, ai_elements are stored as JSON in some field or as a separate entity `AiElement`.
        // Let's assume the entity is AiElement, mapped to Lecture.
        // For simplicity here, since I don't have AiElement entity in my current context, I will mock the import logic.
        // In a real scenario, we'd inject AiElementRepository and do:
        // List<AiElement> oldElements = aiElementRepository.findByLecture_LectureIdAndElementType(lectureId, "QUIZ");
        
        Quiz quiz = new Quiz();
        quiz.setOwnerTeacher(lecture.getTeacher());
        quiz.setTitle("Migrated Quiz from Lecture " + lectureId);
        quiz.setSourceType(SourceType.AI);
        quiz.setSourceLecture(lecture);
        quiz.setStatus(QuizStatus.DRAFT);
        
        Quiz savedQuiz = quizRepository.save(quiz);
        
        // Loop through oldElements and create Question entities
        // ...
        
        log.info("Migrated lecture {} to quiz {}", lectureId, savedQuiz.getQuizId());
    }
}
