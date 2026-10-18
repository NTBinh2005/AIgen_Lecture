package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment", description = "API thanh toán QR qua SePay")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Tạo giao dịch thanh toán SePay",
            description = "Trả về paymentUrl là ảnh VietQR chứa số tiền và mã thanh toán.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentService.createPayment(
                principal.getUserId(), request, getClientIp(httpRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Integer id) {
        return ResponseEntity.ok(paymentService.getPaymentById(principal.getUserId(), id));
    }

    @GetMapping("/my-payments")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.getMyPayments(principal.getUserId()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<PaymentResponse>> getAllPayments(
            @org.springdoc.core.annotations.ParameterObject
            @org.springframework.data.web.PageableDefault(size = 20, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(paymentService.getAllPayments(pageable));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Integer id) {
        return ResponseEntity.ok(paymentService.cancelPayment(principal.getUserId(), id));
    }

    /** SePay ký trên đúng raw JSON body theo HMAC-SHA256(timestamp + "." + body). */
    @Operation(summary = "Webhook SePay đối soát thanh toán tiền vào và hoàn tiền tiền ra")
    @PostMapping(value = "/webhook/sepay", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestBody String rawBody,
            @Parameter(hidden = true) @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @Parameter(hidden = true) @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp) {
        try {
            paymentService.handleSepayWebhook(rawBody, signature, timestamp);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
