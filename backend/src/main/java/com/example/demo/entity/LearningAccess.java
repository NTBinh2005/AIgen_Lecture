package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Ghi nhận quyền học của một user với một product/course cụ thể.
 * Được tạo tự động sau khi payment thành công.
 */
@Entity
@Table(name = "learning_accesses")
@Getter
@Setter
public class LearningAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** ID của course/product được cấp quyền (tham chiếu sang service khác) */
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    /** PREMIUM hoặc FREE */
    @Column(name = "access_type", nullable = false, length = 20)
    private String accessType;

    /** Payment tạo ra quyền học này — dùng để audit/truy vết */
    @Column(name = "payment_id", nullable = false)
    private Integer paymentId;

    /** Null = không giới hạn thời gian */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "granted_at", updatable = false)
    private Instant grantedAt;
}
