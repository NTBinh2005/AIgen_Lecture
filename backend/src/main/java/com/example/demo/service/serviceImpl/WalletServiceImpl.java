package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.response.DepositResponse;
import com.example.demo.dto.response.WalletResponse;
import com.example.demo.dto.response.WalletTransactionResponse;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentMethod;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.SubscriptionPlan;
import com.example.demo.entity.User;
import com.example.demo.entity.Wallet;
import com.example.demo.entity.WalletTransaction;
import com.example.demo.entity.WalletTransactionType;
import com.example.demo.gateway.SepayGateway;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.SubscriptionPlanRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WalletRepository;
import com.example.demo.repository.WalletTransactionRepository;
import com.example.demo.service.SubscriptionService;
import com.example.demo.service.WalletService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionService subscriptionService;
    private final SepayGateway sepayGateway;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public WalletResponse getMyWallet(Integer userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    public Wallet getOrCreateWallet(Integer userId) {
        return walletRepository.findByUserUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));
            Wallet newWallet = new Wallet();
            newWallet.setUser(user);
            newWallet.setBalance(BigDecimal.ZERO);
            newWallet.setPendingBalance(BigDecimal.ZERO);
            newWallet.setLockedBalance(BigDecimal.ZERO);
            return walletRepository.save(newWallet);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getMyTransactions(Integer userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ví tiền không tồn tại"));
        return walletTransactionRepository.findByWalletWalletIdOrderByCreatedAtDesc(wallet.getWalletId(), pageable)
                .map(this::toTransactionResponse);
    }

    @Override
    @Transactional
    public DepositResponse createDeposit(Integer userId, DepositRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (request.getAmount() == null || request.getAmount().compareTo(new BigDecimal("10000")) < 0) {
            throw new BadRequestException("Số tiền nạp tối thiểu là 10.000 VNĐ");
        }

        // Tạo mã nạp tiền ND + 8 số ngẫu nhiên
        String transactionId = "ND" + String.format("%08d", RANDOM.nextInt(100_000_000));
        while (paymentRepository.findByTransactionId(transactionId).isPresent()) {
            transactionId = "ND" + String.format("%08d", RANDOM.nextInt(100_000_000));
        }

        String qrUrl = sepayGateway.createPaymentUrl(
                transactionId,
                request.getAmount(),
                "Nap tien vi " + user.getUserId(),
                null,
                "127.0.0.1"
        );

        // Lưu vào bảng payments để quản lý vòng đời và webhook SePay
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setAmount(request.getAmount());
        payment.setTransactionId(transactionId);
        payment.setPaymentMethod(PaymentMethod.SEPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentUrl(qrUrl);
        paymentRepository.save(payment);

        return DepositResponse.builder()
                .orderCode(transactionId)
                .amount(request.getAmount())
                .qrUrl(qrUrl)
                .accountNumber(sepayGateway.getBankAccount())
                .accountName(sepayGateway.getAccountHolder())
                .bankName(sepayGateway.getBankName())
                .description(transactionId)
                .build();
    }

    @Override
    @Transactional
    public void processDepositSuccess(String orderCode, BigDecimal amount, String referenceId) {
        Payment payment = paymentRepository.findByTransactionId(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch nạp: " + orderCode));
        Wallet wallet = getOrCreateWallet(payment.getUser().getUserId());
        credit(wallet, amount, WalletTransactionType.DEPOSIT, "Nạp tiền vào ví qua SePay", referenceId != null ? referenceId : orderCode);
        log.info("[Wallet] Nạp thành công {} VND cho user {}", amount, payment.getUser().getUserId());
    }

    @Override
    @Transactional
    public void paySubscription(Integer userId, Integer planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói: " + planId));
        if (!plan.isActive()) {
            throw new BadRequestException("Gói đăng ký hiện không hoạt động");
        }
        if (plan.getTargetRole() != user.getRole()) {
            throw new BadRequestException("Gói đăng ký không áp dụng cho vai trò của bạn");
        }

        Wallet wallet = getOrCreateWallet(userId);
        if (wallet.getBalance().compareTo(plan.getPrice()) < 0) {
            throw new BadRequestException("Số dư ví không đủ. Cần " + plan.getPrice() + " VNĐ, hiện có " + wallet.getBalance() + " VNĐ");
        }

        // Trừ tiền trong ví
        String refId = "SUB-" + System.currentTimeMillis();
        debit(wallet, plan.getPrice(), WalletTransactionType.SUBSCRIPTION_PAYMENT, "Thanh toán gói " + plan.getName() + " từ ví", refId);

        // Tạo record Payment trạng thái SUCCESS
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setProductId(plan.getPlanId());
        payment.setAmount(plan.getPrice());
        payment.setTransactionId(refId);
        payment.setPaymentMethod(PaymentMethod.WALLET);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPlanSnapshotJson(subscriptionService.snapshotPlan(plan));
        payment = paymentRepository.save(payment);

        subscriptionService.activateFromPayment(payment);
        log.info("[Wallet] Thanh toán gói {} thành công bằng ví cho user {}", plan.getName(), userId);
    }

    @Override
    @Transactional
    public void credit(Wallet wallet, BigDecimal amount, WalletTransactionType type, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void debit(Wallet wallet, BigDecimal amount, WalletTransactionType type, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Số dư khả dụng không đủ");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(type);
        tx.setAmount(amount.negate());
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void creditPending(Wallet wallet, BigDecimal amount, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(WalletTransactionType.CLASS_INCOME_PENDING);
        tx.setAmount(amount);
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void releasePendingToAvailable(Wallet wallet, BigDecimal amount, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal toDeduct = wallet.getPendingBalance().min(amount);
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(toDeduct));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(WalletTransactionType.CLASS_INCOME_AVAILABLE);
        tx.setAmount(amount);
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void deductPending(Wallet wallet, BigDecimal amount, WalletTransactionType type, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (wallet.getPendingBalance().compareTo(amount) >= 0) {
            wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
        } else {
            // Nếu pending không đủ thì trừ phần còn lại vào balance khả dụng
            BigDecimal remaining = amount.subtract(wallet.getPendingBalance());
            wallet.setPendingBalance(BigDecimal.ZERO);
            wallet.setBalance(wallet.getBalance().subtract(remaining).max(BigDecimal.ZERO));
        }
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(type);
        tx.setAmount(amount.negate());
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void lockBalance(Wallet wallet, BigDecimal amount, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Số dư khả dụng không đủ để yêu cầu rút tiền");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(WalletTransactionType.WITHDRAWAL_LOCK);
        tx.setAmount(amount.negate());
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void unlockBalance(Wallet wallet, BigDecimal amount, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal toUnlock = wallet.getLockedBalance().min(amount);
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(toUnlock));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(WalletTransactionType.WITHDRAWAL_CANCELLED);
        tx.setAmount(amount);
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    @Override
    @Transactional
    public void deductLockedBalance(Wallet wallet, BigDecimal amount, String description, String refId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal toDeduct = wallet.getLockedBalance().min(amount);
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(toDeduct));
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(wallet);
        tx.setType(WalletTransactionType.WITHDRAWAL_COMPLETED);
        tx.setAmount(amount.negate());
        tx.setBalanceAfter(wallet.getBalance());
        tx.setDescription(description);
        tx.setReferenceId(refId);
        walletTransactionRepository.save(tx);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .userId(wallet.getUser().getUserId())
                .balance(wallet.getBalance())
                .pendingBalance(wallet.getPendingBalance())
                .lockedBalance(wallet.getLockedBalance())
                .totalBalance(wallet.getBalance().add(wallet.getPendingBalance()))
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private WalletTransactionResponse toTransactionResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .description(tx.getDescription())
                .referenceId(tx.getReferenceId())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
