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
    private final com.example.demo.service.StudyNotificationService studyNotifications;
    private final com.example.demo.repository.ClassOrderRepository classOrderRepository;

    public EnrollmentServiceImpl(
            ClassStudentRepository classStudentRepository,
            ClassRepository classRepository,
            UserRepository userRepository,
            ClassAccessService classAccessService,
            com.example.demo.service.StudyNotificationService studyNotifications,
            com.example.demo.repository.ClassOrderRepository classOrderRepository
    ) {
        this.classStudentRepository = classStudentRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.classAccessService = classAccessService;
        this.studyNotifications = studyNotifications;
        this.classOrderRepository = classOrderRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /** ENRL-01: Admin/Teacher xem tất cả */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<EnrollmentResponse> findAll(
            EnrollmentStatus status, org.springframework.data.domain.Pageable pageable) {
        return (status == null ? classStudentRepository.findAll(pageable)
                : classStudentRepository.findByStatus(status, pageable)).map(this::toResponse);
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
            throw new ResourceNotFoundException("Không tìm thấy học sinh: " + studentId);
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
            throw new ResourceNotFoundException("Không tìm thấy học sinh: " + studentId);
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
        ClassEntity classEntity = classRepository.lockByClassId(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));

        classAccessService.assertCanManage(classEntity, currentUserId);

        // CLASS-BR-04: lớp CLOSED không nhận enrollment mới
        if (classEntity.getStatus() == com.example.demo.entity.ClassStatus.DRAFT) {
            throw new BadRequestException("Lớp chưa được kích hoạt");
        } else if (classEntity.getStatus() != com.example.demo.entity.ClassStatus.ACTIVE) {
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
            enrollment.setEnrolledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
            return toResponse(enrollment);
        }

        ClassStudent newEnrollment = new ClassStudent();
        newEnrollment.setId(enrollmentId);
        newEnrollment.setClassEntity(classEntity);
        newEnrollment.setStudent(student);
        newEnrollment.setStatus(EnrollmentStatus.ACTIVE);
        newEnrollment.setEnrolledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));

        ClassStudent saved = classStudentRepository.save(newEnrollment);
        studyNotifications.notify(saved.getStudent().getUserId(), "Đã tham gia lớp", "Bạn đã được thêm vào lớp " + saved.getClassEntity().getClassName(),
                com.example.demo.entity.NotificationType.CLASS_ENROLLED, "CLASS", saved.getClassEntity().getClassId());
        return toResponse(saved);
    }

    /** ENRL-04: Cập nhật trạng thái enrollment */
    @Transactional
    public List<BulkEnrollmentItemResponse> enrollBulk(Integer classId, List<Integer> studentIds,
                                                        Integer currentUserId) {
        ClassEntity classEntity = classRepository.lockByClassId(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp"));
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
        ClassEntity classroom = classRepository.lockByClassId(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp"));
        classAccessService.assertCanManage(classroom, currentUserId);
        ClassStudent enrollment = getEnrollment(classId, studentId);
        if ((request.status() == EnrollmentStatus.ACTIVE || request.status() == EnrollmentStatus.SUSPENDED)
                && enrollment.getStatus() != EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.SUSPENDED) {
            if (classroom.getStatus() != ClassStatus.ACTIVE) {
                throw new BadRequestException("Lớp đã đóng hoặc chưa được kích hoạt");
            }
            getStudent(studentId);
            validateEnrollmentWindowAndCapacity(classroom, enrollment);
        }
        if (request.status() == EnrollmentStatus.COMPLETED) {
            LocalDateTime classEndsAt = enrollment.getClassEntity().getEndsAt();
            if (classEndsAt == null || LocalDateTime.now(java.time.ZoneOffset.UTC).isBefore(classEndsAt)) {
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
        ClassEntity classEntity = classRepository.lockByClassCode(classCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lớp với mã: " + classCode));

        // Chỉ cho phép enroll lớp ACTIVE
        if (classEntity.getStatus() == com.example.demo.entity.ClassStatus.DRAFT) {
            throw new BadRequestException("Lớp chưa được kích hoạt");
        } else if (classEntity.getStatus() != ClassStatus.ACTIVE) {
            throw new BadRequestException(
                    "Lớp không ở trạng thái ACTIVE, không thể tự đăng ký");
        }

        if (classEntity.getPrice() != null && classEntity.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            boolean hasOrder = classOrderRepository.existsByStudentUserIdAndClassEntityClassIdAndStatus(
                    studentId, classEntity.getClassId(), com.example.demo.entity.ClassOrderStatus.ACTIVE);
            if (!hasOrder) {
                throw new BadRequestException("Lớp học có học phí (" + classEntity.getPrice() + " VNĐ). Vui lòng thanh toán học phí để tham gia lớp.");
            }
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
            existing.setEnrolledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
            return toResponse(existing);
        }

        ClassStudent newEnrollment = new ClassStudent();
        newEnrollment.setId(enrollmentId);
        newEnrollment.setClassEntity(classEntity);
        newEnrollment.setStudent(student);
        newEnrollment.setStatus(EnrollmentStatus.ACTIVE);
        newEnrollment.setEnrolledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));

        ClassStudent saved = classStudentRepository.save(newEnrollment);
        studyNotifications.notify(saved.getStudent().getUserId(), "Đã tham gia lớp", "Bạn đã được thêm vào lớp " + saved.getClassEntity().getClassName(),
                com.example.demo.entity.NotificationType.CLASS_ENROLLED, "CLASS", saved.getClassEntity().getClassId());
        studyNotifications.notify(saved.getClassEntity().getTeacher().getUserId(), "Học sinh mới tham gia lớp",
                saved.getStudent().getName() + " đã tham gia lớp bằng mã.", com.example.demo.entity.NotificationType.CLASS_JOINED,
                "CLASS", saved.getClassEntity().getClassId() + "/students/" + studentId);
        return toResponse(saved);
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
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh: " + studentId));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Người dùng không phải là học viên: " + studentId);
        }
        if (student.getStatus() != com.example.demo.entity.UserStatus.ACTIVE) {
            throw new BadRequestException("Không thể ghi danh học sinh đã bị vô hiệu hóa");
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
