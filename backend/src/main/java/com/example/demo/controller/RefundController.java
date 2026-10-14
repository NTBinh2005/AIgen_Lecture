package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.RefundCreateRequest;
import com.example.demo.dto.response.RefundResponse;
import com.example.demo.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Refund", description = "API for handling refund requests")
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "Request a refund for a successful payment",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<RefundResponse> requestRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RefundCreateRequest request) {
        return ResponseEntity.ok(refundService.requestRefund(principal.getUserId(), request));
    }

    @Operation(summary = "Get a refund by ID",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public ResponseEntity<RefundResponse> getRefundById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(refundService.getRefundById(principal.getUserId(), id));
    }

    @Operation(summary = "Get all refunds of the current user",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my-refunds")
    public ResponseEntity<List<RefundResponse>> getMyRefunds(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(refundService.getMyRefunds(principal.getUserId()));
    }
}
