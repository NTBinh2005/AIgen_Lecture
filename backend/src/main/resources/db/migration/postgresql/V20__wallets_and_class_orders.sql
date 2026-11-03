-- V20: Wallets, Class Orders (Pay-per-class), Lecture Watch Progress, and Teacher Withdrawal Requests

-- 0. Cập nhật phương thức thanh toán ví cho bảng payments
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check CHECK (payment_method IN ('SEPAY', 'VNPAY', 'MOMO', 'WALLET'));

-- 1. Thêm giá học phí vào bảng classes
ALTER TABLE classes ADD COLUMN IF NOT EXISTS price NUMERIC(12, 2) DEFAULT 0 NOT NULL;
ALTER TABLE classes DROP CONSTRAINT IF EXISTS classes_price_check;
ALTER TABLE classes ADD CONSTRAINT classes_price_check CHECK (price >= 0);

-- 2. Bảng Ví tiền người dùng (wallets)
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    balance NUMERIC(14, 2) DEFAULT 0 NOT NULL CHECK (balance >= 0),
    pending_balance NUMERIC(14, 2) DEFAULT 0 NOT NULL CHECK (pending_balance >= 0),
    locked_balance NUMERIC(14, 2) DEFAULT 0 NOT NULL CHECK (locked_balance >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);

-- 3. Bảng biến động số dư (wallet_transactions - Sổ cái)
CREATE TABLE IF NOT EXISTS wallet_transactions (
    transaction_id SERIAL PRIMARY KEY,
    wallet_id INT NOT NULL REFERENCES wallets(wallet_id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    balance_after NUMERIC(14, 2) NOT NULL,
    description VARCHAR(500),
    reference_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet ON wallet_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_ref ON wallet_transactions(reference_id);

-- 4. Bảng Đơn mua lớp học (class_orders)
CREATE TABLE IF NOT EXISTS class_orders (
    order_id SERIAL PRIMARY KEY,
    order_code VARCHAR(30) NOT NULL UNIQUE,
    student_id INT NOT NULL REFERENCES users(user_id),
    class_id INT NOT NULL REFERENCES classes(class_id),
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    platform_fee_percent NUMERIC(5, 2) DEFAULT 5.00 NOT NULL,
    platform_fee_amount NUMERIC(12, 2) NOT NULL CHECK (platform_fee_amount >= 0),
    teacher_amount NUMERIC(12, 2) NOT NULL CHECK (teacher_amount >= 0),
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REFUNDED')),
    hold_until TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    refunded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_class_orders_student ON class_orders(student_id);
CREATE INDEX IF NOT EXISTS idx_class_orders_class ON class_orders(class_id);
CREATE INDEX IF NOT EXISTS idx_class_orders_hold_until ON class_orders(hold_until) WHERE status = 'ACTIVE';

-- 5. Bảng Tiến độ xem bài giảng của học sinh trong lớp (class_lecture_progress)
CREATE TABLE IF NOT EXISTS class_lecture_progress (
    student_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id INT NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    lecture_id BIGINT NOT NULL REFERENCES lectures(lecture_id) ON DELETE CASCADE,
    watched_seconds INT DEFAULT 0 NOT NULL CHECK (watched_seconds >= 0),
    total_seconds INT DEFAULT 0 NOT NULL CHECK (total_seconds >= 0),
    is_completed BOOLEAN DEFAULT FALSE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (student_id, class_id, lecture_id)
);

CREATE INDEX IF NOT EXISTS idx_class_lecture_progress_student_class ON class_lecture_progress(student_id, class_id);

-- 6. Bảng Yêu cầu rút tiền của giáo viên (withdrawal_requests)
CREATE TABLE IF NOT EXISTS withdrawal_requests (
    withdrawal_id SERIAL PRIMARY KEY,
    teacher_id INT NOT NULL REFERENCES users(user_id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 50000),
    bank_name VARCHAR(50) NOT NULL,
    bank_account VARCHAR(34) NOT NULL,
    account_holder VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED')),
    admin_note VARCHAR(500),
    sepay_webhook_id BIGINT,
    sepay_reference_code TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_withdrawals_teacher ON withdrawal_requests(teacher_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_status ON withdrawal_requests(status);
