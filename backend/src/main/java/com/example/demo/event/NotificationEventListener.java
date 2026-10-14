package com.example.demo.event;

import com.example.demo.entity.NotificationType;
import com.example.demo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lắng nghe các domain events để tự động phát sinh thông báo in-app.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentConfirmed(PaymentConfirmedEvent event) {
        try {
            String title = "Thanh toán thành công";
            String message = String.format("Giao dịch %s đã thanh toán thành công số tiền %s cho sản phẩm #%d",
                    event.transactionId(), event.amount(), event.productId());

            notificationService.createNotification(
                    event.userId(),
                    title,
                    message,
                    NotificationType.PAYMENT_SUCCESS,
                    String.valueOf(event.paymentId())
            );
        } catch (Exception ex) {
            log.error("Failed to send notification for PaymentConfirmedEvent", ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLearningAccessGranted(LearningAccessGrantedEvent event) {
        try {
            String title = "Quyền học đã được kích hoạt";
            String message = String.format("Bạn đã được cấp quyền học thành công cho sản phẩm #%d (loại: %s)",
                    event.productId(), event.accessType());

            notificationService.createNotification(
                    event.userId(),
                    title,
                    message,
                    NotificationType.LEARNING_ACCESS_GRANTED,
                    String.valueOf(event.productId())
            );
        } catch (Exception ex) {
            log.error("Failed to send notification for LearningAccessGrantedEvent", ex);
        }
    }
}
