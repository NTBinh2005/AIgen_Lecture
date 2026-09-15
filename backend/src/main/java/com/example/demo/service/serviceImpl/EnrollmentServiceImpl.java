package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.EnrollmentRequest;
import com.example.demo.dto.response.EnrollmentResponse;
import com.example.demo.dto.request.EnrollmentStatusRequest;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassStatus;
import com.example.demo.entity.ClassStudent;
import com.example.demo.entity.ClassStudentId;
import com.example.demo.entity.EnrollmentStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import com.example.demo.service.EnrollmentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    public EnrollmentServiceImpl(
            ClassStudentRepository classStudentRepository,
            ClassRepository classRepository,
            UserRepository userRepository
    ) {
        this.classStudentRepository = classStudentRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /** ENRL-01: Admin/Teacher xem tất cả */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findAll() {
        return classStudentRepository.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * ENRL-01: Danh sách student trong một lớp.
     * Exception: student chưa ghi danh không được xem danh sách thành viên.
     */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findByClass(Integer classId) {
        if (!classRepository.existsById(classId)) {
            throw new ResourceNotFoundException("Class not found: " + classId);
        }
        return classStudentRepository.findByClassEntity_ClassId(classId)
                .stream().map(this::toResponse).toList();
    }

    /** Toàn bộ lớp của một student — dùng cho admin/teacher view */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findByStudent(Integer studentId) {
        if (!userRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found: " + studentId);
        }
        return classStudentRepository.findByStudent_UserId(studentId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * ENRL-02: Student xem lớp của chính mình.
     * CLASS-AC-02: Chỉ trả các lớp có enrollment ACTIVE hoặc COMPLETED.
     */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findByStudentForSelf(Integer studentId) {
        if (!userRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found: " + studentId);
        }
        List<EnrollmentStatus> visibleStatuses = List.of(
                EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);
        return classStudentRepository
                .findByStudent_UserIdAndStatusIn(studentId, visibleStatuses)
                .stream().map(this::toResponse).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ENRL-03: Admin hoặc Teacher ghi danh student vào lớp.
     * CLASS-BR-04: Không thể enroll vào lớp CLOSED.
     */
    @Transactional
    public EnrollmentResponse enroll(Integer classId, EnrollmentRequest request) {
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));

        // CLASS-BR-04: lớp CLOSED không nhận enrollment mới
        if (classEntity.getStatus() == ClassStatus.CLOSED) {
            throw new BadRequestException(
                    "Lớp đã đóng, không thể ghi danh học viên mới (CLASS-BR-04)");
        }

        User student = getStudent(request.studentId());
        ClassStudentId enrollmentId = new ClassStudentId(classId, student.getUserId());

        ClassStudent enrollment = classStudentRepository.findById(enrollmentId).orElse(null);
        if (enrollment != null) {
            if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
                throw new BadRequestException("Học viên đã đang ghi danh trong lớp này");
            }
            // Tái kích hoạt enrollment đã huỷ/tạm dừng
            enrollment.setStatus(EnrollmentStatus.ACTIVE);
            enrollment.setEnrolledAt(LocalDateTime.now());
            return toResponse(enrollment);
        }

        ClassStudent newEnrollment = new ClassStudent();
        newEnrollment.setId(enrollmentId);
        newEnrollment.setClassEntity(classEntity);
        newEnrollment.setStudent(student);
        newEnrollment.setStatus(EnrollmentStatus.ACTIVE);
        newEnrollment.setEnrolledAt(LocalDateTime.now());

        return toResponse(classStudentRepository.save(newEnrollment));
    }

    /** ENRL-04: Cập nhật trạng thái enrollment */
    @Transactional
    public EnrollmentResponse updateStatus(Integer classId, Integer studentId, EnrollmentStatusRequest request) {
        ClassStudent enrollment = getEnrollment(classId, studentId);
        enrollment.setStatus(request.status());
        return toResponse(enrollment);
    }

    /**
     * ENRL-05: Hủy ghi danh — đổi sang CANCELLED.
     * Giữ nguyên lịch sử.
     */
    @Transactional
    public void cancel(Integer classId, Integer studentId) {
        ClassStudent enrollment = getEnrollment(classId, studentId);
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new BadRequestException("Ghi danh này đã bị hủy trước đó");
        }
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
    }

    /**
     * ENRL-06: Student tự đăng ký bằng classCode.
     * Lớp phải đang ACTIVE.
     */
    @Transactional
    public EnrollmentResponse selfEnroll(String classCode, Integer studentId) {
        ClassEntity classEntity = classRepository.findByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lớp với mã: " + classCode));

        // Chỉ cho phép enroll lớp ACTIVE
        if (classEntity.getStatus() != ClassStatus.ACTIVE) {
            throw new BadRequestException(
                    "Lớp không ở trạng thái ACTIVE, không thể tự đăng ký");
        }

        User student = getStudent(studentId);
        ClassStudentId enrollmentId = new ClassStudentId(classEntity.getClassId(), student.getUserId());

        ClassStudent existing = classStudentRepository.findById(enrollmentId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == EnrollmentStatus.ACTIVE) {
                throw new BadRequestException("Bạn đã đang ghi danh trong lớp này");
            }
            existing.setStatus(EnrollmentStatus.ACTIVE);
            existing.setEnrolledAt(LocalDateTime.now());
            return toResponse(existing);
        }

        ClassStudent newEnrollment = new ClassStudent();
        newEnrollment.setId(enrollmentId);
        newEnrollment.setClassEntity(classEntity);
        newEnrollment.setStudent(student);
        newEnrollment.setStatus(EnrollmentStatus.ACTIVE);
        newEnrollment.setEnrolledAt(LocalDateTime.now());

        return toResponse(classStudentRepository.save(newEnrollment));
    }

    /** @deprecated Dùng cancel() thay thế */
    @Deprecated
    @Transactional
    public void deactivate(Integer classId, Integer studentId) {
        cancel(classId, studentId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private User getStudent(Integer studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("User không phải là học viên: " + studentId);
        }
        return student;
    }

    private ClassStudent getEnrollment(Integer classId, Integer studentId) {
        return classStudentRepository.findById(new ClassStudentId(classId, studentId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy ghi danh cho lớp " + classId + " và học viên " + studentId));
    }

    private EnrollmentResponse toResponse(ClassStudent enrollment) {
        return new EnrollmentResponse(
                enrollment.getClassEntity().getClassId(),
                enrollment.getClassEntity().getClassName(),
                enrollment.getStudent().getUserId(),
                enrollment.getStudent().getName(),
                enrollment.getEnrolledAt(),
                enrollment.getStatus()
        );
    }
}
