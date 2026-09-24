package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.request.PaymentWebhookRequest;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.entity.PaymentMethod;
import com.example.demo.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment", description = "API thanh toán — hỗ trợ VNPay và MoMo")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Tạo giao dịch thanh toán mới.
     * <p>
     * Response trả về field {@code paymentUrl} — client cần redirect người dùng tới URL này
     * để hoàn tất thanh toán trên trang VNPay hoặc MoMo.
     */
    @Operation(
            summary = "Tạo giao dịch thanh toán mới (VNPay / MoMo)",
            description = "Sinh URL thanh toán từ gateway được chọn. Client đọc `paymentUrl` trong response rồi redirect người dùng đến đó.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tạo thành công, kèm paymentUrl"),
                    @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ hoặc gateway lỗi")
            }
    )
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentCreateRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        return ResponseEntity.ok(paymentService.createPayment(principal.getUserId(), request, ipAddress));
    }

    @Operation(summary = "Lấy thông tin giao dịch theo ID", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(paymentService.getPaymentById(principal.getUserId(), id));
    }

    @Operation(summary = "Lấy tất cả giao dịch của người dùng hiện tại", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my-payments")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.getMyPayments(principal.getUserId()));
    }

    @Operation(summary = "Huỷ giao dịch đang PENDING", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) {
        return ResponseEntity.ok(paymentService.cancelPayment(principal.getUserId(), id));
    }

    // ── VNPay webhooks ────────────────────────────────────────────────────────

    /**
     * VNPay IPN (Instant Payment Notification).
     * <p>
     * VNPay gọi endpoint này (server-to-server) sau khi giao dịch hoàn tất.
     * Tham số được truyền dưới dạng query string.
     * Backend xác thực HMAC-SHA512 rồi cập nhật trạng thái payment.
     */
    @Operation(
            summary = "VNPay IPN — webhook xác nhận giao dịch (server-to-server)",
            description = "VNPay gọi endpoint này sau khi thanh toán hoàn tất. Trả về {RspCode: '00', Message: 'success'} nếu hợp lệ."
    )
    @GetMapping("/webhook/vnpay")
    public ResponseEntity<Map<String, String>> handleVnpayIpn(
            @Parameter(hidden = true) @RequestParam Map<String, String> params) {
        try {
            paymentService.handleGatewayCallback(PaymentMethod.VNPAY, params);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", ex.getMessage()));
        }
    }

    /**
     * VNPay Return URL (redirect sau khi người dùng thanh toán xong).
     * <p>
     * VNPay redirect người dùng về URL này với kết quả thanh toán.
     * KHÔNG dùng để cập nhật trạng thái — chỉ dùng cho UX (redirect về frontend).
     * Trạng thái thực sự cập nhật qua {@code /webhook/vnpay} (IPN).
     */
    @Operation(
            summary = "VNPay Return URL — redirect sau khi thanh toán",
            description = "Endpoint này chỉ dành cho UX. Trạng thái giao dịch được xác nhận qua IPN endpoint."
    )
    @GetMapping("/callback/vnpay")
    public ResponseEntity<Map<String, Object>> handleVnpayReturn(
            @Parameter(hidden = true) @RequestParam Map<String, String> params) {
        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String transactionId = params.getOrDefault("vnp_TxnRef", "");
        boolean success = "00".equals(responseCode);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("transactionId", transactionId);
        result.put("responseCode", responseCode);
        result.put("message", success ? "Thanh toán thành công" : "Thanh toán thất bại (code=" + responseCode + ")");
        return ResponseEntity.ok(result);
    }

    // ── MoMo webhooks ─────────────────────────────────────────────────────────

    /**
     * MoMo IPN (Instant Payment Notification).
     * <p>
     * MoMo gọi endpoint này (server-to-server) dưới dạng HTTP POST với JSON body.
     * Backend xác thực HMAC-SHA256 rồi cập nhật trạng thái payment.
     */
    @Operation(
            summary = "MoMo IPN — webhook xác nhận giao dịch (server-to-server)",
            description = "MoMo gọi endpoint này sau khi thanh toán hoàn tất với JSON body."
    )
    @PostMapping("/webhook/momo")
    public ResponseEntity<Map<String, String>> handleMomoIpn(
            @RequestBody Map<String, Object> body) {
        try {
            // Convert Map<String,Object> sang Map<String,String> để truyền vào gateway
            Map<String, String> params = new HashMap<>();
            body.forEach((k, v) -> params.put(k, v != null ? v.toString() : ""));
            paymentService.handleGatewayCallback(PaymentMethod.MOMO, params);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Giao dịch được xác nhận"));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("status", "failed", "message", ex.getMessage()));
        }
    }

    /**
     * MoMo Return URL — redirect người dùng về sau khi thanh toán.
     */
    @Operation(
            summary = "MoMo Return URL — redirect sau khi thanh toán",
            description = "Endpoint này chỉ dành cho UX. Trạng thái giao dịch được xác nhận qua IPN endpoint."
    )
    @GetMapping("/callback/momo")
    public ResponseEntity<Map<String, Object>> handleMomoReturn(
            @Parameter(hidden = true) @RequestParam Map<String, String> params) {
        String resultCode = params.getOrDefault("resultCode", "");
        String orderId = params.getOrDefault("orderId", "");
        boolean success = "0".equals(resultCode);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("transactionId", orderId);
        result.put("resultCode", resultCode);
        result.put("message", success ? "Thanh toán thành công" : "Thanh toán thất bại (code=" + resultCode + ")");
        return ResponseEntity.ok(result);
    }

    // ── Legacy webhook (backward compatible) ──────────────────────────────────

    @Operation(
            summary = "Webhook cũ (legacy) — dùng khi không phân biệt gateway",
            description = "Giữ backward compatibility. Ưu tiên dùng /webhook/vnpay hoặc /webhook/momo."
    )
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @Valid @RequestBody PaymentWebhookRequest request) {
        paymentService.handleWebhook(request);
        return ResponseEntity.ok().build();
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
