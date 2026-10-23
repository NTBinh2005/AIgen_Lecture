package com.example.demo.dto.response;

import com.example.demo.entity.PaymentMethod;
import com.example.demo.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Thông tin giao dịch thanh toán")
public record PaymentResponse(
        @Schema(description = "ID giao dịch nội bộ", example = "1")
        Integer paymentId,

        @Schema(description = "Mã giao dịch UUID dùng để đối soát với gateway", example = "b130e521-8208-466d-8a5f-d2fa4d7e9b0b")
        String transactionId,

        @Schema(description = "ID người dùng", example = "123")
        Integer userId,

        @Schema(description = "ID sản phẩm / khóa học", example = "42")
        Integer productId,

        Integer planId,
        String planName,

        @Schema(description = "Số tiền (VNĐ)", example = "150000")
        BigDecimal amount,

        @Schema(description = "Trạng thái giao dịch: PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED")
        PaymentStatus status,

        @Schema(description = "Phương thức thanh toán: SEPAY")
        PaymentMethod paymentMethod,

        @Schema(description = "URL ảnh VietQR do SePay tạo để khách hàng quét và chuyển khoản.",
                example = "https://vietqr.app/img?acc=...&bank=...&amount=150000&des=DH12345678")
        String paymentUrl,

        @Schema(description = "Mô tả đơn hàng", example = "Mua khóa học AI Video Generation")
        String orderInfo,

        Instant createdAt,
        Instant updatedAt
) {}
