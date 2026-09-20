package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.ClassCreateRequest;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.request.ClassUpdateRequest;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassStatus;
import com.example.demo.entity.ClassTeacher;
import com.example.demo.entity.ClassTeacherAudit;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassTeacherAuditRepository;
import com.example.demo.repository.ClassTeacherRepository;
import com.example.demo.repository.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.demo.service.ClassService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClassServiceImpl implements ClassService {

    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassTeacherAuditRepository classTeacherAuditRepository;

    public ClassServiceImpl(
            ClassRepository classRepository,
            UserRepository userRepository,
            ClassTeacherRepository classTeacherRepository,
            ClassTeacherAuditRepository classTeacherAuditRepository
    ) {
        this.classRepository = classRepository;
        this.userRepository = userRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classTeacherAuditRepository = classTeacherAuditRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClassResponse> findAll() {
        return classRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClassResponse findById(Integer classId) {
        return toResponse(getClassEntity(classId));
    }

    /**
     * ENRL-01: Teacher xem danh sách lớp mình phụ trách
     * (bao gồm cả lớp là co-teacher).
     */
    @Transactional(readOnly = true)
    public List<ClassResponse> findByTeacher(Integer teacherId) {
        return classRepository.findByTeacher_UserId(teacherId)
                .stream().map(this::toResponse).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CLASS-AC-01: Tạo lớp mới với status DRAFT.
     * Exception: trùng classCode trong cùng kỳ → 409 ConflictException.
     */
    @Transactional
    public ClassResponse create(ClassCreateRequest request, Integer currentUserId) {
        User currentUser = getUser(currentUserId);
        User teacher = (request.teacherId() != null)
                ? getTeacher(request.teacherId())
                : currentUser;

        // Admin có thể tạo cho bất kỳ teacher nào; TEACHER chỉ tạo cho chính mình
        if (currentUser.getRole() == UserRole.TEACHER
                && !teacher.getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Teacher chỉ có thể tạo lớp cho chính mình");
        }

        String className = requireText(request.className(), "className");
        String classCode = requireText(request.classCode(), "classCode");
        String semester  = trimNullable(request.semester());

        // CLASS-BR exception: trùng classCode trong cùng kỳ → 409
        checkClassCodeUnique(classCode, semester, null);

        ClassEntity classEntity = new ClassEntity();
        classEntity.setTeacher(teacher);
        classEntity.setClassName(className);
        classEntity.setClassCode(classCode);
        classEntity.setSemester(semester);
        classEntity.setStartsAt(request.startsAt());
        classEntity.setEndsAt(request.endsAt());
        classEntity.setDescription(trimNullable(request.description()));
        classEntity.setStatus(ClassStatus.DRAFT); // CLASS-AC-01: luôn bắt đầu là DRAFT

        ClassEntity saved = classRepository.save(classEntity);

        // Gán co-teachers nếu có
        syncCoTeachers(saved, request.coTeacherIds());

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CLASS-BR-02: Chỉ teacher chính hoặc admin được update.
     * CLASS-BR-05: Đổi teacher chính → ghi ClassTeacherAudit.
     */
    @Transactional
    public ClassResponse update(Integer classId, ClassUpdateRequest request, Integer currentUserId) {
        ClassEntity classEntity = getClassEntity(classId);
        User currentUser = getUser(currentUserId);

        // CLASS-BR-02: kiểm tra quyền
        assertCanManage(classEntity, currentUser);

        // Resolve giá trị mới
        String className = request.className() == null
                ? classEntity.getClassName()
                : requireText(request.className(), "className");
        String classCode = request.classCode() == null
                ? classEntity.getClassCode()
                : requireText(request.classCode(), "classCode");
        String semester = request.semester() == null
                ? classEntity.getSemester()
                : trimNullable(request.semester());

        // Unique check nếu classCode hoặc semester thay đổi
        if (!classCode.equals(classEntity.getClassCode())
                || !java.util.Objects.equals(semester, classEntity.getSemester())) {
            checkClassCodeUnique(classCode, semester, classId);
        }

        // CLASS-BR-05: đổi teacher chính → ghi audit
        if (request.teacherId() != null
                && !request.teacherId().equals(classEntity.getTeacher().getUserId())) {
            User newTeacher = getTeacher(request.teacherId());
            recordTeacherAudit(classEntity, classEntity.getTeacher(), newTeacher, currentUser);
            classEntity.setTeacher(newTeacher);
        }

        classEntity.setClassName(className);
        classEntity.setClassCode(classCode);
        classEntity.setSemester(semester);

        if (request.startsAt() != null) classEntity.setStartsAt(request.startsAt());
        if (request.endsAt()   != null) classEntity.setEndsAt(request.endsAt());
        if (request.description() != null) classEntity.setDescription(trimNullable(request.description()));

        // Không cho phép thay đổi status qua update thông thường
        // (dùng activate() / close() thay thế)
        if (request.status() != null && request.status() != classEntity.getStatus()) {
            throw new BadRequestException(
                    "Sử dụng endpoint /activate hoặc /close để thay đổi trạng thái lớp");
        }

        // Sync co-teachers nếu request có danh sách mới
        if (request.coTeacherIds() != null) {
            syncCoTeachers(classEntity, request.coTeacherIds());
        }

        return toResponse(classEntity);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACTIVATE & CLOSE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CLASS-AC-01: Kích hoạt lớp.
     * Yêu cầu className, classCode, startsAt phải có giá trị.
     */
    @Transactional
    public ClassResponse activate(Integer classId, Integer currentUserId) {
        ClassEntity classEntity = getClassEntity(classId);
        User currentUser = getUser(currentUserId);
        assertCanManage(classEntity, currentUser);

        if (classEntity.getStatus() != ClassStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể kích hoạt lớp đang ở trạng thái DRAFT");
        }
        // CLASS-AC-01: phải đầy đủ thông tin
        if (!StringUtils.hasText(classEntity.getClassName())) {
            throw new BadRequestException("Lớp phải có tên (className) trước khi kích hoạt");
        }
        if (!StringUtils.hasText(classEntity.getClassCode())) {
            throw new BadRequestException("Lớp phải có mã lớp (classCode) trước khi kích hoạt");
        }
        if (classEntity.getStartsAt() == null) {
            throw new BadRequestException("Lớp phải có thời gian bắt đầu (startsAt) trước khi kích hoạt");
        }

        classEntity.setStatus(ClassStatus.ACTIVE);
        return toResponse(classEntity);
    }

    /**
     * CLASS-BR-04 + Exception: Đóng lớp ACTIVE → CLOSED.
     * CLASS-AC-03: Giữ nguyên toàn bộ dữ liệu (bài giảng, điểm, điểm danh).
     *
     * TODO: Kiểm tra live session đang chạy khi entity LiveSession được tạo.
     */
    @Transactional
    public ClassResponse close(Integer classId, Integer currentUserId) {
        ClassEntity classEntity = getClassEntity(classId);
        User currentUser = getUser(currentUserId);
        assertCanManage(classEntity, currentUser);

        if (classEntity.getStatus() == ClassStatus.CLOSED) {
            throw new BadRequestException("Lớp đã được đóng trước đó");
        }
        if (classEntity.getStatus() == ClassStatus.DRAFT) {
            throw new BadRequestException("Không thể đóng lớp đang ở trạng thái DRAFT. Hãy kích hoạt lớp trước.");
        }

        // TODO CLASS-BR exception: kiểm tra live session đang live
        // if (liveSessionRepository.existsByClassEntity_ClassIdAndStatus(classId, LiveStatus.LIVE)) {
        //     throw new BadRequestException("Không thể đóng lớp khi có live session đang diễn ra");
        // }

        classEntity.setStatus(ClassStatus.CLOSED);
        // CLASS-AC-03: không xóa bất kỳ dữ liệu nào — chỉ đổi status
        return toResponse(classEntity);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEACTIVATE (legacy)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void deactivate(Integer classId) {
        // Legacy: đóng lớp không cần kiểm tra ownership
        ClassEntity classEntity = getClassEntity(classId);
        classEntity.setStatus(ClassStatus.CLOSED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    ClassEntity getClassEntity(Integer classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    /**
     * Kiểm tra quyền quản lý lớp.
     * CLASS-BR-02: Teacher chỉ sửa lớp mình phụ trách (chính hoặc co-teacher); Admin có toàn quyền.
     */
    private void assertCanManage(ClassEntity classEntity, User user) {
        if (user.getRole() == UserRole.ADMIN) return;
        if (user.getRole() == UserRole.TEACHER) {
            boolean isMainTeacher = classEntity.getTeacher().getUserId().equals(user.getUserId());
            boolean isCoTeacher   = classTeacherRepository
                    .existsByClassEntity_ClassIdAndTeacher_UserId(classEntity.getClassId(), user.getUserId());
            if (isMainTeacher || isCoTeacher) return;
        }
        throw new AccessDeniedException("Bạn không có quyền quản lý lớp học này");
    }

    /**
     * Exception: trùng classCode trong cùng kỳ → 409.
     * Nếu semester null thì check toàn cục (null semester scope).
     */
    private void checkClassCodeUnique(String classCode, String semester, Integer excludeClassId) {
        boolean exists;
        if (semester != null) {
            exists = (excludeClassId == null)
                    ? classRepository.existsByClassCodeAndSemester(classCode, semester)
                    : classRepository.existsByClassCodeAndSemesterAndClassIdNot(classCode, semester, excludeClassId);
        } else {
            exists = (excludeClassId == null)
                    ? classRepository.existsByClassCodeAndSemesterIsNull(classCode)
                    : classRepository.existsByClassCodeAndSemesterIsNullAndClassIdNot(classCode, excludeClassId);
        }
        if (exists) {
            throw new ConflictException(
                    "Mã lớp '" + classCode + "' đã tồn tại trong kỳ học này (409 Conflict)");
        }
    }

    /**
     * CLASS-BR-05: Ghi audit log khi thay đổi teacher chính.
     */
    private void recordTeacherAudit(ClassEntity classEntity, User previous, User next, User changedBy) {
        ClassTeacherAudit audit = new ClassTeacherAudit();
        audit.setClassEntity(classEntity);
        audit.setPreviousTeacher(previous);
        audit.setNewTeacher(next);
        audit.setChangedBy(changedBy);
        classTeacherAuditRepository.save(audit);
    }

    /**
     * CLASS-BR-01: Đồng bộ danh sách co-teacher.
     * Thêm mới những người chưa có, giữ nguyên những người đã có,
     * xóa những người bị loại khỏi danh sách mới.
     */
    @Transactional
    void syncCoTeachers(ClassEntity classEntity, List<Integer> newCoTeacherIds) {
        final List<Integer> effectiveCoTeacherIds =
                (newCoTeacherIds == null) ? Collections.emptyList() : newCoTeacherIds;

        List<ClassTeacher> existing = classTeacherRepository
                .findByClassEntity_ClassId(classEntity.getClassId());
        Set<Integer> existingIds = existing.stream()
                .map(ct -> ct.getTeacher().getUserId())
                .collect(Collectors.toSet());

        // Tìm id cần xóa (có trong DB nhưng không có trong list mới)
        List<Integer> toRemove = existing.stream()
                .map(ct -> ct.getTeacher().getUserId())
                .filter(id -> !effectiveCoTeacherIds.contains(id))
                .collect(Collectors.toList());
        if (!toRemove.isEmpty()) {
            classTeacherRepository.deleteByClassEntity_ClassIdAndTeacher_UserIdIn(
                    classEntity.getClassId(), toRemove);
        }

        // Thêm id chưa có trong DB
        List<ClassTeacher> toAdd = new ArrayList<>();
        for (Integer coTeacherId : effectiveCoTeacherIds) {
            if (existingIds.contains(coTeacherId)) continue; // đã có, bỏ qua
            User coTeacher = getTeacher(coTeacherId);
            // Không cho phép teacher chính đồng thời là co-teacher
            if (coTeacher.getUserId().equals(classEntity.getTeacher().getUserId())) continue;
            ClassTeacher ct = new ClassTeacher();
            ct.setClassEntity(classEntity);
            ct.setTeacher(coTeacher);
            toAdd.add(ct);
        }
        if (!toAdd.isEmpty()) {
            classTeacherRepository.saveAll(toAdd);
        }
    }

    /** Map entity → response đầy đủ (bao gồm semester, startsAt, endsAt, coTeacherIds) */
    private ClassResponse toResponse(ClassEntity classEntity) {
        User teacher = classEntity.getTeacher();
        List<Integer> coTeacherIds = classTeacherRepository
                .findByClassEntity_ClassId(classEntity.getClassId())
                .stream()
                .map(ct -> ct.getTeacher().getUserId())
                .toList();

        return new ClassResponse(
                classEntity.getClassId(),
                teacher.getUserId(),
                teacher.getName(),
                classEntity.getClassName(),
                classEntity.getClassCode(),
                classEntity.getSemester(),
                classEntity.getStartsAt(),
                classEntity.getEndsAt(),
                classEntity.getDescription(),
                classEntity.getStatus(),
                classEntity.getCreatedAt(),
                coTeacherIds
        );
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User getTeacher(Integer teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BadRequestException("User không phải là giáo viên: " + teacherId);
        }
        return teacher;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " không được để trống");
        }
        return value.trim();
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}
