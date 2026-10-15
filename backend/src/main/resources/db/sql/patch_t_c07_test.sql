-- R2.0-C T-C07 测试用：补缺失列
ALTER TABLE pos_refund ADD COLUMN IF NOT EXISTS cross_group_id VARCHAR(64) NULL AFTER version;
ALTER TABLE pos_refund ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0 AFTER cross_group_id;

ALTER TABLE pos_order_item ADD COLUMN IF NOT EXISTS final_price DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER discount_amount;
