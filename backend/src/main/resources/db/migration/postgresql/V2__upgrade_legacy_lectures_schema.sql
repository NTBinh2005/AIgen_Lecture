-- Bring legacy `lectures` tables up to the complete Lecture entity contract.
-- Every operation is idempotent so partially upgraded local databases are safe.
DO $$
BEGIN
    IF to_regclass('lectures') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE lectures
        ADD COLUMN IF NOT EXISTS access_scope VARCHAR(20),
        ADD COLUMN IF NOT EXISTS business_id UUID,
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE,
        ADD COLUMN IF NOT EXISTS current_version_id UUID,
        ADD COLUMN IF NOT EXISTS current_version_number INTEGER,
        ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITHOUT TIME ZONE,
        ADD COLUMN IF NOT EXISTS latest_generation_job_id UUID,
        ADD COLUMN IF NOT EXISTS original_source TEXT,
        ADD COLUMN IF NOT EXISTS published_at TIMESTAMP WITHOUT TIME ZONE,
        ADD COLUMN IF NOT EXISTS published_version_id UUID,
        ADD COLUMN IF NOT EXISTS row_version BIGINT,
        ADD COLUMN IF NOT EXISTS source_asset_id UUID,
        ADD COLUMN IF NOT EXISTS status VARCHAR(20),
        ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE,
        ADD COLUMN IF NOT EXISTS video_job_id VARCHAR(36),
        ADD COLUMN IF NOT EXISTS video_status VARCHAR(20),
        ADD COLUMN IF NOT EXISTS video_url VARCHAR(500);

    UPDATE lectures
    SET access_scope = COALESCE(access_scope, 'PRIVATE'),
        business_id = COALESCE(business_id, gen_random_uuid()),
        created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
        current_version_number = COALESCE(current_version_number, 0),
        row_version = COALESCE(row_version, 0),
        status = COALESCE(status, 'DRAFT'),
        updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP),
        video_status = COALESCE(video_status, 'PENDING');

    ALTER TABLE lectures
        ALTER COLUMN access_scope SET DEFAULT 'PRIVATE',
        ALTER COLUMN access_scope SET NOT NULL,
        ALTER COLUMN business_id SET DEFAULT gen_random_uuid(),
        ALTER COLUMN business_id SET NOT NULL,
        ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
        ALTER COLUMN created_at SET NOT NULL,
        ALTER COLUMN current_version_number SET DEFAULT 0,
        ALTER COLUMN current_version_number SET NOT NULL,
        ALTER COLUMN row_version SET DEFAULT 0,
        ALTER COLUMN row_version SET NOT NULL,
        ALTER COLUMN status SET DEFAULT 'DRAFT',
        ALTER COLUMN status SET NOT NULL,
        ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
        ALTER COLUMN updated_at SET NOT NULL,
        ALTER COLUMN video_status SET DEFAULT 'PENDING',
        ALTER COLUMN video_status SET NOT NULL;

    CREATE UNIQUE INDEX IF NOT EXISTS idx_lecture_business_id
        ON lectures (business_id);
    CREATE INDEX IF NOT EXISTS idx_lecture_owner_status
        ON lectures (teacher_id, status);
END
$$;
