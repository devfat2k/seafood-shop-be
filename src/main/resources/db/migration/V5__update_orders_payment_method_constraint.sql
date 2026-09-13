-- ============================================================
-- V5: Đồng bộ check constraint cột payment_method bảng orders với enum PaymentMethod
-- ============================================================

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_method_check;

ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check
    CHECK (payment_method IN ('VNPAY', 'COD', 'MOMO', 'ZALOPAY', 'BANK', 'WALLET', 'CASH'));
