package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.EnrollmentRequest;
import com.example.demo.dto.response.EnrollmentResponse;
import com.example.demo.dto.response.BulkEnrollmentItemResponse;
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
import java.util.ArrayList;

import com.example.demo.service.EnrollmentService;
import com.example.demo.service.ClassAccessService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final ClassAccessService classAccessService;

    public EnrollmentServiceImpl(
            ClassStudentRepository classStudentRepository,
            ClassRepository classRepository,
            UserRepository userRepository,
            ClassAccessService classAccessService
    ) {
        this.classStudentRepository = classStudentRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.classAccessService = classAccessService;
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
    public List<EnrollmentResponse> findByClass(Integer classId, Integer currentUserId) {
        ClassEntity classEntity = getClassEntity(classId);
        classAccessService.assertCanManage(classEntity, currentUserId);
        return classStudentRepository.findByClassEntity_ClassId(classId)
                .stream().map(this::toResponse).toList();
    }

    /** Toàn bộ lớp của một student — dùng cho admin/teacher view */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findByStudent(Integer studentId, Integer currentUserId) {
        if (!userRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found: " + studentId);
        }
        User currentUser = classAccessService.requireUser(currentUserId);
        return classStudentRepository.findByStudent_UserId(studentId).stream()
                .filter(enrollment -> currentUser.getRole() == UserRole.ADMIN
                        || classAccessService.canManage(enrollment.getClassEntity(), currentUserId))
                .map(this::toResponse).toList();
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
    public EnrollmentResponse enroll(Integer classId, EnrollmentRequest request, Integer currentUserId) {
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));

        classAccessService.assertCanManage(classEntity, currentUserId);

        // CLASS-BR-04: lớp CLOSED không nhận enrollment mới
        if (classEntity.getStatus() != ClassStatus.ACTIVE) {
            throw new BadRequestException(
                    "Lớp đã đóng, không thể ghi danh học viên mới (CLASS-BR-04)");
        }

        User student = getStudent(request.studentId());
        ClassStudentId enrollmentId = new ClassStudentId(classId, student.getUserId());

        ClassStudent enrollment = classStudentRepository.findById(enrollmentId).orElse(null);
        validateEnrollmentWindowAndCapacity(classEntity, enrollment);
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
    public List<BulkEnrollmentItemResponse> enrollBulk(Integer classId, List<Integer> studentIds,
                                                        Integer currentUserId) {
        ClassEntity classEntity = getClassEntity(classId);
        classAccessService.assertCanManage(classEntity, currentUserId);
        List<BulkEnrollmentItemResponse> results = new ArrayList<>();
        for (Integer studentId : studentIds) {
            try {
                EnrollmentResponse enrollment = enroll(classId, new EnrollmentRequest(studentId), currentUserId);
                results.add(new BulkEnrollmentItemResponse(studentId, true, enrollment, null));
            } catch (RuntimeException ex) {
                results.add(new BulkEnrollmentItemResponse(studentId, false, null, ex.getMessage()));
            }
        }
        return results;
    }

    @Transactional
    public EnrollmentResponse updateStatus(Integer classId, Integer studentId,
                                           EnrollmentStatusRequest request, Integer currentUserId) {
        classAccessService.assertCanManage(getClassEntity(classId), currentUserId);
        ClassStudent enrollment = getEnrollment(classId, studentId);
        if (request.status() == EnrollmentStatus.COMPLETED) {
            LocalDateTime classEndsAt = enrollment.getClassEntity().getEndsAt();
            if (classEndsAt == null || LocalDateTime.now().isBefore(classEndsAt)) {
                throw new BadRequestException(
                        "Enrollment can only be completed after the class end time");
            }
        }
        enrollment.setStatus(request.status());
        return toResponse(enrollment);
    }

    /**
     * ENRL-05: Hủy ghi danh — đổi sang CANCELLED.
     * Giữ nguyên lịch sử.
     */
    @Transactional
    public void cancel(Integer classId, Integer studentId, Integer currentUserId) {
        classAccessService.assertCanManage(getClassEntity(classId), currentUserId);
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
        validateEnrollmentWindowAndCapacity(classEntity, existing);
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
        ClassStudent enrollment = getEnrollment(classId, studentId);
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private ClassEntity getClassEntity(Integer classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    private void validateEnrollmentWindowAndCapacity(ClassEntity classEntity,
                                                       ClassStudent existingEnrollment) {
        LocalDateTime now = LocalDateTime.now();
        if (classEntity.getEnrollmentOpensAt() != null
                && now.isBefore(classEntity.getEnrollmentOpensAt())) {
            throw new BadRequestException("Enrollment has not opened yet");
        }
        if (classEntity.getEnrollmentClosesAt() != null
                && now.isAfter(classEntity.getEnrollmentClosesAt())) {
            throw new BadRequestException("Enrollment is closed");
        }
        boolean alreadyOccupiesSeat = existingEnrollment != null
                && (existingEnrollment.getStatus() == EnrollmentStatus.ACTIVE
                || existingEnrollment.getStatus() == EnrollmentStatus.SUSPENDED);
        if (classEntity.getMaxStudents() != null && !alreadyOccupiesSeat) {
            long activeCount = classStudentRepository.countByClassEntity_ClassIdAndStatusIn(
                    classEntity.getClassId(), List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED));
            if (activeCount >= classEntity.getMaxStudents()) {
                throw new BadRequestException("Class capacity has been reached");
            }
        }
    }

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
