package com.example.demo.service;

import com.example.demo.entity.Attempt;

import java.util.Map;

public interface GradingService {
    void autoGradeObjectiveQuestions(Attempt attempt);
    void suggestScoreForEssay(Integer actorId, Long attemptId, Long answerId);
    void confirmGrade(Integer teacherId, Long attemptId, Map<Long, Double> questionScores);
}
