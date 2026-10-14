package com.example.demo.repository;

import com.example.demo.entity.SubscriptionPlan;
import com.example.demo.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {
    Optional<SubscriptionPlan> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<SubscriptionPlan> findByActiveTrueAndTargetRoleOrderByPriceAsc(UserRole targetRole);
}
