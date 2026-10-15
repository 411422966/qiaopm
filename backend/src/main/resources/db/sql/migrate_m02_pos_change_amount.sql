-- M02-04：收银找零服务端处理 — 数据库字段补充
-- PosOrder: 记录本次支付产生的现金找零汇总
-- PosPayment: 记录每笔 cash 支付的实收金额和找零金额

-- 1. pos_order 表新增字段
ALTER TABLE pos_order
    ADD COLUMN IF NOT EXISTS tendered_amount DECIMAL(12,2) DEFAULT NULL COMMENT '现金实收金额（本次支付中 cash 的实收总额）' AFTER refund_count,
    ADD COLUMN IF NOT EXISTS change_amount  DECIMAL(12,2) DEFAULT NULL COMMENT '找零金额 = tendered_amount - 实付 cash 金额';

-- 2. pos_payment 表新增字段
ALTER TABLE pos_payment
    ADD COLUMN IF NOT EXISTS tendered_amount DECIMAL(12,2) DEFAULT NULL COMMENT '现金实收金额（客人给了多少钱）' AFTER status,
    ADD COLUMN IF NOT EXISTS change_amount  DECIMAL(12,2) DEFAULT NULL COMMENT '找零金额（tendered > amount 时产生）' AFTER tendered_amount;

-- 3. 备注索引（便于后续审计追踪）
CREATE INDEX IF NOT EXISTS idx_pos_payment_change ON pos_payment(change_amount);
CREATE INDEX IF NOT EXISTS idx_pos_order_change ON pos_order(change_amount);
