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
    @org.springframework.data.jpa.repository.Query("select year(i.submittedAt), month(i.submittedAt), day(i.submittedAt), count(i) from InteractionLog i where i.submittedAt >= :since group by year(i.submittedAt), month(i.submittedAt), day(i.submittedAt)")
    java.util.List<Object[]> dailyCounts(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
    boolean existsByStudent_UserIdAndAiElement_ElementId(Integer studentId, Long elementId);
}
