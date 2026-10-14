package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationType;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponse createNotification(Integer userId, String title, String message,
                                                   NotificationType type, String referenceId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification for userId={}, type={}, referenceId={}", userId, type, referenceId);

        // Gửi email thông báo song song nếu user có địa chỉ email hợp lệ
        if (userId != null) {
            try {
                userRepository.findById(userId).ifPresent(user -> {
                    if (StringUtils.hasText(user.getEmail()) && !user.getEmail().endsWith("@phone.local")) {
                        emailService.sendEmail(user.getEmail(), title, message);
                    }
                });
            } catch (Exception ex) {
                log.warn("Could not dispatch email notification for userId={}: {}", userId, ex.getMessage());
            }
        }

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(Integer userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Integer userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Integer userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getReferenceId(),
                notification.getCreatedAt()
        );
    }
}
