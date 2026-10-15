-- ============================================================
-- 美业 SaaS - V0 Baseline 迁移入口（P0-DB-001 收口）
-- 创建日期：2026-08-14
-- 目的：把散落在项目根目录 fix_*.sql 的 DDL 全部纳入版本化迁移
-- 适用范围：全新部署 / 数据库重建 / 灰度前基线对齐
-- ============================================================
--
-- 使用方式（任选其一）：
--   方案 A（mysql 客户端批处理）：
--     mysql -h <host> -P 3306 -u root -p meiyetec < init_migration_v0_baseline.sql
--
--   方案 B（按顺序执行 init_m*.sql）：
--     1. init_m01_auth.sql     -- 用户/角色/权限 + sys_login_log
--     2. init_m02_basic.sql    -- 门店/员工/等级/分类 + sys_log
--     3. init_m03_member.sql   -- 会员/标签/档案 + crm_wallet + crm_wallet_transaction
--     4. init_m05_package.sql  -- 套餐
--     5. init_m06_booking.sql  -- 预约/房间/排班
--     6. init_m07_pos.sql      -- POS
--     7. init_m08_stock.sql    -- 库存
--     8. init_m09_commission*.sql -- 提成
--     ... 其它 init_m*.sql
--
-- 历史已被本脚本取代的根目录 ad-hoc 脚本：
--   - fix_missing_tables.sql         （sys_log / sys_login_log / crm_wallet / crm_wallet_transaction）
--   - fix_booking_calendar_tables.sql （biz_booking / biz_employee_schedule / biz_room）
-- ============================================================

USE meiyetec;

-- ============================================================
-- 1. 用户/角色/权限 + sys_login_log
-- ============================================================
SOURCE init_m01_auth.sql;

-- ============================================================
-- 2. 门店/员工/等级/分类 + sys_log
-- ============================================================
SOURCE init_m02_basic.sql;

-- ============================================================
-- 3. 会员/标签/档案 + crm_wallet + crm_wallet_transaction
-- ============================================================
SOURCE init_m03_member.sql;

-- ============================================================
-- 4. 套餐（P0-DB-001 不涉及，但链路要保留）
-- ============================================================
SOURCE init_m05_package.sql;
SOURCE init_m05_package_part2.sql;
SOURCE init_m05_seed.sql;

-- ============================================================
-- 5. 预约/房间/排班
-- ============================================================
SOURCE init_m06_booking.sql;

-- ============================================================
-- 6. POS
-- ============================================================
SOURCE init_m07_pos.sql;

-- ============================================================
-- 7. 库存
-- ============================================================
SOURCE init_m08_stock.sql;

-- ============================================================
-- 8. 提成
-- ============================================================
SOURCE init_m09_commission.sql;
SOURCE init_m09_commission_v2.sql;

-- ============================================================
-- 9. 其它后续模块（按需启用）
-- ============================================================
-- SOURCE init_m12_tenant_freeze.sql;
-- SOURCE init_m12_dcep_qualification.sql;

-- ============================================================
-- 10. P0-DB-001 数据修复（孤儿重派 + NULL tenant 回填）
-- ============================================================
-- 该脚本是幂等的：在已修复的数据库上再次执行不会出现副作用
-- 全新数据库上：孤儿数=0，NULL tenant=0，所有 UPDATE 影响 0 行
SOURCE patch_p0_db_001_data_repair.sql;

-- ============================================================
-- 最终验收
-- ============================================================
SELECT '=== V0 Baseline 迁移完成 ===' AS step;

-- 6 张 P0 表都应存在
SELECT 'crm_wallet'               AS tbl, COUNT(*) AS exists_check FROM crm_wallet
UNION ALL SELECT 'crm_wallet_transaction',     COUNT(*) FROM crm_wallet_transaction
UNION ALL SELECT 'biz_booking',                COUNT(*) FROM biz_booking
UNION ALL SELECT 'biz_room',                   COUNT(*) FROM biz_room
UNION ALL SELECT 'biz_employee_schedule',      COUNT(*) FROM biz_employee_schedule
UNION ALL SELECT 'sys_login_log',              COUNT(*) FROM sys_login_log
UNION ALL SELECT 'sys_log',                    COUNT(*) FROM sys_log;

-- 孤儿检查（应为 0）
SELECT '=== FK-5 wallet_txn orphans ===' AS step;
SELECT COUNT(*) AS orphans
FROM crm_wallet_transaction wt
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL;

SELECT '=== FK-10 booking orphans ===' AS step;
SELECT COUNT(*) AS orphans
FROM biz_booking b
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

-- NULL tenant 检查（应为 0）
SELECT '=== crm_wallet NULL tenant ===' AS step;
SELECT COUNT(*) AS null_tenants FROM crm_wallet WHERE tenant_id IS NULL;
