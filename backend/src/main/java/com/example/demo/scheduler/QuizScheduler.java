package com.example.demo.scheduler;

import com.example.demo.entity.Attempt;
import com.example.demo.entity.AttemptStatus;
import com.example.demo.entity.ExportJob;
import com.example.demo.entity.ExportStatus;
import com.example.demo.entity.SubmitType;
import com.example.demo.repository.AttemptRepository;
import com.example.demo.repository.ExportJobRepository;
import com.example.demo.service.AttemptService;
import com.example.demo.service.serviceImpl.ExportServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuizScheduler {

    private final AttemptRepository attemptRepository;
    private final AttemptService attemptService;
    private final ExportJobRepository exportJobRepository;
    private final ExportServiceImpl exportServiceImpl;

    @Value("${app.quiz.grace-period-seconds:0}")
    private int gracePeriodSeconds;

    @Value("${app.quiz.export.storage-dir:exports}")
    private String exportDir;

    @Scheduled(fixedRateString = "60000") // Every minute
    public void autoSubmitOverdueAttempts() {
        log.info("Running autoSubmitOverdueAttempts...");
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(gracePeriodSeconds);
        
        List<Attempt> overdueAttempts = attemptRepository.findByStatusAndDeadlineAtBefore(AttemptStatus.IN_PROGRESS, cutoff);
        
        for (Attempt attempt : overdueAttempts) {
            try {
                attemptService.submitAttempt(attempt.getStudent().getUserId(), attempt.getAttemptId(), SubmitType.AUTO);
                log.info("Auto-submitted attempt {}", attempt.getAttemptId());
            } catch (Exception e) {
                log.error("Failed to auto-submit attempt {}", attempt.getAttemptId(), e);
            }
        }
    }

    @Scheduled(fixedRateString = "10000") // Every 10 seconds
    public void processExportJobs() {
        List<ExportJob> queuedJobs = exportJobRepository.findByStatus(ExportStatus.QUEUED);
        for (ExportJob job : queuedJobs) {
            try {
                // Call via Spring proxy to ensure transaction and isolation if needed.
                // Since ExportServiceImpl has @Transactional on processExportJob, it's safe.
                exportServiceImpl.processExportJob(job.getJobId());
            } catch (Exception e) {
                log.error("Failed to process export job {}", job.getJobId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    public void cleanupExpiredExportFiles() {
        log.info("Running cleanupExpiredExportFiles...");
        List<ExportJob> expiredJobs = exportJobRepository.findByExpiresAtBefore(LocalDateTime.now());
        for (ExportJob job : expiredJobs) {
            if (job.getFileKey() != null) {
                File file = new File(exportDir, job.getFileKey());
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        log.info("Deleted expired export file: {}", file.getAbsolutePath());
                    }
                }
            }
            exportJobRepository.delete(job);
        }
    }
}
