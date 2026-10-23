package com.example.demo.service;

import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.NotificationType;
import java.util.List;

public interface NotificationService {

    NotificationResponse createNotification(Integer userId, String title, String message, NotificationType type, String referenceId);

    org.springframework.data.domain.Page<NotificationResponse> getMyNotifications(
            Integer userId, org.springframework.data.domain.Pageable pageable);

    long getUnreadCount(Integer userId);

    NotificationResponse markAsRead(Integer userId, Long notificationId);

    void markAllAsRead(Integer userId);
}
