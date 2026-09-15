package com.example.demo.service;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.entity.Lecture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LectureService {
    LectureResponse createLecture(
            Integer teacherId,
            LectureCreateRequest request
    );

    Page<LectureResponse> getLecturesByTeacher(
            Integer teacherId,
            String titleKeyword,
            Pageable pageable
    );

    Page<LectureResponse> getAllLecturesForStudent(
            String titleKeyword,
            Pageable pageable
    );

    LectureResponse getLecture(
            Long lectureId,
            Integer requesterId,
            UserPrincipal principal
    );

    LectureResponse updateLectureTitle(
            Long lectureId,
            Integer requesterId,
            String newTitle
    );

    void deleteLecture(
            Long lectureId,
            Integer requesterId
    );

    Lecture getVideoStatus(Long lectureId);
}
