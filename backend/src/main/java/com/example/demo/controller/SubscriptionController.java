package com.example.demo.controller;

import com.example.demo.common.security.UserPrincipal;
import com.example.demo.dto.request.SubscriptionPlanRequest;
import com.example.demo.dto.response.SubscriptionPlanResponse;
import com.example.demo.dto.response.SubscriptionResponse;
import com.example.demo.entity.UserRole;
import com.example.demo.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> plans(
            @RequestParam(required = false) UserRole role) {
        return subscriptionService.getPlans(role, false);
    }

    @GetMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SubscriptionPlanResponse> adminPlans(@RequestParam(required = false) UserRole role) {
        return subscriptionService.getPlans(role, true);
    }

    @PostMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlanResponse> createPlan(
            @Valid @RequestBody SubscriptionPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.createPlan(request));
    }

    @PutMapping("/plans/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionPlanResponse updatePlan(@PathVariable Integer planId,
                                                @Valid @RequestBody SubscriptionPlanRequest request) {
        return subscriptionService.updatePlan(planId, request);
    }

    @DeleteMapping("/plans/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePlan(@PathVariable Integer planId) {
        subscriptionService.deactivatePlan(planId);
    }

    @GetMapping("/subscriptions/me")
    public List<SubscriptionResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return subscriptionService.getMySubscriptions(principal.getUserId());
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public SubscriptionResponse cancel(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long subscriptionId) {
        return subscriptionService.cancel(principal.getUserId(), subscriptionId);
    }

    @PostMapping("/subscriptions/{subscriptionId}/resume")
    public SubscriptionResponse resume(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long subscriptionId) {
        return subscriptionService.resume(principal.getUserId(), subscriptionId);
    }
}
