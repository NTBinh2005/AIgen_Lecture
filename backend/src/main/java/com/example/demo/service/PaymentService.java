package com.example.demo.service;

import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.request.PaymentWebhookRequest;
import com.example.demo.dto.response.PaymentResponse;
import java.util.List;

public interface PaymentService {

    /**
     * Tạo giao dịch thanh toán mới.
     * <p>
     * Sinh URL VietQR của SePay. Client hiển thị ảnh tại {@code paymentUrl} để người dùng quét.
     *
     * @param userId  ID người dùng đang thanh toán
     * @param request thông tin tạo payment (productId, amount, paymentMethod, v.v.)
     * @param ipAddress IP người dùng
     * @return PaymentResponse kèm paymentUrl
     */
    PaymentResponse createPayment(Integer userId, PaymentCreateRequest request, String ipAddress);

    /**
     * Overload tạo payment với default localhost IP (dành cho tests và internal calls).
     */
    default PaymentResponse createPayment(Integer userId, PaymentCreateRequest request) {
        return createPayment(userId, request, "127.0.0.1");
    }

    PaymentResponse getPaymentById(Integer userId, Integer paymentId);

    List<PaymentResponse> getMyPayments(Integer userId);
    org.springframework.data.domain.Page<PaymentResponse> getAllPayments(
            org.springframework.data.domain.Pageable pageable);

    PaymentResponse cancelPayment(Integer userId, Integer paymentId);

    /** Xác thực raw body HMAC; xử lý tiền vào thanh toán và tiền ra hoàn tiền. */
    void handleSepayWebhook(String rawBody, String signature, String timestamp);

    /**
     * Xử lý webhook cũ (legacy — backward compatible).
     */
    void handleWebhook(PaymentWebhookRequest request);
}
