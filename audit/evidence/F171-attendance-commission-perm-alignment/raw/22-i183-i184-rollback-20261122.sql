-- ============================================================
-- I183/I184 权限绑定 — 回滚脚本（D1 已批准，2026-11-22，与 21-*.sql 配套）
--
-- 回滚范围:
--   1) 删除 21-*.sql 写入的 103 个 (role_id, permission_id) 绑定
--      （100 个 D1 绑定 + 3 个 D5a 绑定）
--   2) 删除 D5a 新建的权限码 commission:simulation
--
-- 原则:
--   - 精确删除同一份白名单，不按时间范围删（无 create_time 条件）
--   - 顺序: 先删绑定，再删权限码（避免悬空引用）
--   - 预期: sys_role_permission 1267 -> 1164；sys_permission 510 -> 509
--
-- 执行方式:
--   docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec < 本文件
--
-- 注意: 回滚前建议先跑 21-*.sql 的 PRECHECK 确认现状，
--       再跑本脚本，再跑 21-*.sql 的 PRECHECK 确认已还原。
-- ============================================================

-- ---------- 前置检查：确认当前处于「已绑定」状态 ----------
SELECT 'PRECHECK_total_now' AS chk, COUNT(*) AS total FROM sys_role_permission;
-- 期望：1267

-- ============================================================
-- 步骤 1：删除 D5a 的 3 个绑定（按 permission_code 定位，不依赖硬编码 id）
-- ============================================================
DELETE FROM sys_role_permission
WHERE permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'commission:simulation')
  AND role_id IN (5, 6, 8);

-- ============================================================
-- 步骤 2：删除 100 个 D1 绑定（同一份白名单）
-- ============================================================
-- DELETE attendance:check (permission_id=1453) from role 2, 4, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1453 AND role_id IN (2, 4, 5, 6, 8);
-- DELETE attendance:config:edit (permission_id=1452) from role 5, 8
DELETE FROM sys_role_permission WHERE permission_id = 1452 AND role_id IN (5, 8);
-- DELETE attendance:config:list (permission_id=1451) from role 2, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1451 AND role_id IN (2, 5, 6, 8);
-- DELETE attendance:edit (permission_id=1456) from role 5, 8
DELETE FROM sys_role_permission WHERE permission_id = 1456 AND role_id IN (5, 8);
-- DELETE attendance:leave:approve (permission_id=1458) from role 2, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1458 AND role_id IN (2, 5, 6, 8);
-- DELETE attendance:leave:list (permission_id=1459) from role 2, 4, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1459 AND role_id IN (2, 4, 5, 6, 8);
-- DELETE attendance:leave:submit (permission_id=1457) from role 2, 4, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1457 AND role_id IN (2, 4, 5, 6, 8);
-- DELETE attendance:list (permission_id=1454) from role 2, 4, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1454 AND role_id IN (2, 4, 5, 6, 8);
-- DELETE attendance:stat (permission_id=1455) from role 2, 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1455 AND role_id IN (2, 5, 6, 8);
-- DELETE commission:calculate (permission_id=1328) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1328 AND role_id IN (5, 6, 8);
-- DELETE commission:record:stat (permission_id=1330) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1330 AND role_id IN (5, 6, 8);
-- DELETE commission:rule:add (permission_id=1120) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1120 AND role_id IN (5, 6, 8);
-- DELETE commission:rule:delete (permission_id=1122) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1122 AND role_id IN (5, 6, 8);
-- DELETE commission:rule:detail (permission_id=1331) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1331 AND role_id IN (5, 6, 8);
-- DELETE commission:rule:edit (permission_id=1121) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1121 AND role_id IN (5, 6, 8);
-- DELETE commission:rule:stat (permission_id=1332) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1332 AND role_id IN (5, 6, 8);
-- DELETE commission:rule:status (permission_id=1329) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1329 AND role_id IN (5, 6, 8);
-- DELETE commission:salary:add (permission_id=1335) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1335 AND role_id IN (5, 6, 8);
-- DELETE commission:salary:adjust (permission_id=1336) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1336 AND role_id IN (5, 6, 8);
-- DELETE commission:salary:batch-confirm (permission_id=1339) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1339 AND role_id IN (6, 8);
-- DELETE commission:salary:batch-pay (permission_id=1337) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1337 AND role_id IN (6, 8);
-- DELETE commission:salary:confirm (permission_id=1125) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1125 AND role_id IN (6, 8);
-- DELETE commission:salary:export (permission_id=1333) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1333 AND role_id IN (5, 6, 8);
-- DELETE commission:salary:pay (permission_id=1126) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1126 AND role_id IN (6, 8);
-- DELETE commission:salary:stat (permission_id=1334) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1334 AND role_id IN (5, 6, 8);
-- DELETE commission:settlement:add (permission_id=1345) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1345 AND role_id IN (5, 6, 8);
-- DELETE commission:settlement:batch-confirm (permission_id=1327) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1327 AND role_id IN (6, 8);
-- DELETE commission:settlement:batch-pay (permission_id=1341) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1341 AND role_id IN (6, 8);
-- DELETE commission:settlement:confirm (permission_id=1344) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1344 AND role_id IN (6, 8);
-- DELETE commission:settlement:detail (permission_id=1343) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1343 AND role_id IN (5, 6, 8);
-- DELETE commission:settlement:export (permission_id=1342) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1342 AND role_id IN (5, 6, 8);
-- DELETE commission:settlement:pay (permission_id=1340) from role 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1340 AND role_id IN (6, 8);
-- DELETE commission:settlement:stat (permission_id=1338) from role 5, 6, 8
DELETE FROM sys_role_permission WHERE permission_id = 1338 AND role_id IN (5, 6, 8);

-- ============================================================
-- 步骤 3：删除 D5a 新建的权限码
-- ============================================================
DELETE FROM sys_permission WHERE permission_code = 'commission:simulation';

-- ---------- 后置检查：确认已还原 ----------
SELECT 'POSTCHECK_total' AS chk, COUNT(*) AS total FROM sys_role_permission;
-- 期望：1164
SELECT 'POSTCHECK_byrole' AS chk, rp.role_id, COUNT(*) AS cnt
FROM sys_role_permission rp GROUP BY rp.role_id ORDER BY rp.role_id;
-- 期望：1:118 2:40 4:50 5:278 6:192 7:105 8:381
SELECT 'POSTCHECK_simulation' AS chk, COUNT(*) AS cnt
FROM sys_permission WHERE permission_code = 'commission:simulation';
-- 期望：0
SELECT 'POSTCHECK_perm_total' AS chk, COUNT(*) AS cnt FROM sys_permission;
-- 期望：509
