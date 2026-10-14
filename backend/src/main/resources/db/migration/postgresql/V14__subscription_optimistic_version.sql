DO $$ BEGIN
IF to_regclass('subscriptions') IS NOT NULL THEN
    ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;
END IF;
END $$;
