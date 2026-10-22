-- QR orders are not paid until SePay confirms the bank transfer.
ALTER TABLE class_orders DROP CONSTRAINT IF EXISTS class_orders_status_check;
ALTER TABLE class_orders
    ADD CONSTRAINT class_orders_status_check
    CHECK (status IN ('PENDING', 'ACTIVE', 'REFUNDED', 'CANCELLED'));

-- Repair orders created by the old flow, which marked SEPAY_QR orders ACTIVE
-- before receiving payment confirmation.
UPDATE class_orders co
SET status = CASE
    WHEN p.status = 'PENDING' THEN 'PENDING'
    WHEN p.status IN ('CANCELLED', 'FAILED') THEN 'CANCELLED'
    ELSE co.status
END
FROM payments p
WHERE co.payment_method = 'SEPAY_QR'
  AND p.transaction_id = co.order_code
  AND p.status IN ('PENDING', 'CANCELLED', 'FAILED');
