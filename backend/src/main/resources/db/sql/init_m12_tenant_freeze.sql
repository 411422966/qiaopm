-- =============================================
-- 美业SaaS系统 - M12 租户过期冻结
-- =============================================
USE meiyetec;

-- =============================================
-- 1. sys_user 加 tenant_id 字段
-- =============================================
ALTER TABLE sys_user
    ADD COLUMN tenant_id BIGINT DEFAULT 1 COMMENT '所属租户ID' AFTER store_id,
    ADD INDEX idx_tenant_id (tenant_id);

-- 兜底：所有历史用户归到租户 1
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL;

-- =============================================
-- 2. sys_tenant 表索引补全（按状态/到期时间查）
-- =============================================
ALTER TABLE sys_tenant
    ADD INDEX idx_status (status),
    ADD INDEX idx_expire_at (expire_at);

-- =============================================
-- 3. 验证
-- =============================================
SELECT '=== sys_user 新字段 ===' AS '';
SELECT id, username, real_name, store_id, tenant_id, status FROM sys_user LIMIT 10;

SELECT '=== sys_tenant 数据 ===' AS '';
SELECT id, tenant_code, tenant_name, status, expire_at FROM sys_tenant;