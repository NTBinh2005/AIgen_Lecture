package com.example.demo.repository;

import com.example.demo.entity.QuizAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAuditLogRepository extends JpaRepository<QuizAuditLog, Long> {
}
