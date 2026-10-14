package com.example.demo.repository;

import com.example.demo.entity.AuditAction;
import com.example.demo.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> searchLogs(
            @Param("userId") Integer userId,
            @Param("action") AuditAction action,
            @Param("resourceType") String resourceType
    );

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
