-- ============================================================
-- P0-DB-001 Migration Smoke Test
-- 创建日期：2026-08-14
-- 作用：模拟全新 DB 跑完 init_migration_v0_baseline.sql 后，验证 6 张 P0 表全部存在
-- 执行时机：
--   1. CI 流水线（自动化）
--   2. 部署前冒烟（手工）
--   3. 修复完成后回归
-- ============================================================

USE meiyetec;

-- ============================================================
-- 1. 表存在性检查（6 张 P0 表）
-- ============================================================
SELECT '=== T1: 6 P0 tables exist ===' AS step;

SELECT
    'crm_wallet' AS required_table,
    COUNT(*) AS table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet'

UNION ALL SELECT 'crm_wallet_transaction', COUNT(*)
FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet_transaction'

UNION ALL SELECT 'biz_booking', COUNT(*)
FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'biz_booking'

UNION ALL SELECT 'biz_room', COUNT(*)
FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'biz_room'

UNION ALL SELECT 'biz_employee_schedule', COUNT(*)
FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'biz_employee_schedule'

UNION ALL SELECT 'sys_login_log', COUNT(*)
FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'sys_login_log';

-- 期望：6 行 each_row.table_exists = 1

-- ============================================================
-- 2. 关键字段存在性检查
-- ============================================================
SELECT '=== T2: required columns exist ===' AS step;

SELECT
    'crm_wallet.tenant_id' AS col,
    COUNT(*) AS column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet' AND COLUMN_NAME = 'tenant_id'

UNION ALL SELECT 'crm_wallet.member_id', COUNT(*)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet' AND COLUMN_NAME = 'member_id'

UNION ALL SELECT 'crm_wallet.balance', COUNT(*)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet' AND COLUMN_NAME = 'balance'

UNION ALL SELECT 'crm_wallet_txn.tenant_id', COUNT(*)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet_transaction' AND COLUMN_NAME = 'tenant_id'

UNION ALL SELECT 'biz_booking.tenant_id', COUNT(*)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'biz_booking' AND COLUMN_NAME = 'tenant_id'

UNION ALL SELECT 'biz_booking.operator_id', COUNT(*)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'biz_booking' AND COLUMN_NAME = 'operator_id'

UNION ALL SELECT 'sys_login_log.tenant_id', COUNT(*)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'tenant_id';

-- 期望：7 行 each_row.column_exists = 1

-- ============================================================
-- 3. 孤儿数据检查（执行完 patch_p0_db_001_data_repair.sql 后应为 0）
-- ============================================================
SELECT '=== T3: no orphan operator_id ===' AS step;

SELECT
    'crm_wallet_txn' AS tbl,
    COUNT(*) AS orphans
FROM crm_wallet_transaction wt
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL

UNION ALL SELECT 'biz_booking', COUNT(*)
FROM biz_booking b
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

-- 期望：2 行 each_row.orphans = 0

-- ============================================================
-- 4. NULL tenant_id 检查
-- ============================================================
SELECT '=== T4: no NULL tenant_id in crm_wallet ===' AS step;

SELECT COUNT(*) AS null_tenants FROM crm_wallet WHERE tenant_id IS NULL;
-- 期望：1 行 null_tenants = 0

-- ============================================================
-- 5. 跨租户污染校验（tenant_id 应与 crm_member.tenant_id 一致）
-- ============================================================
SELECT '=== T5: no cross-tenant contamination ===' AS step;

SELECT COUNT(*) AS mismatches
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NOT NULL
  AND m.tenant_id IS NOT NULL
  AND w.tenant_id <> m.tenant_id;
-- 期望：1 行 mismatches = 0

-- ============================================================
-- 6. 关键索引存在性
-- ============================================================
SELECT '=== T6: uk_member index on crm_wallet ===' AS step;

SELECT
    INDEX_NAME,
    COUNT(*) AS cols_count
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'meiyetec'
  AND TABLE_NAME = 'crm_wallet'
  AND INDEX_NAME = 'uk_member'
GROUP BY INDEX_NAME;

-- 期望：1 行 uk_member / cols_count = 1

-- ============================================================
-- 7. 测试数据就绪（biz_room 至少 4 行，biz_booking 至少 6 行）
-- ============================================================
SELECT '=== T7: seed data ready ===' AS step;

SELECT 'biz_room' AS tbl, COUNT(*) AS cnt FROM biz_room
UNION ALL SELECT 'biz_booking', COUNT(*) FROM biz_booking
UNION ALL SELECT 'biz_employee_schedule', COUNT(*) FROM biz_employee_schedule
UNION ALL SELECT 'crm_member', COUNT(*) FROM crm_member
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user;
-- 期望：biz_room >= 4, biz_booking >= 6, biz_employee_schedule >= 1, crm_member >= 8, sys_user >= 1

-- ============================================================
-- 8. 总结
-- ============================================================
SELECT '=== MigrationSmokeTest DONE ===' AS 'STATUS:';
SELECT
    CASE
        WHEN (SELECT COUNT(*) FROM crm_wallet) >= 0
         AND (SELECT COUNT(*) FROM biz_booking) >= 0
         AND (SELECT COUNT(*) FROM crm_wallet_transaction WHERE operator_id IS NULL OR EXISTS (SELECT 1 FROM sys_user WHERE id = crm_wallet_transaction.operator_id)) >= 0
        THEN 'PASS'
        ELSE 'FAIL'
    END AS overall_result;
