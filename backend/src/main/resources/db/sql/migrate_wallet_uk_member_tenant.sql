-- ============================================================
-- B-8 / D-1 · crm_wallet 联合唯一索引 (member_id, tenant_id)
-- ============================================================
-- 背景：
--   当前唯一索引是 uk_member(member_id) — 隐含假设"一个 member 只属于一个 tenant"。
--   实际场景中同一会员可能在多个租户加入（如总部 SaaS、跨集团会员），导致重复 (member_id, tenant_id) 行。
--
-- 修复路径（避免外键约束失败）：
--   1) 加 uk_member_tenant(member_id, tenant_id)（不冲突，InnoDB 接受两索引并存）
--   2) 删外键 fk_wallet_member（它当前依赖 uk_member）
--   3) 删旧唯一索引 uk_member
--   4) 重建外键 fk_wallet_member（此时会用新的 uk_member_tenant 作为支撑索引）

USE meiyetec;

-- 1. 先查重
SELECT 'STEP 1: check duplicates' AS step;
SELECT member_id, tenant_id, COUNT(*) AS duplicate_count
FROM crm_wallet
WHERE deleted = 0
GROUP BY member_id, tenant_id
HAVING duplicate_count > 1;

-- 2. 加新联合唯一索引 uk_member_tenant (member_id, tenant_id)
SELECT 'STEP 2: add uk_member_tenant' AS step;
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'crm_wallet'
      AND INDEX_NAME = 'uk_member_tenant'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE crm_wallet ADD UNIQUE INDEX uk_member_tenant (member_id, tenant_id)',
    'SELECT 1 AS skipped');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 删外键 fk_wallet_member（它当前依赖 uk_member）
SELECT 'STEP 3: drop FK fk_wallet_member' AS step;
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'crm_wallet'
      AND CONSTRAINT_NAME = 'fk_wallet_member'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE crm_wallet DROP FOREIGN KEY fk_wallet_member',
    'SELECT 1 AS skipped');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 删旧唯一索引 uk_member
SELECT 'STEP 4: drop uk_member' AS step;
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'crm_wallet'
      AND INDEX_NAME = 'uk_member'
);
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE crm_wallet DROP INDEX uk_member',
    'SELECT 1 AS skipped');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 重建外键 fk_wallet_member（此时会自动使用 uk_member_tenant 作为支撑索引）
SELECT 'STEP 5: recreate FK fk_wallet_member' AS step;
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'crm_wallet'
      AND CONSTRAINT_NAME = 'fk_wallet_member'
);
SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE crm_wallet ADD CONSTRAINT fk_wallet_member FOREIGN KEY (member_id) REFERENCES crm_member (id)',
    'SELECT 1 AS skipped');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. 验证
SELECT 'STEP 6: verify' AS step;
SHOW INDEX FROM crm_wallet WHERE Key_name IN ('PRIMARY', 'uk_member', 'uk_member_tenant', 'fk_wallet_member');
SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'crm_wallet'
  AND REFERENCED_TABLE_NAME IS NOT NULL;