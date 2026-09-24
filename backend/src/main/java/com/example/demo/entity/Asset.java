package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Binary asset owned by a Backend 3 user.
 *
 * <p>Assets are archived, never physically deleted through the Asset API. The
 * scalar owner id deliberately avoids coupling this module to the User entity.
 */
@Entity
@Getter
@Setter
@Table(
        name = "assets",
        indexes = {
                @Index(name = "idx_assets_owner_archived", columnList = "owner_id, archived_at"),
                @Index(name = "idx_assets_purpose", columnList = "purpose"),
                @Index(name = "idx_assets_sha256", columnList = "sha256")
        }
)
@Check(constraints = "size_bytes > 0 AND char_length(sha256) = 64")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Integer ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private AssetPurpose purpose;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "binary_content", nullable = false, columnDefinition = "bytea")
    private byte[] binaryContent;

    @Column(name = "external_source_url", length = 2048)
    private String externalSourceUrl;

    @Column(name = "license_status", length = 100)
    private String licenseStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void archive(Instant archivedAt) {
        if (this.archivedAt == null) {
            this.archivedAt = archivedAt;
        }
    }
}
