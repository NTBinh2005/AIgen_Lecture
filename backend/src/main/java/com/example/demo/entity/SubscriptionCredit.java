package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "subscription_credits") @Getter @Setter
public class SubscriptionCredit {
    @Id @Column(name="payment_id") private Integer paymentId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="subscription_id", nullable=false) private Subscription subscription;
    @Column(name="duration_seconds", nullable=false) private long durationSeconds;
    @Column(nullable=false) private boolean refunded;
}
