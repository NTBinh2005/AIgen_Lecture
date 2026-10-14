package com.example.demo.service;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor
public class StudyReminderScheduler {
    private final QuizAssignmentRepository assignments;
    private final AttemptRepository attempts;
    private final ClassStudentRepository members;
    private final LiveSessionRepository sessions;
    private final StudyNotificationService notices;
    @Scheduled(fixedDelayString="${app.notifications.reminder-check-ms:60000}")
    @Transactional
    public void remind() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        for (QuizAssignment assignment : assignments.findByStatusAndCloseAtBetween(AssignmentStatus.OPEN, now, now.plusHours(24))) {
            if (assignment.getOpenAt() != null && assignment.getOpenAt().isAfter(now)) continue;
            for (ClassStudent member : members.findByClassEntity_ClassId(assignment.getClassEntity().getClassId())) {
                if (member.getStatus() != EnrollmentStatus.ACTIVE) continue;
                boolean submitted = attempts.findByAssignment_AssignmentIdAndStudent_UserIdOrderByAttemptNoDesc(
                        assignment.getAssignmentId(), member.getStudent().getUserId()).stream()
                        .anyMatch(a -> a.getStatus() != AttemptStatus.IN_PROGRESS);
                if (!submitted) notices.notify(member.getStudent().getUserId(), "Bài kiểm tra sắp hết hạn",
                        "Bạn có bài kiểm tra chưa nộp, hạn nộp trong 24 giờ tới.", NotificationType.QUIZ_DUE,
                        "ASSIGNMENT", assignment.getAssignmentId() + "/deadline/" + assignment.getCloseAt());
            }
        }
        for (LiveSession session : sessions.findByStatusInAndStartsAtBetween(
                java.util.List.of(LiveSessionStatus.SCHEDULED, LiveSessionStatus.OPEN), now, now.plusMinutes(15))) {
            notices.notifyClass(session.getClassEntity().getClassId(), "Buổi học sắp bắt đầu", session.getTitle(),
                    NotificationType.LIVE_REMINDER, "LIVE_SESSION", session.getSessionId() + "/starts/" + session.getStartsAt());
        }
    }
}
