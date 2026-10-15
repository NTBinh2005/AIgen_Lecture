-- Hibernate (ddl-auto=update) đã tạo CHECK chỉ cho VNPAY/MOMO và không tự cập nhật khi enum thêm SEPAY,
-- khiến INSERT giao dịch SePay bị 409 (vi phạm ràng buộc). Tạo lại CHECK khớp enum PaymentMethod.
DO $$
BEGIN
    IF to_regclass('public.payments') IS NOT NULL THEN
        ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;
        ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check
            CHECK (payment_method IN ('SEPAY', 'VNPAY', 'MOMO'));
    END IF;
END $$;
