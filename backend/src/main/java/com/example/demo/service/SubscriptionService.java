package com.example.demo.service;

import com.example.demo.dto.request.SubscriptionPlanRequest;
import com.example.demo.dto.response.SubscriptionPlanResponse;
import com.example.demo.dto.response.SubscriptionResponse;
import com.example.demo.entity.UserRole;
import com.example.demo.entity.Payment;
import java.util.List;

public interface SubscriptionService {
    List<SubscriptionPlanResponse> getPlans(UserRole role, boolean includeInactive);
    SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request);
    SubscriptionPlanResponse updatePlan(Integer planId, SubscriptionPlanRequest request);
    void deactivatePlan(Integer planId);
    List<SubscriptionResponse> getMySubscriptions(Integer userId);
    SubscriptionResponse cancel(Integer userId, Long subscriptionId);
    SubscriptionResponse resume(Integer userId, Long subscriptionId);
    void activateFromPayment(Payment payment);
    String snapshotPlan(com.example.demo.entity.SubscriptionPlan plan);
    void revokePayment(Payment payment);
    org.springframework.data.domain.Page<SubscriptionResponse> getAllSubscriptions(
            org.springframework.data.domain.Pageable pageable);
    SubscriptionResponse grant(Integer adminId, com.example.demo.dto.request.AdminSubscriptionGrantRequest request);
    SubscriptionResponse extend(Integer adminId, Long id, int days);
    SubscriptionResponse cancelImmediately(Integer adminId, Long id);
}
