-- ============================================================
-- fix-i129-rollback.sql
-- 用途：回滚 fix-i129-missing-columns.sql（删除 7 个列）
-- 来源：audit/issues/I129-missing-columns-pos-crm.md
-- 基线 commit：69ae5e0
--
-- ⚠️ 使用前提（必读）
--   1. 仅在 fix-i129-missing-columns.sql 执行后出现问题时使用，正常情况不要跑。
--   2. 删列 = 数据不可恢复。执行前务必确认列内已有数据是否可丢弃。
--   3. 后端实体 PosOrder / PosPayment / PackageItem 仍映射这 7 个字段，
--      单独回滚本 SQL 会让相关接口重新变成 500（I129 原故障态）。
--      因此回滚 = 「回滚 SQL + 回滚代码」两件事，不可只做一半。
--   4. 本文件不含 DROP INDEX：fix 未建任何索引，无需回滚索引。
--
-- 执行方式（PowerShell）：
--   Get-Content backup\fix-i129-rollback.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -p****** --default-character-set=utf8mb4 meiyetec
--
-- 可选：执行前单独跑下面的只读查询，先看清会丢多少数据
--   SELECT COUNT(*) AS rows_with_tendered FROM pos_order   WHERE tendered_amount IS NOT NULL;
--   SELECT COUNT(*) AS rows_with_change   FROM pos_order   WHERE change_amount   IS NOT NULL;
--   SELECT COUNT(*) AS rows_with_tendered FROM pos_payment WHERE tendered_amount IS NOT NULL;
--   SELECT COUNT(*) AS rows_with_change   FROM pos_payment WHERE change_amount   IS NOT NULL;
--   SELECT COUNT(*) AS bundles            FROM crm_package WHERE bundle_type = 1;
--
-- 幂等性：可重复执行；列不存在时走跳过分支，不报错
-- 事务：DDL 隐式提交，不使用 START TRANSACTION
-- ============================================================

-- USE meiyetec;

SELECT IF(DATABASE() IS NULL,
          'ERROR: 未选择数据库，请带库名执行或启用第 30 行 USE 语句',
          CONCAT('target schema = ', DATABASE())) AS precheck;

-- ------------------------------------------------------------
-- 1/7  crm_package.bundle_item_count   （与 fix 第 7 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'crm_package'
      AND COLUMN_NAME  = 'bundle_item_count'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE crm_package DROP COLUMN bundle_item_count',
    'SELECT ''crm_package.bundle_item_count not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2/7  crm_package.bundle_config   （与 fix 第 6 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'crm_package'
      AND COLUMN_NAME  = 'bundle_config'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE crm_package DROP COLUMN bundle_config',
    'SELECT ''crm_package.bundle_config not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3/7  crm_package.bundle_type   （与 fix 第 5 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'crm_package'
      AND COLUMN_NAME  = 'bundle_type'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE crm_package DROP COLUMN bundle_type',
    'SELECT ''crm_package.bundle_type not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4/7  pos_payment.change_amount   （与 fix 第 4 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_payment'
      AND COLUMN_NAME  = 'change_amount'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE pos_payment DROP COLUMN change_amount',
    'SELECT ''pos_payment.change_amount not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5/7  pos_payment.tendered_amount   （与 fix 第 3 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_payment'
      AND COLUMN_NAME  = 'tendered_amount'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE pos_payment DROP COLUMN tendered_amount',
    'SELECT ''pos_payment.tendered_amount not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 6/7  pos_order.change_amount   （与 fix 第 2 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_order'
      AND COLUMN_NAME  = 'change_amount'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE pos_order DROP COLUMN change_amount',
    'SELECT ''pos_order.change_amount not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 7/7  pos_order.tendered_amount   （与 fix 第 1 步对称）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_order'
      AND COLUMN_NAME  = 'tendered_amount'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE pos_order DROP COLUMN tendered_amount',
    'SELECT ''pos_order.tendered_amount not exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 收尾哨兵：出现此行说明脚本已执行到末尾
-- ============================================================
SELECT 'fix-i129-rollback applied' AS done;
