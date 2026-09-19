package com.example.demo.repository;

import com.example.demo.entity.ExportJob;
import com.example.demo.entity.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {
    List<ExportJob> findByStatus(ExportStatus status);
    List<ExportJob> findByExpiresAtBefore(LocalDateTime now);
}
