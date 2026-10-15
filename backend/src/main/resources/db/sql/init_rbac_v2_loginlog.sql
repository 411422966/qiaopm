-- R2.0-A 跨租户日志归类（依赖 init_rbac_v2.sql）
USE meiyetec;

ALTER TABLE sys_login_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER user_id,
    ADD INDEX idx_tenant_id (tenant_id);

-- 兜底：历史登录日志关联到管理员所在租户 1
UPDATE sys_login_log l
INNER JOIN sys_user u ON l.user_id = u.id
SET l.tenant_id = u.tenant_id
WHERE l.tenant_id IS NULL AND l.user_id IS NOT NULL;

-- 仍未匹配的租户为 NULL（登录失败用户不存在），保持 NULL 即可
SELECT '=== sys_login_log tenant_id 字段 ===' AS '';
SELECT COUNT(*) AS total, SUM(tenant_id IS NULL) AS null_tenant_rows FROM sys_login_log;
