package com.example.demo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    /** UUID dùng để đối soát với payment gateway (unique per transaction) */
    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    /** Cổng thanh toán được chọn: VNPAY hoặc MOMO */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    /**
     * URL redirect đến trang thanh toán của gateway (VNPAY / MoMo).
     * Trả về cho client để redirect người dùng.
     */
    @Column(name = "payment_url", length = 2048)
    private String paymentUrl;

    /**
     * Mô tả đơn hàng, ghi thêm thông tin sản phẩm / gói học để hiện thị
     * trên trang thanh toán của gateway.
     */
    @Column(name = "order_info", length = 500)
    private String orderInfo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
