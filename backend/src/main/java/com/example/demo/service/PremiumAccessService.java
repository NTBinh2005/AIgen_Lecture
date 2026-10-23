package com.example.demo.service;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import com.example.demo.common.exception.SubscriptionRequiredException;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class PremiumAccessService {
    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Value("${app.subscriptions.enforce-premium:true}") private boolean enforcePremium = true;
    @Transactional(readOnly=true)
    public void requireActivePlan(Integer userId) {
        User user = users.findById(userId).orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản"));
        if (!enforcePremium || user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.STUDENT) return;
        if (user.getStatus() != UserStatus.ACTIVE) throw new AccessDeniedException("Tài khoản đã bị vô hiệu hoá");
        Instant now = Instant.now();
        boolean active = subscriptions.findByUserUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).stream()
                .anyMatch(s -> targetRole(s) == user.getRole()
                        && (s.getExpiresAt() == null || s.getExpiresAt().isAfter(now)));
        if (!active) {
            throw new SubscriptionRequiredException(
                    "Bạn cần gói đăng ký còn hiệu lực để sử dụng tính năng này");
        }
    }
    @Transactional(readOnly=true)
    public void requireStudentPlan(Integer userId) {
        // Học sinh không bắt buộc mua gói thành viên; thanh toán theo từng lớp học
    }
    private UserRole targetRole(Subscription subscription) {
        if (subscription.getPlanSnapshotJson() == null) return subscription.getPlan().getTargetRole();
        try { return UserRole.valueOf(mapper.readTree(subscription.getPlanSnapshotJson()).path("targetRole").asText()); }
        catch (Exception corruptedSnapshot) { throw new AccessDeniedException("Thông tin gói đăng ký không hợp lệ; vui lòng liên hệ admin"); }
    }

}
