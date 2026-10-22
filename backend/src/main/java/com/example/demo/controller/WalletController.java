package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.PaySubscriptionWalletRequest;
import com.example.demo.dto.response.DepositResponse;
import com.example.demo.dto.response.WalletResponse;
import com.example.demo.dto.response.WalletTransactionResponse;
import com.example.demo.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wallet", description = "API ví tiền người dùng")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Lấy thông tin ví của tôi", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getMyWallet(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(walletService.getMyWallet(principal.getUserId()));
    }

    @Operation(summary = "Lấy lịch sử giao dịch ví", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me/transactions")
    public ResponseEntity<Page<WalletTransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseEntity.ok(walletService.getMyTransactions(principal.getUserId(), pageable));
    }

    @Operation(summary = "Nạp tiền vào ví qua SePay VietQR", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/deposit")
    public ResponseEntity<DepositResponse> createDeposit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(walletService.createDeposit(principal.getUserId(), request));
    }

    @Operation(summary = "Thanh toán gói dịch vụ bằng số dư ví", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/pay-subscription")
    public ResponseEntity<Map<String, String>> paySubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaySubscriptionWalletRequest request) {
        walletService.paySubscription(principal.getUserId(), request.getPlanId());
        return ResponseEntity.ok(Map.of("message", "Thanh toán gói thành công bằng ví"));
    }
}
