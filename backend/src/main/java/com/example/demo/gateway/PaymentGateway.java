package com.example.demo.gateway;

import java.math.BigDecimal;

/**
 * Strategy interface tạo thông tin thanh toán cho một cổng thanh toán.
 * <p>
 * Mỗi gateway implement interface này để cung cấp:
 * <ul>
 *   <li>Sinh URL thanh toán để redirect người dùng ({@link #createPaymentUrl})</li>
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
     * @param amount         Số tiền VND
     * @param orderInfo      Mô tả đơn hàng hiển thị trên trang thanh toán
     * @param returnUrl      URL mà gateway redirect người dùng về sau khi thanh toán
     * @param ipAddress      IP của người dùng (nếu gateway cần)
     * @return URL thanh toán hoặc ảnh QR
     */
    String createPaymentUrl(String transactionId, BigDecimal amount, String orderInfo,
                            String returnUrl, String ipAddress);

}
