package com.example.demo.service;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QuizCreateRequest;
import com.example.demo.dto.request.QuizUpdateRequest;
import com.example.demo.dto.response.QuizDetailResponse;
import com.example.demo.dto.response.QuizVersionResponse;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.demo.entity.QuizStatus;
import com.example.demo.entity.SourceType;

public interface QuizService {
    QuizDetailResponse createQuizDraft(Integer teacherId, QuizCreateRequest request);
    QuizDetailResponse updateQuizDraft(Integer teacherId, Long quizId, QuizUpdateRequest request);
    QuizDetailResponse getQuiz(Long quizId, UserPrincipal principal);
    QuizVersionResponse publishQuiz(Integer teacherId, Long quizId);
    void closeQuiz(Integer teacherId, Long quizId);
    void archiveQuiz(Integer teacherId, Long quizId);
    List<QuizVersionResponse> getQuizVersions(Long quizId, UserPrincipal principal);
    Page<QuizDetailResponse> getQuizzes(Integer userId, boolean isAdmin, String title, QuizStatus status, SourceType sourceType, Pageable pageable);
}
