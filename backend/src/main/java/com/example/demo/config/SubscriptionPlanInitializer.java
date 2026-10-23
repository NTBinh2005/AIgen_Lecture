package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.repository.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionPlanInitializer implements ApplicationRunner {
    private final SubscriptionPlanRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        seed("STUDENT_MONTHLY", "Học viên Premium tháng", "150000", BillingCycle.MONTHLY,
                30, UserRole.STUDENT, List.of("Xem video bài giảng", "Làm bài kiểm tra", "Theo dõi tiến độ"));
        seed("STUDENT_YEARLY", "Học viên Premium năm", "1500000", BillingCycle.YEARLY,
                365, UserRole.STUDENT, List.of("Toàn bộ quyền Premium học viên", "Tiết kiệm hai tháng"));
        seed("TEACHER_MONTHLY", "Giáo viên Creator tháng", "299000", BillingCycle.MONTHLY,
                30, UserRole.TEACHER, List.of("Tạo bài giảng AI", "Tạo quiz AI", "Render video"));
    }

    private void seed(String code, String name, String price, BillingCycle cycle, int days,
                      UserRole role, List<String> features) {
        if (repository.existsByCodeIgnoreCase(code)) return;
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(code);
        plan.setName(name);
        plan.setPrice(new BigDecimal(price));
        plan.setBillingCycle(cycle);
        plan.setDurationDays(days);
        plan.setTargetRole(role);
        plan.setFeaturesJson("[\"" + String.join("\",\"", features) + "\"]");
        plan.setActive(true);
        repository.save(plan);
    }
}
