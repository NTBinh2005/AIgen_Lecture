package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.WithdrawalCreateRequest;
import com.example.demo.dto.request.WithdrawalReviewRequest;
import com.example.demo.dto.response.WithdrawalResponse;
import com.example.demo.entity.WithdrawalStatus;
import com.example.demo.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Withdrawals", description = "API rút tiền dành cho giáo viên và admin duyệt")
@RestController
@RequestMapping("/api/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @Operation(summary = "Giáo viên tạo yêu cầu rút tiền về ngân hàng", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<WithdrawalResponse> requestWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WithdrawalCreateRequest request) {
        return ResponseEntity.ok(withdrawalService.requestWithdrawal(principal.getUserId(), request));
    }

    @Operation(summary = "Giáo viên xem lịch sử rút tiền của mình", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<Page<WithdrawalResponse>> getMyWithdrawals(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseEntity.ok(withdrawalService.getMyWithdrawals(principal.getUserId(), pageable));
    }

    @Operation(summary = "Admin xem danh sách yêu cầu rút tiền", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<WithdrawalResponse>> getAllWithdrawals(
            @RequestParam(required = false) WithdrawalStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(withdrawalService.getAllWithdrawals(status, pageable));
    }

    @Operation(summary = "Admin duyệt / từ chối / hoàn tất yêu cầu rút tiền", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WithdrawalResponse> reviewWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id,
            @Valid @RequestBody WithdrawalReviewRequest request) {
        return ResponseEntity.ok(withdrawalService.reviewWithdrawal(principal.getUserId(), id, request));
    }
}
