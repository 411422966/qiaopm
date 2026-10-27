-- ============================================================
-- fix-i129-verify.sql
-- 用途：只读校验 I129 的 7 个列是否补齐、类型/可空性是否正确
-- 来源：audit/issues/I129-missing-columns-pos-crm.md
-- 基线 commit：69ae5e0
-- 特性：全部为 SELECT，无 DDL / 无 DML，可对生产只读账号执行
--
-- 执行方式（PowerShell）：
--   Get-Content backup\fix-i129-verify.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -p****** --default-character-set=utf8mb4 meiyetec
--
-- 判读：§1 的 result 列全部 OK，且 §4 的 found_columns = 7，才算通过
-- ============================================================

-- ------------------------------------------------------------
-- §1  期望列 vs 实际列（逐列比对类型）
-- ------------------------------------------------------------
SELECT e.expect_table  AS table_name,
       e.expect_column AS column_name,
       e.expect_type   AS expect_type,
       c.COLUMN_TYPE   AS actual_type,
       c.IS_NULLABLE,
       c.COLUMN_DEFAULT,
       CASE WHEN c.COLUMN_NAME IS NULL                       THEN 'MISSING'
            WHEN LOWER(c.COLUMN_TYPE) <> e.expect_type       THEN 'TYPE_MISMATCH'
            ELSE 'OK' END AS result
FROM (
              SELECT 'pos_order'   AS expect_table, 'tendered_amount'   AS expect_column, 'decimal(12,2)' AS expect_type
    UNION ALL SELECT 'pos_order',   'change_amount',     'decimal(12,2)'
    UNION ALL SELECT 'pos_payment', 'tendered_amount',   'decimal(12,2)'
    UNION ALL SELECT 'pos_payment', 'change_amount',     'decimal(12,2)'
    UNION ALL SELECT 'crm_package', 'bundle_type',       'int'
    UNION ALL SELECT 'crm_package', 'bundle_config',     'text'
    UNION ALL SELECT 'crm_package', 'bundle_item_count', 'int'
) e
LEFT JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA = DATABASE()
      AND c.TABLE_NAME   = e.expect_table
      AND c.COLUMN_NAME  = e.expect_column
ORDER BY e.expect_table, e.expect_column;

-- ------------------------------------------------------------
-- §2  NOT NULL / 默认值 专项（只校验拍板要 NOT NULL DEFAULT 0 的两列）
-- ------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'crm_package'
  AND COLUMN_NAME IN ('bundle_type', 'bundle_item_count')
ORDER BY COLUMN_NAME;

-- ------------------------------------------------------------
-- §3  bundle_config 必须可空（非组合套餐会显式写 NULL）
-- ------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE, DATA_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'crm_package'
  AND COLUMN_NAME  = 'bundle_config';

-- ------------------------------------------------------------
-- §4  汇总判定：7 个列到齐才算 PASS
-- ------------------------------------------------------------
SELECT COUNT(*)                 AS found_columns,
       7                        AS expected_columns,
       IF(COUNT(*) = 7, 'PASS', 'FAIL') AS result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ( (TABLE_NAME = 'pos_order'   AND COLUMN_NAME IN ('tendered_amount', 'change_amount'))
     OR (TABLE_NAME = 'pos_payment' AND COLUMN_NAME IN ('tendered_amount', 'change_amount'))
     OR (TABLE_NAME = 'crm_package' AND COLUMN_NAME IN ('bundle_type', 'bundle_config', 'bundle_item_count')) );

-- ------------------------------------------------------------
-- §5  三张表行数留痕（与执行前对比，确认 DDL 未影响数据）
-- ------------------------------------------------------------
SELECT 'pos_order'   AS table_name, COUNT(*) AS row_count FROM pos_order
UNION ALL
SELECT 'pos_payment' AS table_name, COUNT(*) AS row_count FROM pos_payment
UNION ALL
SELECT 'crm_package' AS table_name, COUNT(*) AS row_count FROM crm_package;

-- ------------------------------------------------------------
-- §6  索引缺席校验（拍板项 3：3 个索引不建，全部应为 0）
-- ------------------------------------------------------------
SELECT 'idx_pos_payment_change' AS index_name, COUNT(*) AS actual_count, 0 AS expect_count
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pos_payment' AND INDEX_NAME = 'idx_pos_payment_change'
UNION ALL
SELECT 'idx_pos_order_change', COUNT(*), 0
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pos_order' AND INDEX_NAME = 'idx_pos_order_change'
UNION ALL
SELECT 'idx_bundle_type', COUNT(*), 0
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_package' AND INDEX_NAME = 'idx_bundle_type';
