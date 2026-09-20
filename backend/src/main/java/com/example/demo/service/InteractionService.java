package com.example.demo.service;

import com.example.demo.dto.response.QuizResponse;
import com.example.demo.dto.request.SubmitAnswerRequest;
import com.example.demo.dto.response.SubmitAnswerResponse;

import java.util.List;

public interface InteractionService {
    List<QuizResponse> getQuizzes(
            Long lectureId,
            boolean isTeacher
    );

    SubmitAnswerResponse submitAnswer(
            Integer studentId,
            SubmitAnswerRequest request
    );
}
