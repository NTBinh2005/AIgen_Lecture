package com.example.demo.event;

public record EssayScoreRequestedEvent(Long attemptId, Long answerId, Long answerVersion,
        String question, String response, String explanation, double maxPoints) {}
