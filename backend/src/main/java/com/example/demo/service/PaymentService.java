package com.example.demo.service;

import com.example.demo.dto.request.PaymentCreateRequest;
import com.example.demo.dto.request.PaymentWebhookRequest;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.entity.PaymentMethod;
import java.util.List;
import java.util.Map;

public interface PaymentService {

    /**
     * Tạo giao dịch thanh toán mới.
     * <p>
     * Sinh URL redirect đến VNPay hoặc MoMo tuỳ theo {@code request.paymentMethod()}.
     * Client đọc field {@code paymentUrl} trong response rồi redirect người dùng đến đó.
     *
     * @param userId  ID người dùng đang thanh toán
     * @param request thông tin tạo payment (productId, amount, paymentMethod, v.v.)
     * @param ipAddress IP người dùng (truyền vào cho VNPay)
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

    PaymentResponse cancelPayment(Integer userId, Integer paymentId);

    /**
     * Xử lý webhook/IPN từ VNPay hoặc MoMo.
     * <p>
     * Xác thực chữ ký, kiểm tra idempotency, cập nhật trạng thái Payment
     * và phát event {@code PaymentConfirmedEvent} nếu thành công.
     *
     * @param method  Cổng thanh toán gọi callback (VNPAY hoặc MOMO)
     * @param params  Map tham số từ gateway (query string params hoặc parsed JSON body)
     */
    void handleGatewayCallback(PaymentMethod method, Map<String, String> params);

    /**
     * Xử lý webhook cũ (legacy — backward compatible).
     */
    void handleWebhook(PaymentWebhookRequest request);
}
