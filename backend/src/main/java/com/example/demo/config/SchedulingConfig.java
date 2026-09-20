package com.example.demo.config;

import com.example.demo.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import java.time.Duration;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig implements SchedulingConfigurer {

    private final RefreshTokenService refreshTokenService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("scheduled-task-");
        taskScheduler.initialize();

        taskRegistrar.setTaskScheduler(taskScheduler);

        // Xóa refresh token hết hạn mỗi 24 giờ để giữ bảng gọn
        taskRegistrar.addFixedRateTask(
                this::deleteExpiredRefreshTokens,
                Duration.ofHours(24)
        );
    }

    public void deleteExpiredRefreshTokens() {
        try {
            refreshTokenService.deleteExpiredTokens();
            log.info("[Scheduler] Đã xóa các refresh token hết hạn");
        } catch (Exception e) {
            log.error("[Scheduler] Lỗi khi xóa refresh token hết hạn: {}", e.getMessage());
        }
    }
}
