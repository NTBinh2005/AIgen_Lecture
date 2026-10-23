package com.example.demo.service;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.ClassPresentationResponse;
import com.example.demo.dto.response.PresentationVersionResponse;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassPresentation;
import com.example.demo.entity.ClassStatus;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Presentation;
import com.example.demo.entity.PresentationStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.repository.ClassPresentationRepository;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.PresentationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Luồng bản trình chiếu giống bài giảng: giáo viên giao bản đã xuất bản cho lớp,
 * học sinh ghi danh ACTIVE xem được phiên bản đã xuất bản (chỉ HTML trình chiếu, không kèm ghi chú).
 */
@Service
@RequiredArgsConstructor
public class ClassPresentationService {
    private final ClassRepository classRepository;
    private final PresentationRepository presentationRepository;
    private final ClassPresentationRepository classPresentationRepository;
    private final ClassAccessService classAccessService;
    private final PresentationService presentationService;
    private final PresentationRenderService renderService;
    private final PremiumAccessService premiumAccessService;
    private final StudyNotificationService studyNotifications;
    private final PresentationRenderWarmer renderWarmer;

    @Transactional
    public ClassPresentationResponse assign(Integer classId, UUID presentationId, Integer currentUserId) {
        ClassEntity classEntity = getClass(classId);
        classAccessService.assertCanManage(classEntity, currentUserId);
        if (classEntity.getStatus() != ClassStatus.ACTIVE) {
            throw new BadRequestException("Chỉ giao được bản trình chiếu cho lớp đang hoạt động");
        }
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản trình chiếu"));
        User actor = classAccessService.requireUser(currentUserId);
        if (actor.getRole() != UserRole.ADMIN && !presentation.getOwnerId().equals(currentUserId)) {
            throw new AccessDeniedException("Bạn không thể giao bản trình chiếu của giáo viên khác");
        }
        if (!isVisibleToStudents(presentation)) {
            throw new BadRequestException("Chỉ giao được bản trình chiếu đã xuất bản");
        }
        ClassPresentation assignment = classPresentationRepository
                .findByClassEntity_ClassIdAndPresentation_PresentationId(classId, presentationId)
                .orElseGet(() -> {
                    ClassPresentation value = new ClassPresentation();
                    value.setClassEntity(classEntity);
                    value.setPresentation(presentation);
                    ClassPresentation saved = classPresentationRepository.save(value);
                    // Dựng sẵn phiên bản học sinh sẽ xem, để vẫn xem được khi slide-ai-service tắt
                    renderWarmer.warm(publishedVersion(presentation), presentation.getTemplateId());
                    studyNotifications.notifyClass(classId, "Bản trình chiếu mới",
                            "Lớp có bản trình chiếu: " + presentation.getTitle(),
                            NotificationType.PRESENTATION_ASSIGNED, "PRESENTATION", presentationId);
                    return saved;
                });
        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<ClassPresentationResponse> findByClass(Integer classId, Integer currentUserId) {
        ClassEntity classEntity = getClass(classId);
        classAccessService.assertCanView(classEntity, currentUserId);
        return classPresentationRepository.findByClassEntity_ClassIdOrderByAssignedAtDesc(classId).stream()
                .filter(link -> isVisibleToStudents(link.getPresentation()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void unassign(Integer classId, UUID presentationId, Integer currentUserId) {
        ClassEntity classEntity = getClass(classId);
        classAccessService.assertCanManage(classEntity, currentUserId);
        ClassPresentation assignment = classPresentationRepository
                .findByClassEntity_ClassIdAndPresentation_PresentationId(classId, presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Bản trình chiếu chưa được giao cho lớp này"));
        classPresentationRepository.delete(assignment);
    }

    /** Thông tin bản trình chiếu cho học sinh (không kèm nội dung slide/ghi chú). */
    @Transactional(readOnly = true)
    public ClassPresentationResponse getForStudent(UUID presentationId, Integer studentId) {
        Presentation presentation = requireStudentGrant(presentationId, studentId);
        PresentationVersionResponse version = publishedVersion(presentation);
        return new ClassPresentationResponse(
                presentation.getPresentationId(), version.title(), presentation.getTemplateId(),
                version.slides().size(), presentation.getPublishedAt(), null);
    }

    /** HTML trình chiếu của phiên bản đã xuất bản. */
    @Transactional(readOnly = true)
    public String renderForStudent(UUID presentationId, Integer studentId) {
        Presentation presentation = requireStudentGrant(presentationId, studentId);
        return renderService.renderVersion(publishedVersion(presentation), presentation.getTemplateId());
    }

    private Presentation requireStudentGrant(UUID presentationId, Integer studentId) {
        premiumAccessService.requireActivePlan(studentId);
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản trình chiếu"));
        if (!isVisibleToStudents(presentation)
                || !classPresentationRepository.existsActiveEnrollmentGrant(studentId, presentationId)) {
            throw new AccessDeniedException("Bản trình chiếu chưa được giao cho lớp bạn đang học");
        }
        return presentation;
    }

    private PresentationVersionResponse publishedVersion(Presentation presentation) {
        return presentationService.publishedVersionContract(presentation.getPublishedVersionId());
    }

    /** Đã xuất bản và chưa lưu trữ (giống điều kiện bài giảng: publishedAt != null). */
    private static boolean isVisibleToStudents(Presentation presentation) {
        return presentation.getPublishedVersionId() != null
                && presentation.getArchivedAt() == null
                && presentation.getStatus() != PresentationStatus.ARCHIVED;
    }

    private ClassPresentationResponse toResponse(ClassPresentation link) {
        Presentation presentation = link.getPresentation();
        PresentationVersionResponse version = publishedVersion(presentation);
        return new ClassPresentationResponse(
                presentation.getPresentationId(), version.title(), presentation.getTemplateId(),
                version.slides().size(), presentation.getPublishedAt(), link.getAssignedAt());
    }

    private ClassEntity getClass(Integer classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp: " + classId));
    }
}
