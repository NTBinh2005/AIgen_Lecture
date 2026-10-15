package com.example.demo.service;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QuizAssignmentCreateRequest;
import com.example.demo.dto.response.QuizAssignmentResponse;
import com.example.demo.dto.response.StudentAssignmentResponse;
import com.example.demo.dto.response.AttemptResponse;

import java.util.List;

public interface QuizAssignmentService {
    QuizAssignmentResponse createAssignment(Integer teacherId, QuizAssignmentCreateRequest request);
    List<QuizAssignmentResponse> getAssignmentsByClass(Integer teacherId, Integer classId);
    List<StudentAssignmentResponse> getStudentAssignments(Integer studentId, Integer classId, String statusFilter);
    QuizAssignmentResponse updateAssignment(Integer actorId, Long id, com.example.demo.dto.request.QuizAssignmentUpdateRequest request);
    void deleteAssignment(Integer actorId, Long id);
    org.springframework.data.domain.Page<com.example.demo.dto.response.AttemptSummaryResponse> getAssignmentProgress(
            Integer teacherId, Long assignmentId, org.springframework.data.domain.Pageable pageable);
}
