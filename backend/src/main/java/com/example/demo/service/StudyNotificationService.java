package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;

@Service @RequiredArgsConstructor
public class StudyNotificationService {
    private final ApplicationEventPublisher publisher;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ClassStudentRepository enrollments;
    private final NotificationStreamService streamService;

    public record Notice(Integer userId, String title, String message, NotificationType type, String reference) {}

    public void notify(Integer userId, String title, String message, NotificationType type, String target, Object id) {
        publisher.publishEvent(new Notice(userId, title, message, type, target + ":" + id));
    }

    public void notifyClass(Integer classId, String title, String message, NotificationType type, String target, Object id) {
        for (ClassStudent member : enrollments.findByClassEntity_ClassId(classId)) {
            if (member.getStatus() == EnrollmentStatus.ACTIVE)
                notify(member.getStudent().getUserId(), title, message, type, target, id);
        }
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void store(Notice notice) {
        if (users.lockByUserId(notice.userId()).isEmpty()) return;
        if (notifications.existsByUserIdAndTypeAndReferenceId(notice.userId(), notice.type(), notice.reference())) return;
        Notification notification = new Notification();
        notification.setUserId(notice.userId()); notification.setTitle(notice.title());
        notification.setMessage(notice.message()); notification.setType(notice.type());
        notification.setReferenceId(notice.reference()); Notification saved = notifications.save(notification);
        streamService.afterCommit(notice.userId(), new com.example.demo.dto.response.NotificationResponse(
                saved.getId(), saved.getUserId(), saved.getTitle(), saved.getMessage(), saved.getType(), saved.isRead(), saved.getReferenceId(), saved.getCreatedAt()));
    }
}
