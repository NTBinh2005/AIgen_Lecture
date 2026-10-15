package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.request.PaymentWebhookRequest;
import com.example.demo.dto.request.SepayWebhookRequest;
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
import com.example.demo.gateway.SepayGateway;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.ProcessedWebhookEventRepository;
import com.example.demo.repository.SubscriptionPlanRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuditService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.PaymentService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final SecureRandom PAYMENT_CODE_RANDOM = new SecureRandom();

    @Value("${sepay.payment-code-prefix:DH}")
    private String sepayPaymentCodePrefix;

    @Value("${sepay.pending-expiration-minutes:15}")
    private long pendingExpirationMinutes;

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PaymentGatewayFactory gatewayFactory;
    private final SepayGateway sepayGateway;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final com.example.demo.service.SubscriptionService subscriptionService;

    @Override
    @Transactional
    public PaymentResponse createPayment(Integer userId, PaymentCreateRequest request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        var plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy gói đăng ký: " + request.planId()));
        if (!plan.isActive()) {
            throw new BadRequestException("Gói đăng ký hiện không hoạt động");
        }
        if (plan.getTargetRole() != user.getRole()) {
            throw new BadRequestException("Gói đăng ký không áp dụng cho vai trò của bạn");
        }

        if (plan.getBillingCycle() != com.example.demo.entity.BillingCycle.ONE_TIME
                && subscriptionService.getMySubscriptions(userId).stream().anyMatch(s ->
                s.status() == com.example.demo.entity.SubscriptionStatus.ACTIVE && s.expiresAt() == null)) {
            throw new BadRequestException("Bạn đã có gói vĩnh viễn; không thể thay bằng gói có thời hạn");
        }
        if (request.paymentMethod() != PaymentMethod.SEPAY) {
            throw new BadRequestException("Phương thức thanh toán không còn được hỗ trợ; vui lòng dùng SEPAY");
        }
        String transactionId = generatePaymentCode();

        // Sinh VietQR chứa đúng số tiền và mã thanh toán để SePay đối soát webhook.
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
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
    public org.springframework.data.domain.Page<PaymentResponse> getAllPayments(
            org.springframework.data.domain.Pageable pageable) {
        return paymentRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Integer userId, Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));

        if (!payment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to this payment");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy giao dịch đang chờ thanh toán");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    /** Tự động đóng QR đã chờ quá lâu; khóa hàng để không đua với webhook. */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${sepay.pending-expiration-sweep-ms:60000}")
    @Transactional
    public void expireStalePendingPayments() {
        if (pendingExpirationMinutes < 1) {
            log.warn("[SePay] Bỏ qua tác vụ hết hạn vì thời gian cấu hình nhỏ hơn 1 phút");
            return;
        }
        Instant cutoff = Instant.now().minus(pendingExpirationMinutes, ChronoUnit.MINUTES);
        List<Payment> expired = paymentRepository.lockPendingCreatedBefore(cutoff);
        expired.forEach(payment -> payment.setStatus(PaymentStatus.CANCELLED));
        if (!expired.isEmpty()) {
            log.info("[SePay] Đã tự động hủy {} giao dịch chờ quá hạn", expired.size());
        }
    }

    // ── SePay webhook ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void handleSepayWebhook(String rawBody, String signature, String timestamp) {
        SepayWebhookRequest webhook = sepayGateway.verifyAndParseWebhook(rawBody, signature, timestamp);
        if (webhook.id() == null || webhook.code() == null || webhook.code().isBlank()
                || webhook.transferAmount() == null) {
            throw new BadRequestException("Webhook SePay thiếu id, code hoặc transferAmount");
        }
        if (!"in".equalsIgnoreCase(webhook.transferType())) {
            throw new BadRequestException("Chỉ chấp nhận giao dịch tiền vào từ SePay");
        }
        if (!sepayGateway.matchesConfiguredAccount(webhook.accountNumber())) {
            throw new BadRequestException("Tài khoản nhận tiền trong webhook SePay không khớp cấu hình");
        }

        String transactionId = webhook.code().trim().toUpperCase(java.util.Locale.ROOT);
        Payment payment = paymentRepository.lockByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch: " + transactionId));
        if (payment.getPaymentMethod() != PaymentMethod.SEPAY) {
            throw new BadRequestException("Cổng thanh toán không khớp với giao dịch");
        }

        String eventId = "SEPAY:" + webhook.id();
        if (processedWebhookEventRepository.existsById(eventId)) {
            log.info("[SePay] Duplicate webhook ignored: {}", eventId);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("[SePay] Payment already processed txn={} status={}", transactionId, payment.getStatus());
            rememberSepayEvent(eventId);
            return;
        }
        if (payment.getAmount().compareTo(webhook.transferAmount()) != 0) {
            log.warn("[SePay] Amount mismatch txn={}: expected={}, got={}",
                    transactionId, payment.getAmount(), webhook.transferAmount());
            throw new BadRequestException("Số tiền SePay không khớp với giao dịch");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        subscriptionService.activateFromPayment(payment);
        rememberSepayEvent(eventId);

        auditService.log(payment.getUser().getUserId(), AuditAction.PAYMENT_SUCCESS, "PAYMENT",
                String.valueOf(payment.getPaymentId()),
                "Payment confirmed via SEPAY for amount: " + payment.getAmount()
                        + ", reference: " + webhook.referenceCode());

        eventPublisher.publishEvent(new PaymentConfirmedEvent(
                payment.getPaymentId(), payment.getTransactionId(), payment.getUser().getUserId(),
                payment.getProductId(), payment.getAmount()));
        log.info("[SePay] Payment confirmed txn={} webhookId={}", transactionId, webhook.id());
    }

    private void rememberSepayEvent(String eventId) {
        var processed = new com.example.demo.entity.ProcessedWebhookEvent();
        processed.setEventHash(eventId);
        processed.setEventType("SEPAY_PAYMENT_RECEIVED");
        processedWebhookEventRepository.save(processed);
    }

    private String generatePaymentCode() {
        String prefix = sepayPaymentCodePrefix == null
                ? "" : sepayPaymentCodePrefix.trim().toUpperCase(java.util.Locale.ROOT);
        if (!prefix.matches("[A-Z]{2,5}")) {
            throw new IllegalStateException("SEPAY_PAYMENT_CODE_PREFIX phải gồm 2-5 chữ cái A-Z");
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = prefix + String.format("%08d", PAYMENT_CODE_RANDOM.nextInt(100_000_000));
            if (!paymentRepository.existsByTransactionId(code)) return code;
        }
        throw new IllegalStateException("Không thể sinh mã thanh toán duy nhất");
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
