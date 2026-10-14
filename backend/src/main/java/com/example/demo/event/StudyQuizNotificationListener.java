package com.example.demo.event;
import com.example.demo.dto.event.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.StudyNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
@Component @RequiredArgsConstructor
public class StudyQuizNotificationListener {
    private final StudyNotificationService notices;
    private final AttemptRepository attempts;
    @EventListener
    public void assigned(QuizAssignmentPublishedEvent event) {
        notices.notifyClass(event.getClassId(), "Bài kiểm tra mới", "Lớp có bài kiểm tra mới. Hãy xem giờ mở bài và hạn nộp.",
                NotificationType.QUIZ_ASSIGNED, "ASSIGNMENT", event.getAssignmentId());
    }
    @EventListener
    public void submitted(AttemptSubmittedEvent event) {
        attempts.findById(event.getAttemptId()).filter(a -> a.getStatus() == AttemptStatus.REVIEW_REQUIRED).ifPresent(a ->
            notices.notify(a.getAssignment().getClassEntity().getTeacher().getUserId(), "Bài tự luận chờ chấm",
                    "Có bài làm cần giáo viên chấm điểm.", NotificationType.REVIEW_REQUIRED, "ATTEMPT", a.getAttemptId()));
    }
    @EventListener
    public void graded(AttemptGradedEvent event) {
        notices.notify(event.getStudentId(), "Đã có điểm bài kiểm tra", "Bài làm đã được chấm. Hãy mở kết quả để xem chi tiết.",
                NotificationType.ATTEMPT_GRADED, "ATTEMPT", event.getAttemptId());
    }
}
