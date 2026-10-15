-- ============================================
-- MEMBER Feature 修复
-- 为 crm_member_profile 添加 tenant_id 字段
-- 2026-08-25
-- ============================================

ALTER TABLE crm_member_profile
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID'
    AFTER deleted;

CREATE INDEX idx_profile_tenant_id ON crm_member_profile(tenant_id);
