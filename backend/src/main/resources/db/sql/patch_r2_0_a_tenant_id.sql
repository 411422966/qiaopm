-- =====================================================
-- R2.0-A 数据隔离补丁：补齐 sys_user / crm_member 的 tenant_id
-- 适用：已经跑过 init_m01_auth.sql / init_m03_member.sql 的库
-- =====================================================

USE meiyetec;

-- ----------------------------------------
-- 1. sys_user 补 tenant_id（实体已有该字段，但 DDL 漏建）
-- ----------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''所属租户ID'' AFTER store_id,
     ADD INDEX idx_tenant_id (tenant_id)',
    'SELECT ''sys_user.tenant_id already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------
-- 2. crm_member 补 tenant_id
-- ----------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_member' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE crm_member ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''所属租户ID'' AFTER store_id,
     ADD INDEX idx_tenant_id (tenant_id)',
    'SELECT ''crm_member.tenant_id already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------
-- 3. 回填：默认租户 1（美业演示租户）
--    真实环境需要按门店所属品牌归属回填，这里仅做最小可用
-- ----------------------------------------
UPDATE sys_user   SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE crm_member SET tenant_id = 1 WHERE tenant_id IS NULL;

-- ----------------------------------------
-- 3. mem_level 补 tenant_id（R2.0-A 触发：/member/page SQL 注入失败）
-- ----------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'mem_level' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE mem_level ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''所属租户ID'' AFTER status,
     ADD INDEX idx_tenant_id (tenant_id)',
    'SELECT ''mem_level.tenant_id already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE mem_level SET tenant_id = 1 WHERE tenant_id IS NULL;

SELECT 'R2.0-A patch applied' AS done;
