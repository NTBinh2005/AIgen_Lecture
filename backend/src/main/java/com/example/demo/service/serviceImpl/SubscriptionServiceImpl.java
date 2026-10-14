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
    private final com.example.demo.repository.SubscriptionCreditRepository creditRepository;
    private final com.example.demo.repository.LearningAccessRepository learningAccessRepository;
    private final com.example.demo.repository.UserRepository userRepository;

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
        if (payment.getStatus() != PaymentStatus.SUCCESS || payment.getPlan() == null || creditRepository.existsById(payment.getPaymentId())
                || subscriptionRepository.existsByLastPaymentId(payment.getPaymentId())) {
            return;
        }
        SubscriptionPlan plan = payment.getPlan();
        Instant now = Instant.now();
        // Serialize purchases for one user, including callbacks from separate instances.
        userRepository.lockByUserId(payment.getUser().getUserId()).orElseThrow();
        if (creditRepository.existsById(payment.getPaymentId())) return;
        SubscriptionPlanResponse purchased = payment.getPlanSnapshotJson() == null
                ? toPlanResponse(plan) : readPlanSnapshot(payment.getPlanSnapshotJson());
        int purchasedDays = purchased.durationDays() != null ? purchased.durationDays()
                : purchased.billingCycle() == BillingCycle.YEARLY ? 365 : 30;
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
                    .filter(other -> (other.getPlanSnapshotJson() == null ? other.getPlan().getTargetRole()
                            : readPlanSnapshot(other.getPlanSnapshotJson()).targetRole()) == purchased.targetRole())
                    .forEach(other -> {
                        if (other.getExpiresAt() == null && purchased.billingCycle() != BillingCycle.ONE_TIME) {
                            throw new ConflictException("Không thể thay gói vĩnh viễn bằng gói có thời hạn");
                        }
                        if (other.getExpiresAt() != null && other.getExpiresAt().isAfter(now)) {
                            long remainingSeconds = java.time.Duration.between(now, other.getExpiresAt()).getSeconds();
                            Instant base = subscription.getExpiresAt() == null ? now : subscription.getExpiresAt();
                            subscription.setExpiresAt(base.plusSeconds(remainingSeconds));
                        }
                        other.setStatus(SubscriptionStatus.CANCELLED);
                        other.setCancelledAt(now);
                        other.setCancelAtPeriodEnd(false);
                        subscriptionRepository.save(other);
                    });
        }

        if (purchased.billingCycle() == BillingCycle.ONE_TIME) {
            subscription.setExpiresAt(null);
        } else {
            Instant base = subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now)
                    ? subscription.getExpiresAt() : now;
            subscription.setExpiresAt(base.plus(purchasedDays, ChronoUnit.DAYS));
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
        subscription.setLastPaymentId(payment.getPaymentId());
        subscription.setPlanSnapshotJson(payment.getPlanSnapshotJson() == null ? snapshotPlan(plan) : payment.getPlanSnapshotJson());
        Subscription savedSubscription = subscriptionRepository.save(subscription);
        SubscriptionCredit credit = new SubscriptionCredit();
        credit.setPaymentId(payment.getPaymentId()); credit.setSubscription(savedSubscription);
        credit.setDurationSeconds(purchased.billingCycle() == BillingCycle.ONE_TIME ? 0 : purchasedDays * 86400L);
        creditRepository.save(credit);
        // Credits from replaced plans follow their remaining entitlement to the new plan.
        for (Subscription other : subscriptionRepository.findByUserUserIdOrderByCreatedAtDesc(payment.getUser().getUserId())) {
            if (other.getStatus() == SubscriptionStatus.CANCELLED && now.equals(other.getCancelledAt())
                    && !other.getSubscriptionId().equals(subscription.getSubscriptionId())) {
                for (SubscriptionCredit previous : creditRepository.findBySubscriptionSubscriptionIdAndRefundedFalse(other.getSubscriptionId())) {
                    previous.setSubscription(savedSubscription); creditRepository.save(previous);
                }
                learningAccessRepository.findByUserUserIdAndProductId(payment.getUser().getUserId(), other.getPlan().getPlanId())
                        .forEach(learningAccessRepository::delete);
            }
        }
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

    @Override
    @Transactional(readOnly=true)
    public List<SubscriptionResponse> getAllSubscriptions() {
        return subscriptionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public String snapshotPlan(SubscriptionPlan plan) {
        try { return objectMapper.writeValueAsString(toPlanResponse(plan)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Không thể lưu thông tin gói lúc mua", e); }
    }

    private SubscriptionPlanResponse readPlanSnapshot(String json) {
        try { return objectMapper.readValue(json, SubscriptionPlanResponse.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Thông tin gói lúc mua không hợp lệ", e); }
    }

    @Override
    @Transactional
    public void revokePayment(Payment payment) {
        userRepository.lockByUserId(payment.getUser().getUserId()).orElseThrow();
        SubscriptionCredit credit = creditRepository.findById(payment.getPaymentId()).orElse(null);
        if (credit != null && !credit.isRefunded()) {
            Subscription subscription = credit.getSubscription();
            credit.setRefunded(true); creditRepository.save(credit);
            Instant now = Instant.now();
            if (credit.getDurationSeconds() == 0 || subscription.getExpiresAt() == null) {
                subscription.setStatus(SubscriptionStatus.CANCELLED); subscription.setCancelledAt(now);
            } else {
                Instant reduced = subscription.getExpiresAt().minusSeconds(credit.getDurationSeconds());
                subscription.setExpiresAt(reduced.isBefore(now) ? now : reduced);
                if (!reduced.isAfter(now)) {
                    subscription.setStatus(SubscriptionStatus.CANCELLED); subscription.setCancelledAt(now);
                }
            }
            subscription.setCancelAtPeriodEnd(false); subscriptionRepository.save(subscription);
        } else if (credit == null) {
            subscriptionRepository.findByLastPaymentId(payment.getPaymentId()).ifPresent(subscription -> {
                subscription.setStatus(SubscriptionStatus.CANCELLED); subscription.setCancelledAt(Instant.now());
                subscription.setCancelAtPeriodEnd(false); subscriptionRepository.save(subscription);
            });
        }
        learningAccessRepository.findByPaymentId(payment.getPaymentId()).ifPresent(learningAccessRepository::delete);
    }

    private void requireAdmin(Integer id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (user.getRole() != UserRole.ADMIN) throw new AccessDeniedException("Chỉ admin được quản lý gói của người khác");
    }

    @Override @Transactional
    public SubscriptionResponse grant(Integer adminId, com.example.demo.dto.request.AdminSubscriptionGrantRequest request) {
        requireAdmin(adminId);
        User user = userRepository.lockByUserId(request.userId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        SubscriptionPlan plan = getPlan(request.planId());
        if (user.getStatus() != UserStatus.ACTIVE || plan.getTargetRole() != user.getRole()) throw new BadRequestException("Gói không phù hợp với tài khoản");
        if (!plan.isActive()) throw new BadRequestException("Gói đã ngừng bán");
        Subscription subscription = subscriptionRepository.findFirstByUserUserIdAndPlanPlanIdAndStatusOrderByCreatedAtDesc(
                request.userId(), request.planId(), SubscriptionStatus.ACTIVE).orElseGet(() -> {
                    Subscription created = new Subscription(); created.setUser(user); created.setPlan(plan); created.setStartedAt(Instant.now()); return created;
                });
        Instant now = Instant.now();
        if (plan.getBillingCycle() == BillingCycle.ONE_TIME) subscription.setExpiresAt(null);
        else {
            Instant base = subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now) ? subscription.getExpiresAt() : now;
            subscription.setExpiresAt(base.plus(request.days() == null ? durationDays(plan) : request.days(), ChronoUnit.DAYS));
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE); subscription.setCancelledAt(null); subscription.setCancelAtPeriodEnd(false);
        if (subscription.getPlanSnapshotJson() == null) subscription.setPlanSnapshotJson(snapshotPlan(plan));
        return toResponse(subscriptionRepository.save(subscription));
    }

    @Override @Transactional
    public SubscriptionResponse extend(Integer adminId, Long id, int days) {
        requireAdmin(adminId);
        if (days < 1 || days > 3650) throw new BadRequestException("Số ngày từ 1 đến 3650");
        Subscription subscription = subscriptionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói đăng ký"));
        userRepository.lockByUserId(subscription.getUser().getUserId()).orElseThrow();
        if (subscription.getExpiresAt() == null) throw new BadRequestException("Gói vĩnh viễn không cần gia hạn");
        Instant now = Instant.now();
        Instant base = subscription.getExpiresAt().isAfter(now) ? subscription.getExpiresAt() : now;
        subscription.setExpiresAt(base.plus(days, ChronoUnit.DAYS)); subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelledAt(null); subscription.setCancelAtPeriodEnd(false);
        return toResponse(subscriptionRepository.save(subscription));
    }

    @Override @Transactional
    public SubscriptionResponse cancelImmediately(Integer adminId, Long id) {
        requireAdmin(adminId);
        Subscription subscription = subscriptionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói đăng ký"));
        userRepository.lockByUserId(subscription.getUser().getUserId()).orElseThrow();
        subscription.setStatus(SubscriptionStatus.CANCELLED); subscription.setCancelledAt(Instant.now()); subscription.setCancelAtPeriodEnd(false);
        learningAccessRepository.findByUserUserIdAndProductId(subscription.getUser().getUserId(), subscription.getPlan().getPlanId())
                .forEach(learningAccessRepository::delete);
        return toResponse(subscriptionRepository.save(subscription));
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
                (subscription.getPlanSnapshotJson() == null ? toPlanResponse(subscription.getPlan()) : readPlanSnapshot(subscription.getPlanSnapshotJson())), subscription.getStatus(), subscription.getStartedAt(),
                subscription.getExpiresAt(), subscription.isCancelAtPeriodEnd(), subscription.getCancelledAt(),
                subscription.getLastPaymentId(), subscription.getCreatedAt(), subscription.getUpdatedAt());
    }
}
