DO $$ BEGIN
IF to_regclass('payments') IS NOT NULL THEN
    ALTER TABLE payments ADD COLUMN IF NOT EXISTS plan_snapshot_json TEXT;
END IF;
IF to_regclass('subscriptions') IS NOT NULL THEN
    ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_snapshot_json TEXT;
END IF;
IF to_regclass('payments') IS NOT NULL AND to_regclass('subscriptions') IS NOT NULL THEN
    CREATE TABLE IF NOT EXISTS subscription_credits (
        payment_id INTEGER PRIMARY KEY REFERENCES payments(payment_id),
        subscription_id BIGINT NOT NULL REFERENCES subscriptions(subscription_id),
        duration_seconds BIGINT NOT NULL,
        refunded BOOLEAN NOT NULL DEFAULT FALSE
    );
    CREATE INDEX IF NOT EXISTS idx_subscription_credits_subscription ON subscription_credits(subscription_id);
END IF;
END $$;
