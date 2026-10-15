-- ============================================
-- RBAC Feature 修复
-- 为 sys_role / sys_log / sys_role_permission 表添加 tenant_id 字段
-- 2026-08-25
-- ============================================

-- --------------------------------------------------------
-- 1. sys_role：添加 tenant_id（平台级角色为 NULL，租户角色为 tenantId）
-- --------------------------------------------------------
ALTER TABLE sys_role
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户（平台级角色为NULL）'
    AFTER status;

CREATE INDEX idx_role_tenant_id ON sys_role(tenant_id);

-- 存量内置角色（SUPER_ADMIN / PLATFORM_ADMIN / ADMIN / BOSS / MANAGER / CLERK / OPERATOR）
-- 保持 tenant_id = NULL，表示平台级角色
-- 租户自定义角色通过 createRole() 写入时填入当前 tenantId

-- --------------------------------------------------------
-- 2. sys_log：添加 tenant_id（用于多租户日志隔离）
-- --------------------------------------------------------
ALTER TABLE sys_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID'
    AFTER id;

CREATE INDEX idx_log_tenant_id ON sys_log(tenant_id);
CREATE INDEX idx_log_tenant_created ON sys_log(tenant_id, created_at);

-- --------------------------------------------------------
-- 3. sys_role_permission：添加 tenant_id（关联到角色所属租户，便于清理）
-- --------------------------------------------------------
ALTER TABLE sys_role_permission
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户'
    AFTER permission_id;

CREATE INDEX idx_rp_tenant_id ON sys_role_permission(tenant_id);

-- 回填现有数据的 tenant_id（通过 role_id 关联 sys_role）
UPDATE sys_role_permission rp
    INNER JOIN sys_role r ON r.id = rp.role_id
SET rp.tenant_id = r.tenant_id
WHERE rp.tenant_id IS NULL;
