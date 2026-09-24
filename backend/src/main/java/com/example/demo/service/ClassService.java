package com.example.demo.service;

import com.example.demo.dto.request.ClassCreateRequest;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.request.ClassUpdateRequest;
import com.example.demo.dto.response.ClassLectureResponse;

import java.util.List;

public interface ClassService {

    List<ClassResponse> findVisible(Integer currentUserId);

    ClassResponse findById(Integer classId, Integer currentUserId);

    /** ENRL-01: Teacher xem danh sách lớp mình phụ trách */
    List<ClassResponse> findByTeacher(Integer teacherId);

    /**
     * CLASS-AC-01: Tạo lớp. Status mặc định DRAFT.
     * Ném ConflictException 409 nếu classCode trùng trong cùng kỳ.
     */
    ClassResponse create(ClassCreateRequest request, Integer currentUserId);

    /**
     * CLASS-BR-02: Chỉ teacher chính hoặc admin được update.
     * CLASS-BR-05: Đổi teacher chính phải ghi audit log.
     */
    ClassResponse update(Integer classId, ClassUpdateRequest request, Integer currentUserId);

    /**
     * CLASS-AC-01: Kích hoạt lớp DRAFT → ACTIVE.
     * Yêu cầu className, classCode, startsAt đều không null.
     */
    ClassResponse activate(Integer classId, Integer currentUserId);

    /**
     * CLASS-BR-04: Đóng lớp ACTIVE → CLOSED.
     * Không thể đóng khi có live session đang chạy.
     * CLASS-AC-03: Giữ nguyên dữ liệu bài giảng, điểm, điểm danh.
     */
    ClassResponse close(Integer classId, Integer currentUserId);

    ClassResponse archive(Integer classId, Integer currentUserId);

    ClassLectureResponse assignLecture(Integer classId, Long lectureId, Integer currentUserId);

    List<ClassLectureResponse> findLectures(Integer classId, Integer currentUserId);

    void unassignLecture(Integer classId, Long lectureId, Integer currentUserId);

    /** Soft-delete (legacy — nội bộ, không expose ra API chính) */
    void deactivate(Integer classId);
}
