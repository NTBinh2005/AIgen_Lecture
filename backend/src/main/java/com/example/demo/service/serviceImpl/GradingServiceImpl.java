package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.QuestionDto;
import com.example.demo.entity.Attempt;
import com.example.demo.entity.AttemptAnswer;
import com.example.demo.entity.QuestionType;
import com.example.demo.repository.AttemptAnswerRepository;
import com.example.demo.repository.AttemptRepository;
import com.example.demo.service.GradingService;
import com.example.demo.service.LlmService;
import com.example.demo.service.ClassAccessService;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.entity.AttemptStatus;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
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
    private final ClassAccessService classAccessService;

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
                
                String correctAnswer = q.getCorrectAnswer();
                if (correctAnswer != null && answer.getResponse() != null
                        && answer.getResponse().trim().equalsIgnoreCase(correctAnswer.trim())) {
                    answer.setIsCorrect(true);
                    answer.setPointsAwarded((double) q.getPoints());
                    totalObjectiveScore += q.getPoints();
                } else {
                    answer.setIsCorrect(false);
                    answer.setPointsAwarded(0.0);
                }
            } else if (q.getQuestionType() == QuestionType.SHORT_ANSWER) {
                String correctAnswer = q.getCorrectAnswer();
                if (correctAnswer != null && answer.getResponse() != null
                        && answer.getResponse().trim().equalsIgnoreCase(correctAnswer.trim())) {
                    answer.setIsCorrect(true);
                    answer.setPointsAwarded((double) q.getPoints());
                    totalObjectiveScore += q.getPoints();
                } else {
                    answer.setIsCorrect(false);
                    answer.setPointsAwarded(0.0);
                }
            } else if (q.getQuestionType() == QuestionType.ESSAY) {
                // Async trigger AI scoring
                suggestScoreForEssayUnchecked(answer);
            }
        }
        
        attemptAnswerRepository.saveAll(answers);
        
        attempt.setObjectiveScore(totalObjectiveScore);
        
        boolean hasEssay = questions.stream().anyMatch(q -> q.getQuestionType() == QuestionType.ESSAY);
        
        if (!hasEssay) {
            attempt.setFinalScore(totalObjectiveScore);
        }
    }

    private final LlmService llmService;

    @Override
    @Transactional
    public void suggestScoreForEssay(Integer actorId, Long attemptId, Long answerId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found: " + attemptId));
        classAccessService.assertCanManage(attempt.getAssignment().getClassEntity(), actorId);
        AttemptAnswer answer = attemptAnswerRepository.findById(answerId)
                .filter(value -> value.getAttempt().getAttemptId().equals(attemptId))
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found: " + answerId));
        suggestScoreForEssayUnchecked(answer);
    }

    private void suggestScoreForEssayUnchecked(AttemptAnswer answer) {
        Attempt attempt = answer.getAttempt();
        
        List<QuestionDto> questions;
        try {
            questions = objectMapper.readValue(attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
        } catch (Exception e) {
            return;
        }
        
        QuestionDto question = questions.stream().filter(q -> q.getQuestionId().equals(answer.getQuestionId())).findFirst().orElse(null);
        if (question == null || question.getQuestionType() != QuestionType.ESSAY) return;
        
        // Use Async for AI scoring (simplified here, but should be run async via Executor)
        CompletableFuture.runAsync(() -> {
            try {
                Double suggestedScore = llmService.aiSuggestScore(question.getQuestionText(), answer.getResponse(), question.getExplanation());
                if (suggestedScore != null) {
                    answer.setAiSuggestedScore(suggestedScore);
                    attemptAnswerRepository.save(answer);
                }
            } catch (Exception e) {
                // Ignore AI failure
            }
        });
    }

    @Override
    @Transactional
    public void confirmGrade(Integer teacherId, Long attemptId, Map<Long, Double> questionScores) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found: " + attemptId));
        classAccessService.assertCanManage(attempt.getAssignment().getClassEntity(), teacherId);

        List<QuestionDto> questions;
        try {
            questions = objectMapper.readValue(
                    attempt.getQuizVersion().getQuestionsSnapshot(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BadRequestException("Cannot parse quiz questions");
        }
        Map<Long, QuestionDto> questionMap = questions.stream()
                .collect(Collectors.toMap(QuestionDto::getQuestionId, question -> question));
        
        List<AttemptAnswer> answers = attemptAnswerRepository.findByAttempt_AttemptId(attemptId);
        
        Map<Long, AttemptAnswer> answersByQuestion = answers.stream()
                .collect(Collectors.toMap(AttemptAnswer::getQuestionId, answer -> answer));
        for (Map.Entry<Long, Double> entry : questionScores.entrySet()) {
            QuestionDto question = questionMap.get(entry.getKey());
            if (question == null || !answersByQuestion.containsKey(entry.getKey())) {
                throw new BadRequestException("Question does not belong to this attempt: " + entry.getKey());
            }
            Double score = entry.getValue();
            if (score == null || score < 0 || score > question.getPoints()) {
                throw new BadRequestException("Score must be between 0 and " + question.getPoints()
                        + " for question " + entry.getKey());
            }
        }

        double finalScore = 0.0;
        
        for (AttemptAnswer answer : answers) {
            if (questionScores.containsKey(answer.getQuestionId())) {
                double score = questionScores.get(answer.getQuestionId());
                answer.setTeacherFinalScore(score);
                answer.setPointsAwarded(score);
                // answer.setGradedBy(teacherUser);
                answer.setGradedAt(LocalDateTime.now());
            } else {
                // Existing auto/manual score is retained.
            }
            if (answer.getPointsAwarded() != null) {
                finalScore += answer.getPointsAwarded();
            }
        }
        
        attemptAnswerRepository.saveAll(answers);
        
        attempt.setFinalScore(finalScore);
        attempt.setStatus(AttemptStatus.GRADED);
        attemptRepository.save(attempt);
        
        // Log auditing if necessary
    }
}
