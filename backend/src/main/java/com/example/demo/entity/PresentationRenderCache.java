package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** HTML trình chiếu đã dựng, dùng khi slide-ai-service tắt. Khoá = SHA-256(deck JSON + theme). */
@Entity
@Getter
@Setter
@Table(name = "presentation_render_cache")
public class PresentationRenderCache {
    @Id
    @Column(name = "cache_key", length = 64, nullable = false, updatable = false)
    private String cacheKey;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "theme", length = 40, nullable = false)
    private String theme;

    @Column(name = "html", nullable = false, columnDefinition = "TEXT")
    private String html;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
