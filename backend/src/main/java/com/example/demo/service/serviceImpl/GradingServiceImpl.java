package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.QuestionDto;
import com.example.demo.entity.Attempt;
import com.example.demo.entity.AttemptAnswer;
import com.example.demo.entity.QuestionType;
import com.example.demo.repository.AttemptAnswerRepository;
import com.example.demo.repository.AttemptRepository;
import com.example.demo.service.GradingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradingServiceImpl implements GradingService {
    
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final AttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void autoGradeObjectiveQuestions(Attempt attempt) {
        List<AttemptAnswer> answers = attemptAnswerRepository.findByAttempt_AttemptId(attempt.getAttemptId());
        
        List<QuestionDto> questions;
        try {
            questions = objectMapper.readValue(attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse questions snapshot", e);
        }
        
        Map<Long, QuestionDto> questionMap = questions.stream()
                .collect(Collectors.toMap(QuestionDto::getQuestionId, q -> q));
                
        double totalObjectiveScore = 0.0;
        
        for (AttemptAnswer answer : answers) {
            QuestionDto q = questionMap.get(answer.getQuestionId());
            if (q == null) continue;
            
            if (q.getQuestionType() == QuestionType.MCQ_SINGLE || 
                q.getQuestionType() == QuestionType.TRUE_FALSE) {
                
                if (answer.getResponse() != null && answer.getResponse().trim().equalsIgnoreCase(q.getCorrectAnswer())) {
                    answer.setIsCorrect(true);
                    answer.setPointsAwarded((double) q.getPoints());
                    totalObjectiveScore += q.getPoints();
                } else {
                    answer.setIsCorrect(false);
                    answer.setPointsAwarded(0.0);
                }
            } else if (q.getQuestionType() == QuestionType.SHORT_ANSWER) {
                if (answer.getResponse() != null && answer.getResponse().trim().equalsIgnoreCase(q.getCorrectAnswer().trim())) {
                    answer.setIsCorrect(true);
                    answer.setPointsAwarded((double) q.getPoints());
                    totalObjectiveScore += q.getPoints();
                } else {
                    answer.setIsCorrect(false);
                    answer.setPointsAwarded(0.0);
                }
            } else if (q.getQuestionType() == QuestionType.ESSAY) {
                // Async trigger AI scoring
                suggestScoreForEssay(answer.getAnswerId());
            }
        }
        
        attemptAnswerRepository.saveAll(answers);
        
        attempt.setObjectiveScore(totalObjectiveScore);
        
        boolean hasEssay = questions.stream().anyMatch(q -> q.getQuestionType() == QuestionType.ESSAY);
        
        if (!hasEssay) {
            attempt.setFinalScore(totalObjectiveScore);
        }
    }

    @Override
    @Transactional
    public void suggestScoreForEssay(Long answerId) {
        // Trigger AI service asynchronously if not already handled
        // Simplified for now - assume AI suggests and updates the AttemptAnswer
    }

    @Override
    @Transactional
    public void confirmGrade(Integer teacherId, Long attemptId, Map<Long, Double> questionScores) {
        Attempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        // Verify teacher ownership...
        
        List<AttemptAnswer> answers = attemptAnswerRepository.findByAttempt_AttemptId(attemptId);
        
        double finalScore = attempt.getObjectiveScore() != null ? attempt.getObjectiveScore() : 0.0;
        
        for (AttemptAnswer answer : answers) {
            if (questionScores.containsKey(answer.getQuestionId())) {
                double score = questionScores.get(answer.getQuestionId());
                answer.setTeacherFinalScore(score);
                answer.setPointsAwarded(score);
                finalScore += score;
                // answer.setGradedBy(teacherUser);
                answer.setGradedAt(java.time.LocalDateTime.now());
            } else {
                if (answer.getPointsAwarded() != null && answer.getTeacherFinalScore() == null) {
                    // It means it was auto-graded and teacher didn't override
                    finalScore += answer.getPointsAwarded();
                } else if (answer.getTeacherFinalScore() != null) {
                    finalScore += answer.getTeacherFinalScore();
                }
            }
        }
        
        attemptAnswerRepository.saveAll(answers);
        
        attempt.setFinalScore(finalScore);
        attempt.setStatus(com.example.demo.entity.AttemptStatus.GRADED);
        attemptRepository.save(attempt);
        
        // Log auditing if necessary
    }
}
