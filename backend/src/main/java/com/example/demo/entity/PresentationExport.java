package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@Table(name = "presentation_exports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_presentation_export_version", columnNames = "presentation_version_id")
}, indexes = {
        @Index(name = "idx_presentation_export_presentation", columnList = "presentation_id,created_at")
})
public class PresentationExport {

    @Id
    @Column(name = "presentation_export_id", nullable = false, updatable = false)
    private UUID presentationExportId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false, updatable = false)
    private Presentation presentation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_version_id", nullable = false, updatable = false)
    private PresentationVersion presentationVersion;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "file_bytes", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] fileBytes;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    @Column(name = "sha256", nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Integer requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        if (presentationExportId == null) presentationExportId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
