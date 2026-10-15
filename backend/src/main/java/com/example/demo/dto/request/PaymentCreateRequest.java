package com.example.demo.dto.request;

import com.example.demo.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentCreateRequest(
        @NotNull(message = "Gói đăng ký không được để trống") Integer planId,
        @NotNull(message = "Phương thức thanh toán không được để trống") PaymentMethod paymentMethod,
        String orderInfo,
        String returnUrl
) {
    public PaymentCreateRequest {
        if (paymentMethod == null) paymentMethod = PaymentMethod.SEPAY;
    }

    /** Compatibility for Java callers; the client-supplied amount is deliberately ignored. */
    public PaymentCreateRequest(Integer planId, BigDecimal ignoredAmount) {
        this(planId, PaymentMethod.SEPAY, null, null);
    }

    public PaymentCreateRequest(Integer planId, BigDecimal ignoredAmount, PaymentMethod method) {
        this(planId, method, null, null);
    }

    public PaymentCreateRequest(Integer planId, BigDecimal ignoredAmount, PaymentMethod method,
                                String orderInfo, String returnUrl) {
        this(planId, method, orderInfo, returnUrl);
    }
}
