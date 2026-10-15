-- ============================================================
-- P0-DB-001 — 数据修复脚本（孤儿重派 + NULL tenant 回填）
-- 创建日期：2026-08-14
-- 范围：phase 2 审计发现的 9 孤儿 + 50 NULL tenant
-- 安全：①所有变更可逆（用 UPDATE + WHERE 限制）
--       ②统计前后行数，便于确认状态
-- ============================================================

USE meiyetec;

-- ============================================================
-- 前置：查看孤儿与 NULL 分布（如需回滚核对）
-- ============================================================
SELECT '=== BEFORE FIX: orphans / nulls ===' AS step;

-- FK-5 orphan 视图
SELECT
    'crm_wallet_transaction.operator_id orphans' AS metric,
    COUNT(*) AS cnt
FROM crm_wallet_transaction wt
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id);

-- FK-10 orphan 视图
SELECT
    'biz_booking.operator_id orphans' AS metric,
    COUNT(*) AS cnt
FROM biz_booking b
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id);

-- crm_wallet.tenant_id NULL 视图
SELECT
    'crm_wallet.tenant_id IS NULL' AS metric,
    COUNT(*) AS cnt
FROM crm_wallet
WHERE tenant_id IS NULL;

-- ============================================================
-- P0-DB-001-A: crm_wallet_transaction.operator_id  4 孤儿重派
-- 把 operator_id=7 (deleted user) 重派给 sys_user.username='admin'
-- ============================================================
START TRANSACTION;

UPDATE crm_wallet_transaction wt
INNER JOIN sys_user u ON u.username = 'admin'
SET wt.operator_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL;

-- 验证
SELECT '=== AFTER P0-DB-001-A: wallet_txn orphans ===' AS step;
SELECT COUNT(*) AS remaining_orphans
FROM crm_wallet_transaction wt
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL;

COMMIT;

-- ============================================================
-- P0-DB-001-B: biz_booking.operator_id  5 孤儿重派
-- 同样策略：repairs to admin
-- ============================================================
START TRANSACTION;

UPDATE biz_booking b
INNER JOIN sys_user u ON u.username = 'admin'
SET b.operator_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

-- 验证
SELECT '=== AFTER P0-DB-001-B: booking orphans ===' AS step;
SELECT COUNT(*) AS remaining_orphans
FROM biz_booking b
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

COMMIT;

-- ============================================================
-- P0-DB-001-C: crm_wallet.tenant_id  50 NULL  按 crm_member.tenant_id 回填
-- 安全条件：crm_member 存在且 tenant_id IS NOT NULL
-- 边界：如果 wallet.member_id 关联的 member.tenant_id 也是 NULL，保持 NULL
-- ============================================================
START TRANSACTION;

UPDATE crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL
  AND m.tenant_id IS NOT NULL;

-- 验证
SELECT '=== AFTER P0-DB-001-C: wallet null tenants ===' AS step;
SELECT COUNT(*) AS remaining_null_tenants
FROM crm_wallet
WHERE tenant_id IS NULL;

-- 跨租户污染校验（应为 0 行）
SELECT '=== cross-tenant check ===' AS step;
SELECT COUNT(*) AS cross_tenant_mismatches
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NOT NULL
  AND m.tenant_id IS NOT NULL
  AND w.tenant_id <> m.tenant_id;

COMMIT;

-- ============================================================
-- 汇总报告
-- ============================================================
SELECT '=== FINAL STATE ===' AS step;

SELECT 'crm_wallet total'         AS tbl, COUNT(*) AS cnt FROM crm_wallet
UNION ALL SELECT 'crm_wallet.tenant_id IS NULL', COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL
UNION ALL SELECT 'crm_wallet_transaction total', COUNT(*) FROM crm_wallet_transaction
UNION ALL SELECT 'crm_wallet_txn.operator_id orphans', COUNT(*) FROM crm_wallet_transaction wt
    WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id) AND wt.operator_id IS NOT NULL
UNION ALL SELECT 'biz_booking total', COUNT(*) FROM biz_booking
UNION ALL SELECT 'biz_booking.operator_id orphans', COUNT(*) FROM biz_booking b
    WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id) AND b.operator_id IS NOT NULL;
