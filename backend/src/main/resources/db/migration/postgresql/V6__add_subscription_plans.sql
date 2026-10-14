CREATE TABLE IF NOT EXISTS subscription_plans (
    plan_id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(15,2) NOT NULL CHECK (price > 0),
    billing_cycle VARCHAR(20) NOT NULL,
    duration_days INTEGER,
    target_role VARCHAR(20) NOT NULL,
    features_json TEXT NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO subscription_plans
    (code, name, description, price, billing_cycle, duration_days, target_role, features_json)
VALUES
    ('STUDENT_MONTHLY', 'Học viên Premium tháng', 'Truy cập các tính năng Premium dành cho học viên',
     150000, 'MONTHLY', 30, 'STUDENT', '["Xem video bài giảng","Làm bài kiểm tra","Theo dõi tiến độ"]'),
    ('STUDENT_YEARLY', 'Học viên Premium năm', 'Gói Premium 12 tháng dành cho học viên',
     1500000, 'YEARLY', 365, 'STUDENT', '["Toàn bộ quyền Premium học viên","Tiết kiệm hai tháng"]'),
    ('TEACHER_MONTHLY', 'Giáo viên Creator tháng', 'Công cụ AI và video dành cho giáo viên',
     299000, 'MONTHLY', 30, 'TEACHER', '["Tạo bài giảng AI","Tạo quiz AI","Render video"]')
ON CONFLICT (code) DO NOTHING;

DO $$
BEGIN
    IF to_regclass('public.payments') IS NOT NULL THEN
        ALTER TABLE payments ADD COLUMN IF NOT EXISTS plan_id INTEGER;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_subscription_plan') THEN
            ALTER TABLE payments ADD CONSTRAINT fk_payments_subscription_plan
                FOREIGN KEY (plan_id) REFERENCES subscription_plans(plan_id);
        END IF;
    END IF;

    IF to_regclass('public.users') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS subscriptions (
            subscription_id BIGSERIAL PRIMARY KEY,
            user_id INTEGER NOT NULL REFERENCES users(user_id),
            plan_id INTEGER NOT NULL REFERENCES subscription_plans(plan_id),
            status VARCHAR(20) NOT NULL,
            started_at TIMESTAMPTZ NOT NULL,
            expires_at TIMESTAMPTZ,
            cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
            cancelled_at TIMESTAMPTZ,
            last_payment_id INTEGER UNIQUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status
            ON subscriptions(user_id, status);
    END IF;
END $$;
