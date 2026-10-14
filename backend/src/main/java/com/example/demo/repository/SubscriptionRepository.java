package com.example.demo.repository;

import com.example.demo.entity.Subscription;
import com.example.demo.entity.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserUserIdOrderByCreatedAtDesc(Integer userId);
    Optional<Subscription> findFirstByUserUserIdAndPlanPlanIdAndStatusOrderByCreatedAtDesc(
            Integer userId, Integer planId, SubscriptionStatus status);
    List<Subscription> findByUserUserIdAndStatus(Integer userId, SubscriptionStatus status);
    List<Subscription> findByStatusAndExpiresAtBefore(SubscriptionStatus status, Instant now);
    boolean existsByLastPaymentId(Integer paymentId);
    Optional<Subscription> findByLastPaymentId(Integer paymentId);
}
