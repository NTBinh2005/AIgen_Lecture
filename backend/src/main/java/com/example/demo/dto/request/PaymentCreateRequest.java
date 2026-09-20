package com.example.demo.dto.request;

import com.example.demo.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Yêu cầu tạo giao dịch thanh toán mới")
public record PaymentCreateRequest(

        @Schema(description = "Mã sản phẩm / khóa học cần thanh toán", example = "42")
        @NotNull(message = "Mã sản phẩm/khóa học không được để trống")
        Integer productId,

        @Schema(description = "Số tiền cần thanh toán (VNĐ)", example = "150000")
        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền phải lớn hơn 0")
        BigDecimal amount,

        @Schema(description = "Cổng thanh toán: VNPAY hoặc MOMO", example = "VNPAY")
        @NotNull(message = "Phương thức thanh toán không được để trống")
        PaymentMethod paymentMethod,

        @Schema(description = "Mô tả đơn hàng (tùy chọn)", example = "Mua khóa học AI Video Generation")
        String orderInfo,

        @Schema(description = "URL callback sau khi thanh toán xong (tùy chọn — dùng mặc định nếu để trống)",
                example = "https://myapp.com/payment-result")
        String returnUrl
) {
    public PaymentCreateRequest {
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.VNPAY;
        }
    }

    public PaymentCreateRequest(Integer productId, BigDecimal amount) {
        this(productId, amount, PaymentMethod.VNPAY, null, null);
    }

    public PaymentCreateRequest(Integer productId, BigDecimal amount, PaymentMethod paymentMethod) {
        this(productId, amount, paymentMethod, null, null);
    }
}
