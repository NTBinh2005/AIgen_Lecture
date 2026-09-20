package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
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

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RefundResponse requestRefund(Integer userId, RefundCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Ownership check — only the payment owner may request a refund
        if (!payment.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Access denied to this payment");
        }

        // Only SUCCESS payments are eligible for refund
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Only successful payments can be refunded");
        }

        // Duplicate-refund guard (idempotency)
        if (refundRepository.existsByPaymentPaymentId(request.paymentId())) {
            throw new BadRequestException("A refund has already been requested for this payment");
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setUser(user);
        refund.setAmount(payment.getAmount()); // refund the full paid amount
        refund.setReason(request.reason());
        refund.setStatus(RefundStatus.REQUESTED);

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

        if (!refund.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Access denied to this refund");
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

    private RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getRefundId(),
                refund.getPayment().getPaymentId(),
                refund.getUser().getUserId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}
