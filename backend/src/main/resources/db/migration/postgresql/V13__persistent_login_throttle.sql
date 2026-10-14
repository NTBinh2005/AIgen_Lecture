CREATE TABLE IF NOT EXISTS login_failures (
    throttle_key VARCHAR(64) PRIMARY KEY,
    failure_count INTEGER NOT NULL,
    last_failure TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_login_failures_last_failure ON login_failures(last_failure);
