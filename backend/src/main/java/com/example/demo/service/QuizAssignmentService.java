package com.example.demo.service;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.QuizAssignmentCreateRequest;
import com.example.demo.dto.response.QuizAssignmentResponse;
import com.example.demo.dto.response.StudentAssignmentResponse;

import java.util.List;

public interface QuizAssignmentService {
    QuizAssignmentResponse createAssignment(Integer teacherId, QuizAssignmentCreateRequest request);
    List<QuizAssignmentResponse> getAssignmentsByClass(Integer teacherId, Integer classId);
    List<StudentAssignmentResponse> getStudentAssignments(Integer studentId, Integer classId, String statusFilter);
}
