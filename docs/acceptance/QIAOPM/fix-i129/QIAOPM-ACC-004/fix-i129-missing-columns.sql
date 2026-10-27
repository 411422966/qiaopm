-- ============================================================
-- fix-i129-missing-columns.sql
-- 用途：补齐 I129 记录缺失的 7 个物理列
--       pos_order   : tendered_amount / change_amount
--       pos_payment : tendered_amount / change_amount
--       crm_package : bundle_type / bundle_config / bundle_item_count
-- 来源：audit/issues/I129-missing-columns-pos-crm.md
-- 基线 commit：69ae5e0
-- 前置：I130（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，本文件改用判存 + PREPARE）
-- 参考实现：backend/src/main/resources/db/sql/patch_r2_0_a_tenant_id.sql:10-19
--
-- 执行方式（PowerShell，库名由命令行给出）：
--   Get-Content backup\fix-i129-missing-columns.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -p****** --default-character-set=utf8mb4 meiyetec
--   若命令行未带库名，取消第 24 行 USE 语句的注释并填实际库名
--
-- 验证：fix-i129-verify.sql     回滚：fix-i129-rollback.sql
-- 幂等性：可重复执行；列已存在时走哨兵分支，不报错
-- 索引：本文件不建任何索引（change_amount / bundle_type 选择性极低、无代码依赖，
--       如未来出现对应查询需求再评估）
-- 事务：DDL 隐式提交，不使用 START TRANSACTION
-- ============================================================

-- USE meiyetec;

SELECT IF(DATABASE() IS NULL,
          'ERROR: 未选择数据库，请带库名执行或启用第 24 行 USE 语句',
          CONCAT('target schema = ', DATABASE())) AS precheck;

-- ------------------------------------------------------------
-- 1/7  pos_order.tendered_amount   DECIMAL(12,2) NULL
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_order'
      AND COLUMN_NAME  = 'tendered_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pos_order ADD COLUMN tendered_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''现金实收金额（本次支付中 cash 的实收总额）'' AFTER refund_count',
    'SELECT ''pos_order.tendered_amount already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2/7  pos_order.change_amount   DECIMAL(12,2) NULL
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_order'
      AND COLUMN_NAME  = 'change_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pos_order ADD COLUMN change_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''找零金额 = tendered_amount - 实付 cash 金额'' AFTER tendered_amount',
    'SELECT ''pos_order.change_amount already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3/7  pos_payment.tendered_amount   DECIMAL(12,2) NULL
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_payment'
      AND COLUMN_NAME  = 'tendered_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pos_payment ADD COLUMN tendered_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''现金实收金额（客人给了多少钱）'' AFTER status',
    'SELECT ''pos_payment.tendered_amount already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4/7  pos_payment.change_amount   DECIMAL(12,2) NULL
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'pos_payment'
      AND COLUMN_NAME  = 'change_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pos_payment ADD COLUMN change_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''找零金额（tendered > amount 时产生）'' AFTER tendered_amount',
    'SELECT ''pos_payment.change_amount already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5/7  crm_package.bundle_type   INT NOT NULL DEFAULT 0
--      拍板项 2/1：INT（不改 TINYINT）；NOT NULL DEFAULT 0（脚本原文可空）
--      不加 AFTER，追加到表尾 —— 便于 MySQL 8.0 INSTANT ADD COLUMN
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'crm_package'
      AND COLUMN_NAME  = 'bundle_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE crm_package ADD COLUMN bundle_type INT NOT NULL DEFAULT 0 COMMENT ''组合类型：0=单商品 1=组合（含子项目）''',
    'SELECT ''crm_package.bundle_type already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 6/7  crm_package.bundle_config   TEXT NULL
--      拍板项 2：保持可空（m04 原文无 DEFAULT 子句，可空列默认即 NULL）
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'crm_package'
      AND COLUMN_NAME  = 'bundle_config'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE crm_package ADD COLUMN bundle_config TEXT COMMENT ''组合子项目 JSON：[{serviceId, serviceName, times, unitPrice}, ...]''',
    'SELECT ''crm_package.bundle_config already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 7/7  crm_package.bundle_item_count   INT NOT NULL DEFAULT 0
-- ------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'crm_package'
      AND COLUMN_NAME  = 'bundle_item_count'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE crm_package ADD COLUMN bundle_item_count INT NOT NULL DEFAULT 0 COMMENT ''组合子项目总数（冗余）''',
    'SELECT ''crm_package.bundle_item_count already exists'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 收尾哨兵：出现此行说明脚本已执行到末尾
-- ============================================================

SELECT 'fix-i129-missing-columns applied' AS done;

