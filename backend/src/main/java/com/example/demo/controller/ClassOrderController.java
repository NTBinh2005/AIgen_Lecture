package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.ClassOrderRequest;
import com.example.demo.dto.response.ClassOrderResponse;
import com.example.demo.service.ClassOrderService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Class Orders", description = "API mua và hoàn tiền học phí lớp học")
@RestController
@RequestMapping("/api/class-orders")
@RequiredArgsConstructor
public class ClassOrderController {

    private final ClassOrderService classOrderService;

    @Operation(summary = "Mua lớp học (bằng ví hoặc SePay QR)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/purchase")
    public ResponseEntity<ClassOrderResponse> purchaseClass(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ClassOrderRequest request) {
        return ResponseEntity.ok(classOrderService.purchaseClass(principal.getUserId(), request));
    }

    @Operation(summary = "Yêu cầu hoàn tiền học phí lớp học (trong vòng 4 ngày và xem <= 5% bài giảng)", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/classes/{classId}/refund")
    public ResponseEntity<Map<String, String>> refundClass(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer classId) {
        classOrderService.refundClass(principal.getUserId(), classId);
        return ResponseEntity.ok(Map.of("message", "Hoàn tiền học phí về ví thành công"));
    }

    @Operation(summary = "Xem lịch sử mua lớp học của tôi", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<Page<ClassOrderResponse>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseEntity.ok(classOrderService.getStudentOrders(principal.getUserId(), pageable));
    }

    @Operation(summary = "Xem danh sách đơn mua của lớp học", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/classes/{classId}")
    public ResponseEntity<Page<ClassOrderResponse>> getClassOrders(
            @PathVariable Integer classId,
            Pageable pageable) {
        return ResponseEntity.ok(classOrderService.getClassOrders(classId, pageable));
    }
}
