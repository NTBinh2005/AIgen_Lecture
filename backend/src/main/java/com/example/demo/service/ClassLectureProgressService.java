package com.example.demo.service;

import com.example.demo.dto.request.ClassProgressUpdateRequest;
import com.example.demo.dto.response.ClassProgressResponse;

public interface ClassLectureProgressService {
    void recordProgress(Integer studentId, Integer classId, Long lectureId, ClassProgressUpdateRequest request);
    ClassProgressResponse getProgress(Integer studentId, Integer classId);
    boolean isEligibleForRefundProgress(Integer studentId, Integer classId);
}
