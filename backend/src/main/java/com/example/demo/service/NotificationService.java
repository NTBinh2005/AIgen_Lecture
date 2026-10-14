package com.example.demo.service;

import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.NotificationType;
import java.util.List;

public interface NotificationService {

    NotificationResponse createNotification(Integer userId, String title, String message, NotificationType type, String referenceId);

    List<NotificationResponse> getMyNotifications(Integer userId);

    long getUnreadCount(Integer userId);

    NotificationResponse markAsRead(Integer userId, Long notificationId);

    void markAllAsRead(Integer userId);
}
