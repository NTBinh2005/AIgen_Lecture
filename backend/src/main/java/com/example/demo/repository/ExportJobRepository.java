package com.example.demo.repository;

import com.example.demo.entity.ExportJob;
import com.example.demo.entity.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from ExportJob e where e.jobId = :id")
    java.util.Optional<ExportJob> lockByJobId(@org.springframework.data.repository.query.Param("id") Long id);
    List<ExportJob> findByStatus(ExportStatus status);
    List<ExportJob> findByExpiresAtBefore(LocalDateTime now);
    List<ExportJob> findByRequestedBy_UserIdOrderByCreatedAtDesc(Integer userId);
    org.springframework.data.domain.Page<ExportJob> findByRequestedBy_UserId(
            Integer userId, org.springframework.data.domain.Pageable pageable);
}
