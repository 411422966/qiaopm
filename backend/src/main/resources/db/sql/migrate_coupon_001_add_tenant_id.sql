-- ============================================
-- COUPON Feature 修复
-- 为 market_coupon / market_coupon_grant 添加 tenant_id 字段
-- 2026-08-26
-- ============================================

-- 1. market_coupon
ALTER TABLE market_coupon
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER store_id;

ALTER TABLE market_coupon
    ADD CONSTRAINT fk_coupon_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_coupon_tenant_id ON market_coupon(tenant_id);

-- 2. market_coupon_grant
ALTER TABLE market_coupon_grant
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER member_id;

ALTER TABLE market_coupon_grant
    ADD CONSTRAINT fk_coupon_grant_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_coupon_grant_tenant_id ON market_coupon_grant(tenant_id);

-- 3. 存量数据回填（通过 store_id → pos_store → tenant_id）
UPDATE market_coupon c
    INNER JOIN pos_store s ON c.store_id = s.id
SET c.tenant_id = s.tenant_id
WHERE c.tenant_id IS NULL;

UPDATE market_coupon_grant g
    INNER JOIN market_coupon c ON g.coupon_id = c.id
SET g.tenant_id = c.tenant_id
WHERE g.tenant_id IS NULL AND g.deleted = 0;