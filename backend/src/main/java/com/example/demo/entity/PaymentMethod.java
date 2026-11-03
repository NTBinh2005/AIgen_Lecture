package com.example.demo.entity;

/**
 * Cổng thanh toán mà người dùng chọn khi tạo giao dịch.
 */
public enum PaymentMethod {
    SEPAY,
    WALLET,
    /** Chỉ giữ để đọc lịch sử giao dịch cũ; không còn được phép tạo mới. */
    @Deprecated
    VNPAY,
    /** Chỉ giữ để đọc lịch sử giao dịch cũ; không còn được phép tạo mới. */
    @Deprecated
    MOMO
}
