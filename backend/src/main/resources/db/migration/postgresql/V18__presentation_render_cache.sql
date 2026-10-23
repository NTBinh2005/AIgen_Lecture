-- HTML trình chiếu đã dựng bởi slide-ai-service, để vẫn trình chiếu được khi service tắt.
-- cache_key = SHA-256 của deck JSON + theme (cùng nội dung -> cùng HTML).
CREATE TABLE IF NOT EXISTS presentation_render_cache (
    cache_key VARCHAR(64) PRIMARY KEY,
    version_id UUID,
    theme VARCHAR(40) NOT NULL,
    html TEXT NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_presentation_render_cache_version ON presentation_render_cache(version_id);
