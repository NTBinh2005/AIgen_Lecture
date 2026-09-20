package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.EnrollmentRequest;
import com.example.demo.dto.request.SelfEnrollRequest;
import com.example.demo.dto.response.EnrollmentResponse;
import com.example.demo.dto.request.EnrollmentStatusRequest;
import com.example.demo.service.EnrollmentService;
import com.example.demo.service.serviceImpl.EnrollmentServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentServiceImpl enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    /** ENRL-01: Admin xem tất cả enrollment */
    @GetMapping("/enrollments")
    @Operation(summary = "ENRL-01: List all enrollments (ADMIN)")
    public List<EnrollmentResponse> findAll() {
        return enrollmentService.findAll();
    }

    /**
     * ENRL-01: Danh sách học viên trong một lớp.
     * Exception: student chưa ghi danh không được xem (kiểm tra ở service layer).
     */
    @GetMapping("/classes/{classId}/students")
    @Operation(summary = "ENRL-01: List students in a class (TEACHER / ADMIN)")
    public List<EnrollmentResponse> findByClass(@PathVariable Integer classId) {
        return enrollmentService.findByClass(classId);
    }

    /** Admin/Teacher xem toàn bộ lớp của một student */
    @GetMapping("/students/{studentId}/classes")
    @Operation(summary = "List classes for a student (ADMIN / TEACHER)")
    public List<EnrollmentResponse> findByStudent(@PathVariable Integer studentId) {
        return enrollmentService.findByStudent(studentId);
    }

    /**
     * ENRL-02: Student xem lớp của chính mình.
     * CLASS-AC-02: Chỉ trả lớp có enrollment ACTIVE hoặc COMPLETED.
     */
    @GetMapping("/students/me/classes")
    @Operation(summary = "ENRL-02: Get my enrolled classes (STUDENT)")
    public List<EnrollmentResponse> findMyClasses(@AuthenticationPrincipal UserPrincipal principal) {
        return enrollmentService.findByStudentForSelf(principal.getUserId());
    }

    // ── WRITE ─────────────────────────────────────────────────────────────────

    /**
     * ENRL-03: Admin hoặc Teacher ghi danh học viên vào lớp.
     * CLASS-BR-04: Lớp CLOSED không nhận enrollment.
     */
    @PostMapping("/classes/{classId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "ENRL-03: Enroll a student (TEACHER / ADMIN)")
    public EnrollmentResponse enroll(
            @PathVariable Integer classId,
            @Valid @RequestBody EnrollmentRequest request
    ) {
        return enrollmentService.enroll(classId, request);
    }

    /**
     * ENRL-06: Student tự đăng ký bằng mã lớp (classCode).
     * Lớp phải đang ACTIVE.
     */
    @PostMapping("/classes/self-enroll")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "ENRL-06: Self-enroll by class code (STUDENT)")
    public EnrollmentResponse selfEnroll(
            @Valid @RequestBody SelfEnrollRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return enrollmentService.selfEnroll(request.classCode(), principal.getUserId());
    }

    /** ENRL-04: Cập nhật trạng thái enrollment (ACTIVE/SUSPENDED/COMPLETED/CANCELLED) */
    @PatchMapping("/classes/{classId}/students/{studentId}")
    @Operation(summary = "ENRL-04: Update enrollment status (TEACHER / ADMIN)")
    public EnrollmentResponse updateStatus(
            @PathVariable Integer classId,
            @PathVariable Integer studentId,
            @Valid @RequestBody EnrollmentStatusRequest request
    ) {
        return enrollmentService.updateStatus(classId, studentId, request);
    }

    /**
     * ENRL-05: Hủy ghi danh — đổi sang CANCELLED.
     * Giữ nguyên lịch sử truy cập.
     */
    @DeleteMapping("/classes/{classId}/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "ENRL-05: Cancel enrollment (TEACHER / ADMIN)")
    public void cancel(@PathVariable Integer classId, @PathVariable Integer studentId) {
        enrollmentService.cancel(classId, studentId);
    }
}
