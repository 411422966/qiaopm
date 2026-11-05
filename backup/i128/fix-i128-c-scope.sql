-- =============================================
-- I128-c 修复 sys_role_data_scope 越权（manager01 跨租户）
-- 2026-11-05
-- 依据：init_rbac_v2.sql:242,245（FINANCE/PLATFORM_ADMIN 的 scope 行）
--       migrate_r3_0_c_menu_role.sql:22-34（CLERK/MANAGER/BOSS/OPERATOR/ADMIN 正确 scope）
-- 冲突事实：id=4 挂 CLERK(role_id=4) 占唯一键 uk_role_scope(role_id,data_scope)，
--           导致 CLERK 正确行被 INSERT IGNORE 静默跳过；
--           id=5 挂 MANAGER(role_id=5) 使其归并出 ALL → 跨租户放行
-- 编码：UTF-8 无 BOM
-- =============================================
SET NAMES utf8mb4;
START TRANSACTION;

-- ---------- §1 前置快照（修复前全表留证） ----------
SELECT '=== PRE-FIX sys_role_data_scope ===' AS phase;
SELECT srd.id, srd.role_id, sr.role_code, srd.data_scope, srd.data_scope_value,
       srd.remark, HEX(srd.remark) AS remark_hex
FROM sys_role_data_scope srd
LEFT JOIN sys_role sr ON sr.id = srd.role_id
ORDER BY srd.id;

-- ---------- §2 精确删除两条脏行（条件三重保护，内容不符则不删） ----------
-- id=5 : MANAGER 误挂 PLATFORM_ADMIN 的 ALL 行 → 越权根因
DELETE FROM sys_role_data_scope
WHERE id = 5 AND role_id = 5 AND data_scope = 'ALL';
SELECT 'deleted_id5' AS phase, ROW_COUNT() AS affected;

-- id=4 : CLERK 误挂 FINANCE 的 STORE 行（含错误 remark「财务仅门店数据」）
DELETE FROM sys_role_data_scope
WHERE id = 4 AND role_id = 4 AND data_scope = 'STORE';
SELECT 'deleted_id4' AS phase, ROW_COUNT() AS affected;

-- ---------- §3 重建 CLERK 的正确数据范围（补回被 INSERT IGNORE 跳过的那条） ----------
INSERT INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '店员仅当前门店数据'
FROM sys_role r
WHERE r.role_code = 'CLERK'
  AND NOT EXISTS (
 SELECT 1 FROM (SELECT role_id, data_scope FROM sys_role_data_scope) x
        WHERE x.role_id = r.id AND x.data_scope = 'STORE'
      );
SELECT 'inserted_clerk' AS phase, ROW_COUNT() AS affected;

COMMIT;

-- ---------- §4 提交后立即快照 ----------
SELECT '=== POST-FIX sys_role_data_scope ===' AS phase;
SELECT srd.id, srd.role_id, sr.role_code, srd.data_scope, srd.remark
FROM sys_role_data_scope srd
LEFT JOIN sys_role sr ON sr.id = srd.role_id
ORDER BY srd.id;
