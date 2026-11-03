package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.SepayWebhookRequest;
import com.example.demo.dto.request.WithdrawalCreateRequest;
import com.example.demo.dto.request.WithdrawalReviewRequest;
import com.example.demo.dto.response.WithdrawalResponse;
import com.example.demo.entity.ProcessedWebhookEvent;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.Wallet;
import com.example.demo.entity.WithdrawalRequest;
import com.example.demo.entity.WithdrawalStatus;
import com.example.demo.gateway.SepayGateway;
import com.example.demo.repository.ProcessedWebhookEventRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WithdrawalRequestRepository;
import com.example.demo.service.StudyNotificationService;
import com.example.demo.service.WalletService;
import com.example.demo.service.WithdrawalService;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final SepayGateway sepayGateway;
    private final ProcessedWebhookEventRepository processedEvents;
    private final StudyNotificationService studyNotifications;

    private static final Pattern RT_PATTERN = Pattern.compile("(?i)(?<![A-Z0-9])RT([1-9][0-9]{0,9})(?![A-Z0-9])");

    @Override
    @Transactional
    public WithdrawalResponse requestWithdrawal(Integer teacherId, WithdrawalCreateRequest request) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BadRequestException("Chỉ có tài khoản giáo viên mới được quyền rút tiền từ ví");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(new BigDecimal("50000")) < 0) {
            throw new BadRequestException("Số tiền rút tối thiểu là 50.000 VNĐ");
        }

        Wallet wallet = walletService.getOrCreateWallet(teacherId);
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Số dư khả dụng trong ví không đủ. Bạn chỉ có thể rút từ tiền đã giải ngân sau 4 ngày.");
        }

        WithdrawalRequest wr = new WithdrawalRequest();
        wr.setTeacher(teacher);
        wr.setAmount(request.getAmount());
        wr.setBankName(request.getBankName().trim());
        wr.setBankAccount(request.getBankAccount().trim());
        wr.setAccountHolder(request.getAccountHolder().trim().toUpperCase());
        wr.setStatus(WithdrawalStatus.REQUESTED);
        wr = withdrawalRepository.save(wr);

        // Khóa số dư trong ví
        walletService.lockBalance(wallet, request.getAmount(), "Khóa tiền cho yêu cầu rút tiền RT" + wr.getWithdrawalId(), "RT" + wr.getWithdrawalId());

        log.info("[Withdrawal] Giáo viên {} tạo yêu cầu rút {} VNĐ (RT{})", teacherId, request.getAmount(), wr.getWithdrawalId());
        return toResponse(wr);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WithdrawalResponse> getMyWithdrawals(Integer teacherId, Pageable pageable) {
        return withdrawalRepository.findByTeacherUserIdOrderByCreatedAtDesc(teacherId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WithdrawalResponse> getAllWithdrawals(WithdrawalStatus status, Pageable pageable) {
        if (status != null) {
            return withdrawalRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(this::toResponse);
        }
        return withdrawalRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public WithdrawalResponse reviewWithdrawal(Integer adminId, Integer withdrawalId, WithdrawalReviewRequest request) {
        WithdrawalRequest wr = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu rút tiền: " + withdrawalId));

        if (wr.getStatus() == WithdrawalStatus.COMPLETED) {
            throw new BadRequestException("Yêu cầu rút tiền này đã hoàn tất");
        }
        if (wr.getStatus() == WithdrawalStatus.REJECTED) {
            throw new BadRequestException("Yêu cầu rút tiền này đã bị từ chối trước đó");
        }

        WithdrawalStatus newStatus;
        try {
            newStatus = WithdrawalStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Trạng thái không hợp lệ: " + request.getStatus());
        }

        Wallet wallet = walletService.getOrCreateWallet(wr.getTeacher().getUserId());

        if (newStatus == WithdrawalStatus.REJECTED) {
            wr.setStatus(WithdrawalStatus.REJECTED);
            wr.setAdminNote(request.getAdminNote());
            // Trả lại tiền bị khóa về balance khả dụng
            walletService.unlockBalance(wallet, wr.getAmount(), "Mở khóa tiền do từ chối rút RT" + wr.getWithdrawalId(), "RT" + wr.getWithdrawalId());
            studyNotifications.notify(wr.getTeacher().getUserId(), "Yêu cầu rút tiền bị từ chối",
                    "Yêu cầu rút " + wr.getAmount() + " VNĐ đã bị từ chối. Lý do: " + (request.getAdminNote() != null ? request.getAdminNote() : "Không đạt yêu cầu"),
                    com.example.demo.entity.NotificationType.REFUND_STATUS, "WALLET", wr.getWithdrawalId());
        } else if (newStatus == WithdrawalStatus.APPROVED) {
            wr.setStatus(WithdrawalStatus.APPROVED);
            wr.setAdminNote(request.getAdminNote());
        } else if (newStatus == WithdrawalStatus.COMPLETED) {
            wr.setStatus(WithdrawalStatus.COMPLETED);
            wr.setAdminNote(request.getAdminNote());
            // Trừ vĩnh viễn khoản tiền bị khóa
            walletService.deductLockedBalance(wallet, wr.getAmount(), "Hoàn tất rút tiền RT" + wr.getWithdrawalId(), "RT" + wr.getWithdrawalId());
            studyNotifications.notify(wr.getTeacher().getUserId(), "Rút tiền thành công",
                    "Số tiền " + wr.getAmount() + " VNĐ đã được chuyển về tài khoản ngân hàng của bạn.",
                    com.example.demo.entity.NotificationType.PAYMENT_SUCCESS, "WALLET", wr.getWithdrawalId());
        }

        wr = withdrawalRepository.save(wr);
        return toResponse(wr);
    }

    @Override
    @Transactional
    public void handleOutgoingSepayWebhook(SepayWebhookRequest webhook, String rawBody) {
        if (!"out".equalsIgnoreCase(webhook.transferType())) {
            return;
        }

        Integer id = extractWithdrawalId(webhook);
        if (id == null) {
            // Không phải mã rút tiền RT..., có thể là HT... hoàn tiền
            return;
        }

        WithdrawalRequest wr = withdrawalRepository.findById(id).orElse(null);
        if (wr == null) {
            return;
        }

        String eventId = "SEPAY_WD:" + webhook.id();
        if (processedEvents.existsById(eventId)) {
            return;
        }

        if (wr.getStatus() != WithdrawalStatus.APPROVED && wr.getStatus() != WithdrawalStatus.REQUESTED) {
            log.info("[Withdrawal] Đã xử lý yêu cầu RT{} trước đó, bỏ qua", id);
            return;
        }

        if (wr.getAmount().compareTo(webhook.transferAmount()) != 0) {
            log.warn("[Withdrawal] Số tiền chi không khớp RT{}: cần {}, webhook {}", id, wr.getAmount(), webhook.transferAmount());
            return;
        }

        Wallet wallet = walletService.getOrCreateWallet(wr.getTeacher().getUserId());
        walletService.deductLockedBalance(wallet, wr.getAmount(), "Hoàn tất chuyển khoản rút tiền RT" + id + " qua SePay", "RT" + id);

        wr.setStatus(WithdrawalStatus.COMPLETED);
        wr.setSepayWebhookId(webhook.id());
        wr.setSepayReferenceCode(webhook.referenceCode());
        wr.setAdminNote("Tự động xác nhận thành công qua webhook ngân hàng SePay");
        withdrawalRepository.save(wr);

        ProcessedWebhookEvent processed = new ProcessedWebhookEvent();
        processed.setEventHash(eventId);
        processed.setEventType("SEPAY_WITHDRAWAL_PAID");
        processedEvents.save(processed);

        studyNotifications.notify(wr.getTeacher().getUserId(), "Rút tiền thành công",
                "Số tiền " + wr.getAmount() + " VNĐ đã được chuyển thành công tới ngân hàng của bạn (mã GD: " + webhook.referenceCode() + ").",
                com.example.demo.entity.NotificationType.PAYMENT_SUCCESS, "WALLET", wr.getWithdrawalId());

        log.info("[Withdrawal] Tự động hoàn tất chi tiền RT{} cho giáo viên {}", id, wr.getTeacher().getUserId());
    }

    private Integer extractWithdrawalId(SepayWebhookRequest webhook) {
        String content = webhook.content();
        if (content == null || content.isBlank()) {
            content = webhook.code();
        }
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = RT_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private WithdrawalResponse toResponse(WithdrawalRequest wr) {
        String qrUrl = null;
        // Nếu APPROVED hoặc REQUESTED, tạo VietQR SePay để admin có thể quét thanh toán
        if (wr.getStatus() == WithdrawalStatus.APPROVED || wr.getStatus() == WithdrawalStatus.REQUESTED) {
            try {
                qrUrl = sepayGateway.createRefundQrUrl(
                        "RT" + wr.getWithdrawalId(),
                        wr.getAmount(),
                        wr.getBankName(),
                        wr.getBankAccount(),
                        wr.getAccountHolder()
                );
            } catch (Exception ex) {
                log.warn("[Withdrawal] Không thể tạo QR rút tiền cho RT{}: {}", wr.getWithdrawalId(), ex.getMessage());
            }
        }

        return WithdrawalResponse.builder()
                .withdrawalId(wr.getWithdrawalId())
                .teacherId(wr.getTeacher().getUserId())
                .teacherName(wr.getTeacher().getName())
                .teacherEmail(wr.getTeacher().getEmail())
                .amount(wr.getAmount())
                .bankName(wr.getBankName())
                .bankAccount(wr.getBankAccount())
                .accountHolder(wr.getAccountHolder())
                .status(wr.getStatus())
                .adminNote(wr.getAdminNote())
                .qrUrl(qrUrl)
                .createdAt(wr.getCreatedAt())
                .updatedAt(wr.getUpdatedAt())
                .build();
    }
}
