package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.RefundCreateRequest;
import com.example.demo.dto.response.RefundResponse;
import com.example.demo.entity.AuditAction;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.Refund;
import com.example.demo.entity.RefundStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.RefundRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuditService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.RefundService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.example.demo.service.SubscriptionService subscriptionService;
    private final com.example.demo.gateway.SepayGateway sepayGateway;
    private final com.example.demo.repository.ProcessedWebhookEventRepository processedEvents;
    private final com.example.demo.repository.AuditLogRepository auditLogs;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Override
    @Transactional
    public RefundResponse requestRefund(Integer userId, RefundCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Payment payment = paymentRepository.lockByPaymentId(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Ownership check — only the payment owner may request a refund
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to this payment");
        }

        // Only SUCCESS payments are eligible for refund
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Chỉ có thể hoàn tiền giao dịch đã thanh toán thành công");
        }

        // Duplicate-refund guard (idempotency)
        if (refundRepository.existsByPaymentPaymentId(request.paymentId())) {
            throw new ConflictException("Giao dịch này đã có yêu cầu hoàn tiền");
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setUser(user);
        refund.setAmount(payment.getAmount()); // refund the full paid amount
        refund.setReason(request.reason());
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setBankName(trim(request.bankName()));
        refund.setBankAccount(trim(request.bankAccount()));
        refund.setAccountHolder(trim(request.accountHolder()));
        validateRecipient(refund);

        Refund saved = refundRepository.save(refund);

        auditService.log(userId, AuditAction.REFUND_REQUESTED, "REFUND", String.valueOf(saved.getRefundId()),
                "Refund requested for payment: " + payment.getPaymentId() + ", amount: " + saved.getAmount() + ", reason: " + saved.getReason());

        notificationService.createNotification(
                userId,
                "Yêu cầu hoàn tiền đã ghi nhận",
                String.format("Yêu cầu hoàn tiền cho giao dịch #%d số tiền %s đang được xử lý.", payment.getPaymentId(), saved.getAmount()),
                NotificationType.REFUND_STATUS,
                String.valueOf(saved.getRefundId())
        );

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponse getRefundById(Integer userId, Integer refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (actor.getRole() != com.example.demo.entity.UserRole.ADMIN
                && !refund.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to this refund");
        }

        return toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getMyRefunds(Integer userId) {
        return refundRepository.findByUserUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RefundResponse> getAllRefunds(
            org.springframework.data.domain.Pageable pageable) {
        return refundRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public RefundResponse updateStatus(Integer adminId, Integer refundId, RefundStatus status) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (admin.getRole() != com.example.demo.entity.UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role is required");
        }
        Refund refund = lockRefundWithPayment(refundId);
        boolean valid = (refund.getStatus() == RefundStatus.REQUESTED
                && (status == RefundStatus.APPROVED || status == RefundStatus.REJECTED))
                || (refund.getStatus() == RefundStatus.APPROVED && status == RefundStatus.COMPLETED);
        if (!valid) {
            throw new BadRequestException("Không thể chuyển trạng thái yêu cầu hoàn tiền như đã chọn");
        }
        if (status == RefundStatus.COMPLETED
                && refund.getPayment().getPaymentMethod() == com.example.demo.entity.PaymentMethod.SEPAY) {
            throw new BadRequestException("Hoàn tiền SePay chỉ được xác nhận bằng webhook tiền ra hợp lệ");
        }
        if (status == RefundStatus.APPROVED) {
            validateRecipient(refund);
            if (refund.getPayment().getStatus() != PaymentStatus.SUCCESS) {
                throw new BadRequestException("Giao dịch gốc không còn đủ điều kiện hoàn tiền");
            }
        }
        refund.setStatus(status);
        Refund saved = refundRepository.save(refund);
        if (status == RefundStatus.COMPLETED) {
            Payment payment = refund.getPayment();
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            subscriptionService.revokePayment(payment);
            recordCompletion(refund, "Manual reconciliation for legacy payment method");
        }
        events.publishEvent(new com.example.demo.event.RefundStatusChangedEvent(
                saved.getRefundId(), saved.getUser().getUserId(), status));
        return toResponse(saved);
    }

    /** Called only after PaymentService has verified the signature on the raw body. */
    @Override
    @Transactional
    public void handleOutgoingSepayWebhook(com.example.demo.dto.request.SepayWebhookRequest webhook, String rawBody) {
        if (!"out".equalsIgnoreCase(webhook.transferType())
                || !sepayGateway.matchesOutgoingAccount(webhook.accountNumber())) {
            throw new BadRequestException("Tài khoản nguồn hoàn tiền SePay không khớp cấu hình");
        }
        Integer id = extractRefundId(webhook);
        Refund refund = lockRefundWithPayment(id);
        String eventId = "SEPAY:" + webhook.id();
        if (processedEvents.existsById(eventId)) {
            // Never accept an event already bound to a different payment/refund.
            if (!webhook.id().equals(refund.getSepayWebhookId())) {
                throw new BadRequestException("Giao dịch SePay đã được dùng cho một giao dịch khác");
            }
            return;
        }
        if (refund.getStatus() != RefundStatus.APPROVED) {
            throw new BadRequestException("Chỉ đối soát hoàn tiền cho yêu cầu đã được duyệt và chưa hoàn tất");
        }
        Payment payment = refund.getPayment();
        if (payment.getPaymentMethod() != com.example.demo.entity.PaymentMethod.SEPAY
                || payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Giao dịch gốc không phải giao dịch SePay đã thanh toán thành công");
        }
        validateRecipient(refund);
        if (refund.getAmount().compareTo(webhook.transferAmount()) != 0
                || payment.getAmount().compareTo(refund.getAmount()) != 0) {
            throw new BadRequestException("Số tiền SePay không khớp với yêu cầu hoàn tiền");
        }
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setSepayWebhookId(webhook.id());
        refund.setSepayReferenceCode(webhook.referenceCode());
        refund.setSepayWebhookPayload(rawBody);
        payment.setStatus(PaymentStatus.REFUNDED);
        subscriptionService.revokePayment(payment);
        var processed = new com.example.demo.entity.ProcessedWebhookEvent();
        processed.setEventHash(eventId); processed.setEventType("SEPAY_REFUND_PAID");
        processedEvents.save(processed);
        recordCompletion(refund, "SEPAY webhookId=" + webhook.id() + ", amount=" + refund.getAmount());
        events.publishEvent(new com.example.demo.event.RefundStatusChangedEvent(
                id, refund.getUser().getUserId(), RefundStatus.COMPLETED));
    }

    private Integer extractRefundId(com.example.demo.dto.request.SepayWebhookRequest webhook) {
        // Bank memos may wrap the code. Reject partial, ambiguous or conflicting codes.
        var pattern = java.util.regex.Pattern.compile("(?i)(?<![A-Z0-9])HT([1-9][0-9]{0,9})(?![A-Z0-9])");
        if (webhook.code() != null && !webhook.code().isBlank()
                && !webhook.code().strip().matches("(?i)HT[1-9][0-9]{0,9}")) {
            throw new BadRequestException("Mã hoàn tiền SePay không hợp lệ");
        }
        Integer found = null;
        for (String text : new String[] {webhook.code(), webhook.content()}) {
            if (text == null || text.isBlank()) continue;
            var matcher = pattern.matcher(text);
            boolean matched = false;
            while (matcher.find()) {
                matched = true;
                int candidate;
                try { candidate = Integer.parseInt(matcher.group(1)); }
                catch (NumberFormatException ex) { throw new BadRequestException("Mã hoàn tiền SePay không hợp lệ"); }
                if (found != null && found != candidate) {
                    throw new BadRequestException("Webhook chứa nhiều mã hoàn tiền khác nhau");
                }
                found = candidate;
            }
            if (text.equals(webhook.code()) && !matched) {
                throw new BadRequestException("Mã hoàn tiền SePay không hợp lệ");
            }
        }
        if (found == null) throw new BadRequestException("Không tìm thấy mã hoàn tiền HT trong webhook");
        return found;
    }

    @Override
    @Transactional
    public RefundResponse updateBankAccount(Integer userId, Integer refundId,
            com.example.demo.dto.request.RefundBankAccountRequest request) {
        Refund refund = lockRefundWithPayment(refundId);
        if (!refund.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền sửa tài khoản nhận hoàn tiền này");
        }
        boolean legacyApprovedWithoutRecipient = refund.getStatus() == RefundStatus.APPROVED
                && (refund.getBankName() == null || refund.getBankAccount() == null || refund.getAccountHolder() == null);
        if (refund.getStatus() != RefundStatus.REQUESTED && !legacyApprovedWithoutRecipient) {
            throw new BadRequestException("Không thể sửa tài khoản nhận sau khi yêu cầu đã được duyệt hoặc kết thúc");
        }
        refund.setBankName(trim(request.bankName())); refund.setBankAccount(trim(request.bankAccount()));
        refund.setAccountHolder(trim(request.accountHolder())); validateRecipient(refund);
        // Old approvals without a recipient must be reviewed again with the actual account.
        refund.setStatus(RefundStatus.REQUESTED);
        return toResponse(refundRepository.save(refund));
    }

    private Refund lockRefundWithPayment(Integer refundId) {
        Integer paymentId = refundRepository.findPaymentIdByRefundId(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền"));
        paymentRepository.lockByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));
        return refundRepository.lockByRefundId(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền"));
    }

    private void recordCompletion(Refund refund, String metadata) {
        // Commit audit, entitlement revocation and webhook deduplication together.
        var audit = new com.example.demo.entity.AuditLog();
        audit.setUserId(refund.getUser().getUserId()); audit.setAction(AuditAction.REFUND_COMPLETED);
        audit.setResourceType("REFUND"); audit.setResourceId(refund.getRefundId().toString());
        audit.setMetadata(metadata); auditLogs.save(audit);
    }

    private void validateRecipient(Refund refund) {
        sepayGateway.createRefundQrUrl("HT" + refund.getRefundId(), refund.getAmount(),
                refund.getBankName(), refund.getBankAccount(), refund.getAccountHolder());
    }

    private String trim(String value) { return value == null ? null : value.strip(); }

    private RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getRefundId(),
                refund.getPayment().getPaymentId(),
                refund.getUser().getUserId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getCreatedAt(),
                refund.getUpdatedAt(),
                refund.getBankName(), refund.getBankAccount(), refund.getAccountHolder(),
                "HT" + refund.getRefundId(),
                refund.getStatus() == RefundStatus.APPROVED && refund.getBankAccount() != null
                        && refund.getPayment().getPaymentMethod() == com.example.demo.entity.PaymentMethod.SEPAY
                        ? sepayGateway.createRefundQrUrl("HT" + refund.getRefundId(), refund.getAmount(),
                                refund.getBankName(), refund.getBankAccount(), refund.getAccountHolder()) : null,
                refund.getSepayWebhookId(), refund.getSepayReferenceCode()
        );
    }
}
