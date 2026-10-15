-- ============================================
-- R3.0-B 补丁：跨店核销主链路拦截
-- 2026-08-14
-- 目的：
--   1. crm_package_order 加 store_id 字段（购买门店）
--   2. crm_package_redemption 加 store_id + cross_fee 字段（核销门店 + 跨店手续费）
--   3. 同时为已有数据回填 store_id（取会员所属门店）
-- ============================================

-- 1. crm_package_order.store_id
ALTER TABLE crm_package_order
    ADD COLUMN store_id BIGINT DEFAULT NULL COMMENT '购买门店ID（用于跨店核销判断）' AFTER member_id,
    ADD KEY idx_store (store_id);

-- 2. crm_package_redemption.store_id + cross_fee
ALTER TABLE crm_package_redemption
    ADD COLUMN store_id BIGINT DEFAULT NULL COMMENT '核销门店ID' AFTER member_id,
    ADD COLUMN cross_fee DECIMAL(12,2) DEFAULT 0.00 COMMENT '跨店手续费' AFTER times,
    ADD COLUMN cross_rule_id BIGINT DEFAULT NULL COMMENT '命中的跨店规则ID' AFTER cross_fee;

-- 3. 历史数据回填：从 crm_member.store_id 反查
UPDATE crm_package_order o
INNER JOIN crm_member m ON m.id = o.member_id
SET o.store_id = m.store_id
WHERE o.store_id IS NULL AND m.store_id IS NOT NULL;

-- 4. 验证
SELECT
    (SELECT COUNT(*) FROM crm_package_order WHERE store_id IS NOT NULL) AS package_order_with_store,
    (SELECT COUNT(*) FROM crm_package_order) AS package_order_total,
    (SELECT COUNT(*) FROM crm_package_order WHERE store_id IS NULL) AS package_order_missing_store;
