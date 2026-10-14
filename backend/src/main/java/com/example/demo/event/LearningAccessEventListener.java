package com.example.demo.event;

import com.example.demo.entity.AuditAction;
import com.example.demo.entity.LearningAccess;
import com.example.demo.entity.User;
import com.example.demo.repository.LearningAccessRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.service.AuditService;
import com.example.demo.entity.BillingCycle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lắng nghe PaymentConfirmedEvent và:
 *   1. Tạo LearningAccess cho user với product đã thanh toán.
 *   2. Publish LearningAccessGrantedEvent để các service khác consume.
 *
 * Chạy trong transaction mới (AFTER_COMMIT) để đảm bảo idempotency:
 * nếu LearningAccess đã tồn tại cho payment này thì bỏ qua.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningAccessEventListener {

    private final LearningAccessRepository learningAccessRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final PaymentRepository paymentRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentConfirmed(PaymentConfirmedEvent event) {
        // Idempotency guard — tránh cấp quyền trùng lặp nếu callback đến 2 lần
        if (learningAccessRepository.existsByPaymentId(event.paymentId())) {
            log.info("LearningAccess already granted for paymentId={}, skipping.", event.paymentId());
            return;
        }

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.warn("Cannot grant learning access: userId={} not found.", event.userId());
            return;
        }

        LearningAccess access = new LearningAccess();
        access.setUser(user);
        access.setProductId(event.productId());
        access.setPaymentId(event.paymentId());
        var payment = paymentRepository.findById(event.paymentId()).orElse(null);
        var plan = payment == null ? null : payment.getPlan();
        access.setAccessType(plan == null ? "PREMIUM" : plan.getCode());
        if (plan == null || plan.getBillingCycle() == BillingCycle.ONE_TIME) {
            access.setExpiresAt(null);
        } else {
            int days = plan.getDurationDays() != null
                    ? plan.getDurationDays()
                    : (plan.getBillingCycle() == BillingCycle.YEARLY ? 365 : 30);
            access.setExpiresAt(Instant.now().plus(days, ChronoUnit.DAYS));
        }

        LearningAccess saved = learningAccessRepository.save(access);
        log.info("LearningAccess granted: userId={}, productId={}, paymentId={}",
                event.userId(), event.productId(), event.paymentId());

        auditService.log(saved.getUser().getUserId(), AuditAction.PERMISSION_GRANTED, "LEARNING_ACCESS",
                String.valueOf(saved.getId()), "Granted access for product: " + saved.getProductId() + ", accessType: " + saved.getAccessType());

        // Publish event để các service khác (Course Service, v.v.) biết
        LearningAccessGrantedEvent grantedEvent = new LearningAccessGrantedEvent(
                saved.getUser().getUserId(),
                saved.getProductId(),
                saved.getPaymentId(),
                saved.getAccessType(),
                saved.getExpiresAt()
        );
        eventPublisher.publishEvent(grantedEvent);
    }
}
