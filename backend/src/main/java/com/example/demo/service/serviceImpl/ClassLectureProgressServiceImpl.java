package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.dto.request.ClassProgressUpdateRequest;
import com.example.demo.dto.response.ClassProgressResponse;
import com.example.demo.entity.ClassEntity;
import com.example.demo.entity.ClassLecture;
import com.example.demo.entity.ClassLectureProgress;
import com.example.demo.entity.ClassLectureProgressId;
import com.example.demo.entity.ClassOrder;
import com.example.demo.entity.ClassOrderStatus;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.User;
import com.example.demo.repository.ClassLectureProgressRepository;
import com.example.demo.repository.ClassLectureRepository;
import com.example.demo.repository.ClassOrderRepository;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ClassLectureProgressService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassLectureProgressServiceImpl implements ClassLectureProgressService {

    private final ClassLectureProgressRepository progressRepository;
    private final ClassLectureRepository classLectureRepository;
    private final ClassRepository classRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;
    private final ClassOrderRepository classOrderRepository;

    @Override
    @Transactional
    public void recordProgress(Integer studentId, Integer classId, Long lectureId, ClassProgressUpdateRequest request) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học"));
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài giảng"));

        ClassLectureProgress progress = progressRepository.findByIdStudentIdAndIdClassIdAndIdLectureId(studentId, classId, lectureId)
                .orElseGet(() -> {
                    ClassLectureProgress p = new ClassLectureProgress();
                    p.setId(new ClassLectureProgressId(studentId, classId, lectureId));
                    p.setStudent(student);
                    p.setClassEntity(classEntity);
                    p.setLecture(lecture);
                    return p;
                });

        // Chỉ tăng tiến độ, không lùi
        int currentWatched = progress.getWatchedSeconds() != null ? progress.getWatchedSeconds() : 0;
        int newWatched = Math.max(currentWatched, request.getWatchedSeconds());
        progress.setWatchedSeconds(newWatched);

        int total = request.getTotalSeconds() > 0 ? request.getTotalSeconds() : (progress.getTotalSeconds() != null ? progress.getTotalSeconds() : 0);
        progress.setTotalSeconds(total);

        boolean completed = Boolean.TRUE.equals(request.getIsCompleted()) ||
                (total > 0 && newWatched >= total * 0.9);
        progress.setIsCompleted(completed);

        progressRepository.save(progress);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassProgressResponse getProgress(Integer studentId, Integer classId) {
        List<ClassLecture> classLectures = classLectureRepository.findByClassEntity_ClassId(classId);
        int totalLectures = classLectures.size();

        List<ClassLectureProgress> progresses = progressRepository.findByIdStudentIdAndIdClassId(studentId, classId);
        int completedLectures = (int) progresses.stream().filter(ClassLectureProgress::getIsCompleted).count();

        // Tính tổng thời lượng đã xem vs tổng thời lượng lớp
        long totalWatchedSeconds = 0;
        long totalClassSeconds = 0;

        for (ClassLectureProgress p : progresses) {
            totalWatchedSeconds += p.getWatchedSeconds() != null ? p.getWatchedSeconds() : 0;
        }

        // Lấy tổng thời lượng các bài giảng trong lớp
        for (ClassLecture cl : classLectures) {
            // Giả định mỗi bài giảng trung bình 1800 giây nếu chưa có dữ liệu cụ thể
            totalClassSeconds += 1800;
        }

        BigDecimal watchedPercentage = BigDecimal.ZERO;
        if (totalLectures > 0) {
            if (totalClassSeconds > 0) {
                watchedPercentage = BigDecimal.valueOf(totalWatchedSeconds)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalClassSeconds), 2, RoundingMode.HALF_UP);
            }
            // Hoặc tính theo số bài hoàn thành nếu cao hơn
            BigDecimal completedPercentage = BigDecimal.valueOf(completedLectures)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalLectures), 2, RoundingMode.HALF_UP);
            if (completedPercentage.compareTo(watchedPercentage) > 0) {
                watchedPercentage = completedPercentage;
            }
        }

        // Kiểm tra điều kiện hoàn tiền
        boolean canRefund = true;
        String reason = null;
        Long remainingHoldSeconds = null;

        var orderOpt = classOrderRepository.findByStudentUserIdAndClassEntityClassIdAndStatus(studentId, classId, ClassOrderStatus.ACTIVE);
        if (orderOpt.isEmpty()) {
            canRefund = false;
            reason = "Bạn chưa mua lớp học này hoặc đã hoàn tiền";
        } else {
            ClassOrder order = orderOpt.get();
            Instant now = Instant.now();
            Instant holdUntil = order.getHoldUntil();
            if (now.isAfter(holdUntil)) {
                canRefund = false;
                reason = "Đã quá thời hạn 4 ngày để yêu cầu hoàn tiền";
            } else {
                remainingHoldSeconds = Math.max(0, Duration.between(now, holdUntil).getSeconds());
                if (watchedPercentage.compareTo(new BigDecimal("5.00")) > 0) {
                    canRefund = false;
                    reason = "Bạn đã học quá 5% bài giảng của lớp (" + watchedPercentage + "%), không thể hoàn tiền theo chính sách";
                }
            }
        }

        return ClassProgressResponse.builder()
                .classId(classId)
                .totalLectures(totalLectures)
                .completedLectures(completedLectures)
                .watchedPercentage(watchedPercentage)
                .canRefund(canRefund)
                .refundBlockReason(reason)
                .remainingHoldSeconds(remainingHoldSeconds)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEligibleForRefundProgress(Integer studentId, Integer classId) {
        ClassProgressResponse res = getProgress(studentId, classId);
        return Boolean.TRUE.equals(res.getCanRefund());
    }
}
