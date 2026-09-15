package com.example.demo.repository;

import com.example.demo.entity.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InteractionLogRepository extends JpaRepository<InteractionLog, Long> {

    /**
     * Kiểm tra học sinh đã từng trả lời câu hỏi này chưa (theo BR-06).
     * Dùng để xác định isFirstAttempt.
     */
    boolean existsByStudent_UserIdAndAiElement_ElementId(Integer studentId, Long elementId);
}
