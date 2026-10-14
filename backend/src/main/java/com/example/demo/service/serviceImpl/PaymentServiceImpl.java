package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.request.PaymentWebhookRequest;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.entity.AuditAction;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentMethod;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.User;
import com.example.demo.event.PaymentConfirmedEvent;
import com.example.demo.gateway.PaymentGateway;
import com.example.demo.gateway.PaymentGatewayFactory;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.SubscriptionPlanRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuditService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PaymentGatewayFactory gatewayFactory;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final com.example.demo.service.SubscriptionService subscriptionService;

    @Override
    @Transactional
    public PaymentResponse createPayment(Integer userId, PaymentCreateRequest request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription plan not found: " + request.planId()));
        if (!plan.isActive()) {
            throw new BadRequestException("Subscription plan is not active");
        }
        if (plan.getTargetRole() != user.getRole()) {
            throw new BadRequestException("Subscription plan is not available for your role");
        }

        if (plan.getBillingCycle() != com.example.demo.entity.BillingCycle.ONE_TIME
                && subscriptionService.getMySubscriptions(userId).stream().anyMatch(s ->
                s.status() == com.example.demo.entity.SubscriptionStatus.ACTIVE && s.expiresAt() == null)) {
            throw new BadRequestException("Bạn đã có gói vĩnh viễn; không thể thay bằng gói có thời hạn");
        }
        String transactionId = UUID.randomUUID().toString();

        // Sinh URL thanh toán từ gateway được chọn (VNPay hoặc MoMo)
        PaymentGateway gateway = gatewayFactory.getGateway(request.paymentMethod());
        String paymentUrl;
        try {
            paymentUrl = gateway.createPaymentUrl(
                    transactionId,
                    plan.getPrice(),
                    request.orderInfo() == null || request.orderInfo().isBlank()
                            ? "Gói " + plan.getName() : request.orderInfo(),
                    request.returnUrl(),
                    ipAddress
            );
        } catch (Exception ex) {
            log.error("[Payment] Failed to create payment URL for method={}: {}", request.paymentMethod(), ex.getMessage());
            throw new BadRequestException("Không thể tạo liên kết thanh toán: " + ex.getMessage());
        }

        Payment payment = new Payment();
        payment.setTransactionId(transactionId);
        payment.setUser(user);
        payment.setProductId(plan.getPlanId());
        payment.setPlan(plan);
        payment.setPlanSnapshotJson(subscriptionService.snapshotPlan(plan));
        payment.setAmount(plan.getPrice());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(request.paymentMethod());
        payment.setPaymentUrl(paymentUrl);
        payment.setOrderInfo(request.orderInfo() == null || request.orderInfo().isBlank()
                ? "Gói " + plan.getName() : request.orderInfo());

        Payment saved = paymentRepository.save(payment);

        auditService.log(userId, AuditAction.PAYMENT_CREATED, "PAYMENT",
                String.valueOf(saved.getPaymentId()),
                "Payment created via " + request.paymentMethod() + " for product: "
                        + saved.getProductId() + ", amount: " + saved.getAmount());

        log.info("[Payment] Created paymentId={}, method={}, txn={}", saved.getPaymentId(), request.paymentMethod(), transactionId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Integer userId, Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (actor.getRole() != com.example.demo.entity.UserRole.ADMIN
                && !payment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to this payment");
        }

        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(Integer userId) {
        return paymentRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Integer userId, Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!payment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to this payment");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Only PENDING payments can be cancelled");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    // ── Gateway-specific webhook (VNPay / MoMo) ───────────────────────────────

    @Override
    @Transactional
    public void handleGatewayCallback(PaymentMethod method, Map<String, String> params) {
        PaymentGateway gateway = gatewayFactory.getGateway(method);

        // 1. Xác thực chữ ký
        if (!gateway.verifyCallback(params)) {
            log.warn("[Payment] Invalid signature from {}: params={}", method, params);
            throw new BadRequestException("Chữ ký không hợp lệ từ " + method);
        }

        String transactionId = gateway.extractTransactionId(params);
        String status = gateway.extractStatus(params);

        Payment payment = paymentRepository.lockByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (payment.getPaymentMethod() != method) {
            throw new BadRequestException("Cổng thanh toán không khớp với giao dịch");
        }

        // 2. Idempotency guard — chỉ xử lý payment còn PENDING (không "sống lại" payment đã huỷ)
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("[Payment] Already processed txn={} (status={}), skipping.", transactionId, payment.getStatus());
            return;
        }

        // 3. Đối chiếu số tiền để chống giả mạo/sai lệch
        BigDecimal paidAmount = gateway.extractAmount(params);
        if ("SUCCESS".equalsIgnoreCase(status)
                && (paidAmount == null || payment.getAmount().compareTo(paidAmount) != 0)) {
            log.warn("[Payment] Amount mismatch txn={}: expected={}, got={}", transactionId, payment.getAmount(), paidAmount);
            throw new BadRequestException("Số tiền không khớp với giao dịch");
        }

        // 4. Cập nhật trạng thái
        if ("SUCCESS".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            subscriptionService.activateFromPayment(payment);

            auditService.log(payment.getUser().getUserId(), AuditAction.PAYMENT_SUCCESS, "PAYMENT",
                    String.valueOf(payment.getPaymentId()),
                    "Payment confirmed via " + method + " for amount: " + payment.getAmount());

            // 4. Phát event — Invoice + LearningAccess + Notification sẽ được xử lý async
            PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                    payment.getPaymentId(),
                    payment.getTransactionId(),
                    payment.getUser().getUserId(),
                    payment.getProductId(),
                    payment.getAmount()
            );
            eventPublisher.publishEvent(event);
            log.info("[Payment] PaymentConfirmedEvent published for txn={}", transactionId);

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            auditService.log(payment.getUser().getUserId(), AuditAction.PAYMENT_FAILED, "PAYMENT",
                    String.valueOf(payment.getPaymentId()),
                    "Payment failed via " + method + " callback");

            notificationService.createNotification(
                    payment.getUser().getUserId(),
                    "Thanh toán thất bại",
                    String.format("Giao dịch %s thanh toán không thành công qua %s.",
                            payment.getTransactionId(), method),
                    NotificationType.PAYMENT_FAILED,
                    String.valueOf(payment.getPaymentId())
            );
        }
    }

    // ── Legacy webhook (backward compatible) ──────────────────────────────────

    @Override
    @Transactional
    public void handleWebhook(PaymentWebhookRequest request) {
        Payment payment = paymentRepository.findByTransactionId(request.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        // Idempotency check
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            if (payment.getAmount().compareTo(request.amount()) != 0) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                throw new BadRequestException("Amount mismatch in payment webhook");
            }

            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);
            subscriptionService.activateFromPayment(payment);

            auditService.log(payment.getUser().getUserId(), AuditAction.PAYMENT_SUCCESS, "PAYMENT",
                    String.valueOf(payment.getPaymentId()), "Payment confirmed for amount: " + payment.getAmount());

            PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                    payment.getPaymentId(),
                    payment.getTransactionId(),
                    payment.getUser().getUserId(),
                    payment.getProductId(),
                    payment.getAmount()
            );
            eventPublisher.publishEvent(event);

        } else if ("FAILED".equalsIgnoreCase(request.status())) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            auditService.log(payment.getUser().getUserId(), AuditAction.PAYMENT_FAILED, "PAYMENT",
                    String.valueOf(payment.getPaymentId()), "Payment failed via webhook callback");

            notificationService.createNotification(
                    payment.getUser().getUserId(),
                    "Thanh toán thất bại",
                    String.format("Giao dịch %s thanh toán không thành công.", payment.getTransactionId()),
                    NotificationType.PAYMENT_FAILED,
                    String.valueOf(payment.getPaymentId())
            );
        } else {
            throw new BadRequestException("Unknown status in webhook");
        }
    }

    // ── Mapper ─────────────────────────────────────────────────────────────────

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getTransactionId(),
                payment.getUser().getUserId(),
                payment.getProductId(),
                payment.getPlan() == null ? null : payment.getPlan().getPlanId(),
                payment.getPlan() == null ? null : payment.getPlan().getName(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getPaymentUrl(),
                payment.getOrderInfo(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
