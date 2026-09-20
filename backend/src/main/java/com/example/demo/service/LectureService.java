package com.example.demo.service;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.LectureCreateRequest;
import com.example.demo.dto.request.LectureUpdateRequest;
import com.example.demo.dto.response.LectureResponse;
import com.example.demo.dto.response.LectureVersionResponse;
import com.example.demo.entity.Lecture;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LectureService {
    LectureResponse createLecture(Integer teacherId, LectureCreateRequest request);

    Page<LectureResponse> getLecturesByTeacher(Integer teacherId, String titleKeyword, Pageable pageable);

    /** Legacy direct-student route is fail-closed; Class service must grant access. */
    Page<LectureResponse> getAllLecturesForStudent(String titleKeyword, Pageable pageable);

    LectureResponse getLecture(Long lectureId, Integer requesterId, UserPrincipal principal);

    LectureResponse updateLectureTitle(Long lectureId, Integer requesterId, String newTitle);

    LectureResponse updateLecture(
            Long lectureId,
            Integer requesterId,
            boolean admin,
            LectureUpdateRequest request);

    LectureResponse publishLecture(
            Long lectureId,
            UUID versionId,
            Integer requesterId,
            boolean admin);

    void deleteLecture(Long lectureId, Integer requesterId);

    void archiveLecture(Long lectureId, Integer requesterId, boolean admin);

    List<LectureVersionResponse> getVersions(Long lectureId, Integer requesterId, boolean admin);

    LectureVersionResponse getVersion(
            Long lectureId,
            UUID versionId,
            Integer requesterId,
            UserPrincipal principal);

    LectureVersionResponse getPublishedVersionContract(UUID versionId);

    LectureVersionResponse getSourceVersionForPresentation(
            UUID versionId, Integer requesterId, boolean admin);

    List<Integer> getCollaborators(Long lectureId, Integer requesterId, boolean admin);

    void addCollaborator(Long lectureId, Integer collaboratorId, Integer requesterId, boolean admin);

    void removeCollaborator(Long lectureId, Integer collaboratorId, Integer requesterId, boolean admin);

    Lecture getVideoStatus(Long lectureId);
}
