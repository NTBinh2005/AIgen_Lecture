-- Nullable for historical refunds: never invent recipients or completed transfers.
DO $$
BEGIN
    IF to_regclass('public.refunds') IS NOT NULL THEN
        ALTER TABLE refunds ADD COLUMN IF NOT EXISTS bank_name VARCHAR(30);
        ALTER TABLE refunds ADD COLUMN IF NOT EXISTS bank_account VARCHAR(34);
        ALTER TABLE refunds ADD COLUMN IF NOT EXISTS account_holder VARCHAR(150);
        ALTER TABLE refunds ADD COLUMN IF NOT EXISTS sepay_webhook_id BIGINT;
        ALTER TABLE refunds ADD COLUMN IF NOT EXISTS sepay_reference_code TEXT;
        ALTER TABLE refunds ADD COLUMN IF NOT EXISTS sepay_webhook_payload TEXT;
        CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_sepay_webhook_id ON refunds(sepay_webhook_id);
    END IF;
END $$;
