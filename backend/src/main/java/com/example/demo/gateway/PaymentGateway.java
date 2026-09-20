package com.example.demo.gateway;

import java.math.BigDecimal;

/**
 * Strategy interface cho các Payment Gateway (VNPay, MoMo, v.v.).
 * <p>
 * Mỗi gateway implement interface này để cung cấp:
 * <ul>
 *   <li>Sinh URL thanh toán để redirect người dùng ({@link #createPaymentUrl})</li>
 *   <li>Xác thực chữ ký callback / IPN từ gateway ({@link #verifyCallback})</li>
 * </ul>
 *
 * Thêm gateway mới: tạo class mới implement {@code PaymentGateway} rồi đăng ký
 * vào {@link PaymentGatewayFactory}.
 */
public interface PaymentGateway {

    /**
     * Sinh URL redirect đến trang thanh toán của gateway.
     *
     * @param transactionId  Mã giao dịch nội bộ (UUID) — dùng để đối soát khi gateway callback về
     * @param amount         Số tiền (VND, không có phần thập phân đối với VNPay/MoMo)
     * @param orderInfo      Mô tả đơn hàng hiển thị trên trang thanh toán
     * @param returnUrl      URL mà gateway redirect người dùng về sau khi thanh toán
     * @param ipAddress      IP của người dùng (VNPay yêu cầu)
     * @return URL đầy đủ để redirect người dùng
     */
    String createPaymentUrl(String transactionId, BigDecimal amount, String orderInfo,
                            String returnUrl, String ipAddress);

    /**
     * Xác thực chữ ký HMAC trong callback/IPN từ gateway.
     *
     * @param params Map các tham số nhận được từ gateway (query params hoặc JSON body)
     * @return {@code true} nếu chữ ký hợp lệ
     */
    boolean verifyCallback(java.util.Map<String, String> params);

    /**
     * Trả về trạng thái chuẩn hoá từ tham số callback của gateway.
     *
     * @param params Map các tham số nhận được từ gateway
     * @return {@code "SUCCESS"}, {@code "FAILED"} hoặc {@code "PENDING"}
     */
    String extractStatus(java.util.Map<String, String> params);

    /**
     * Lấy transactionId nội bộ (UUID) từ tham số callback của gateway.
     *
     * @param params Map các tham số nhận được từ gateway
     * @return transactionId đã lưu khi tạo payment
     */
    String extractTransactionId(java.util.Map<String, String> params);
}
