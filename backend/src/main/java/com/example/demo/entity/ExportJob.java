package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "export_jobs", indexes = {
        @Index(name = "idx_export_jobs_status", columnList = "status")
})
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ExportType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "scope_json", columnDefinition = "TEXT")
    private String scopeJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExportStatus status;

    @Column(name = "progress")
    private Integer progress = 0;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_key", length = 500)
    private String fileKey;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "error_report", columnDefinition = "TEXT")
    private String errorReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ExportStatus.QUEUED;
        }
    }
    
    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
