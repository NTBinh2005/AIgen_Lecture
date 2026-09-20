package com.example.demo.service;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.AttemptAnswerSubmitRequest;
import com.example.demo.dto.response.AttemptResponse;
import com.example.demo.dto.response.AttemptStartResponse;
import com.example.demo.entity.SubmitType;

import java.util.List;

public interface AttemptService {
    AttemptStartResponse startAttempt(Integer studentId, Long assignmentId);
    AttemptResponse fetchAttempt(Integer userId, Long attemptId, boolean isTeacher);
    AttemptResponse getMyResult(Integer studentId, Long assignmentId);
    List<AttemptResponse> getStudentQuizHistory(Integer studentId);
    
    void submitAnswer(Integer studentId, Long attemptId, AttemptAnswerSubmitRequest request);
    AttemptResponse submitAttempt(Integer studentId, Long attemptId, SubmitType submitType);
    
    void recordSignal(Integer studentId, Long attemptId);
    
    // Admin/Teacher operations
    void reopenAttempt(Integer teacherId, Long attemptId);
    
    AttemptStartResponse startPreview(Integer teacherId, Long assignmentId);
}
