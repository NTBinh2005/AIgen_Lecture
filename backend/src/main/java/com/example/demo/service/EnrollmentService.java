package com.example.demo.service;

import com.example.demo.dto.request.EnrollmentRequest;
import com.example.demo.dto.response.EnrollmentResponse;
import com.example.demo.dto.request.EnrollmentStatusRequest;

import java.util.List;

public interface EnrollmentService {
    /** ENRL-01: Admin/Teacher xem toàn bộ hoặc lọc theo lớp */
    List<EnrollmentResponse> findAll();

    /** ENRL-01: Danh sách student trong một lớp */
    List<EnrollmentResponse> findByClass(Integer classId);

    /** Tất cả lớp của một student (admin/teacher view) */
    List<EnrollmentResponse> findByStudent(Integer studentId);

    /**
     * ENRL-02: Student xem lớp của mình — chỉ trả enrollment
     * có trạng thái ACTIVE hoặc COMPLETED.
     */
    List<EnrollmentResponse> findByStudentForSelf(Integer studentId);

    /**
     * ENRL-03: Admin hoặc Teacher ghi danh student vào lớp.
     * CLASS-BR-04: Không thể enroll vào lớp CLOSED.
     */
    EnrollmentResponse enroll(Integer classId, EnrollmentRequest request);

    /** ENRL-04: Cập nhật trạng thái enrollment */
    EnrollmentResponse updateStatus(Integer classId, Integer studentId, EnrollmentStatusRequest request);

    /**
     * ENRL-05: Hủy ghi danh — đổi sang CANCELLED.
     * Giữ nguyên lịch sử (soft-cancel).
     */
    void cancel(Integer classId, Integer studentId);

    /**
     * ENRL-06: Student tự đăng ký bằng classCode.
     * Lớp phải ở trạng thái ACTIVE.
     */
    EnrollmentResponse selfEnroll(String classCode, Integer studentId);

    /** @deprecated dùng cancel() thay thế */
    @Deprecated
    void deactivate(Integer classId, Integer studentId);
}

