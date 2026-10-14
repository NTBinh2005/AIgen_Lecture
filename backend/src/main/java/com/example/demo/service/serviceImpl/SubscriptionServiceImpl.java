package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ConflictException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.SubscriptionPlanRequest;
import com.example.demo.dto.response.SubscriptionPlanResponse;
import com.example.demo.dto.response.SubscriptionResponse;
import com.example.demo.entity.*;
import com.example.demo.repository.SubscriptionPlanRepository;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.service.SubscriptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getPlans(UserRole role, boolean includeInactive) {
        return planRepository.findAll().stream()
                .filter(plan -> includeInactive || plan.isActive())
                .filter(plan -> role == null || plan.getTargetRole() == role)
                .sorted((left, right) -> left.getPrice().compareTo(right.getPrice()))
                .map(this::toPlanResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request) {
        String code = request.code().trim().toUpperCase();
        if (planRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Plan code already exists: " + code);
        }
        SubscriptionPlan plan = new SubscriptionPlan();
        apply(plan, request, code);
        return toPlanResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public SubscriptionPlanResponse updatePlan(Integer planId, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = getPlan(planId);
        String code = request.code().trim().toUpperCase();
        planRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getPlanId().equals(planId))
                .ifPresent(existing -> { throw new ConflictException("Plan code already exists: " + code); });
        apply(plan, request, code);
        return toPlanResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public void deactivatePlan(Integer planId) {
        SubscriptionPlan plan = getPlan(planId);
        plan.setActive(false);
        planRepository.save(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getMySubscriptions(Integer userId) {
        return subscriptionRepository.findByUserUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel(Integer userId, Long subscriptionId) {
        Subscription subscription = owned(subscriptionId, userId);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BadRequestException("Only an active subscription can be cancelled");
        }
        if (subscription.getExpiresAt() == null) {
            throw new BadRequestException("A one-time plan cannot be cancelled after activation");
        }
        subscription.setCancelledAt(Instant.now());
        subscription.setCancelAtPeriodEnd(true);
        return toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public SubscriptionResponse resume(Integer userId, Long subscriptionId) {
        Subscription subscription = owned(subscriptionId, userId);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE || !subscription.isCancelAtPeriodEnd()) {
            throw new BadRequestException("Subscription is not pending cancellation");
        }
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
        return toResponse(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public void activateFromPayment(Payment payment) {
        if (payment.getPlan() == null || subscriptionRepository.existsByLastPaymentId(payment.getPaymentId())) {
            return;
        }
        SubscriptionPlan plan = payment.getPlan();
        Instant now = Instant.now();
        Subscription subscription = subscriptionRepository
                .findFirstByUserUserIdAndPlanPlanIdAndStatusOrderByCreatedAtDesc(
                        payment.getUser().getUserId(), plan.getPlanId(), SubscriptionStatus.ACTIVE)
                .orElseGet(() -> {
                    Subscription created = new Subscription();
                    created.setUser(payment.getUser());
                    created.setPlan(plan);
                    created.setStatus(SubscriptionStatus.ACTIVE);
                    created.setStartedAt(now);
                    return created;
                });

        if (subscription.getSubscriptionId() == null) {
            subscriptionRepository.findByUserUserIdAndStatus(payment.getUser().getUserId(), SubscriptionStatus.ACTIVE)
                    .stream()
                    .filter(other -> other.getPlan().getTargetRole() == plan.getTargetRole())
                    .forEach(other -> {
                        other.setStatus(SubscriptionStatus.CANCELLED);
                        other.setCancelledAt(now);
                        other.setCancelAtPeriodEnd(false);
                        subscriptionRepository.save(other);
                    });
        }

        if (plan.getBillingCycle() == BillingCycle.ONE_TIME) {
            subscription.setExpiresAt(null);
        } else {
            Instant base = subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now)
                    ? subscription.getExpiresAt() : now;
            subscription.setExpiresAt(base.plus(durationDays(plan), ChronoUnit.DAYS));
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
        subscription.setLastPaymentId(payment.getPaymentId());
        subscriptionRepository.save(subscription);
    }

    @Scheduled(fixedDelayString = "${app.subscriptions.expiry-check-ms:60000}")
    @Transactional
    public void expireSubscriptions() {
        Instant now = Instant.now();
        subscriptionRepository.findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now)
                .forEach(subscription -> {
                    subscription.setStatus(subscription.isCancelAtPeriodEnd()
                            ? SubscriptionStatus.CANCELLED : SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(subscription);
                });
    }

    private void apply(SubscriptionPlan plan, SubscriptionPlanRequest request, String code) {
        if (request.targetRole() == UserRole.ADMIN) {
            throw new BadRequestException("Subscription plans cannot target ADMIN users");
        }
        plan.setCode(code);
        plan.setName(request.name().trim());
        plan.setDescription(request.description() == null ? null : request.description().trim());
        plan.setPrice(request.price());
        plan.setBillingCycle(request.billingCycle());
        plan.setDurationDays(request.durationDays());
        plan.setTargetRole(request.targetRole());
        try {
            plan.setFeaturesJson(objectMapper.writeValueAsString(request.features()));
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Plan features are not valid");
        }
        plan.setActive(request.active() == null || request.active());
    }

    private int durationDays(SubscriptionPlan plan) {
        if (plan.getDurationDays() != null) return plan.getDurationDays();
        return plan.getBillingCycle() == BillingCycle.YEARLY ? 365 : 30;
    }

    private SubscriptionPlan getPlan(Integer id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found: " + id));
    }

    private Subscription owned(Long id, Integer userId) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
        if (!subscription.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied to this subscription");
        }
        return subscription;
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        List<String> features;
        try {
            features = objectMapper.readValue(plan.getFeaturesJson(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            features = List.of();
        }
        return new SubscriptionPlanResponse(plan.getPlanId(), plan.getCode(), plan.getName(),
                plan.getDescription(), plan.getPrice(), plan.getBillingCycle(), plan.getDurationDays(),
                plan.getTargetRole(), features, plan.isActive(), plan.getCreatedAt(), plan.getUpdatedAt());
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(subscription.getSubscriptionId(), subscription.getUser().getUserId(),
                toPlanResponse(subscription.getPlan()), subscription.getStatus(), subscription.getStartedAt(),
                subscription.getExpiresAt(), subscription.isCancelAtPeriodEnd(), subscription.getCancelledAt(),
                subscription.getLastPaymentId(), subscription.getCreatedAt(), subscription.getUpdatedAt());
    }
}
