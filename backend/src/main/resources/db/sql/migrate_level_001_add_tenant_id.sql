-- ============================================
-- LEVEL Feature 修复
-- 为 biz_level_benefit 添加 tenant_id 字段
-- 2026-08-25
-- ============================================

ALTER TABLE biz_level_benefit
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID'
    AFTER deleted;

CREATE INDEX idx_level_benefit_tenant_id ON biz_level_benefit(tenant_id);
