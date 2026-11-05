-- =============================================
-- I128-d 修复测试账号 sys_user.store_id 全为 NULL
-- 2026-11-05
-- 目的：clerk01 / manager01 / boss01 / operator01 的 sys_user.store_id = NULL，
--       经 RbacContextLoader.java:136-139 归并后 storeIds 为空集，
--       再经 DataScopeService.java:50-53（Lambda 版 :70-73）注入 store_id = -9999
--       → 非 admin 角色查询业务数据全部落空、页面全空。
-- 目标门店：pos_store.id = 1（美业SaaS示范店 / STORE001，status=1，deleted=0，tenant_id=1）
-- 依据：audit/issues/I128-c-scope-overprivilege.md §6.2 遗留观察
-- 编码：UTF-8 无 BOM
-- 幂等：store_id IS NULL 条件下可重复执行（第二次 affected = 0）
-- 回滚：backup/i128/fix-test-user-store-rollback.sql（store_id 置回 NULL）
--
-- 执行方式（PowerShell）：
--   [System.IO.File]::ReadAllText('backup\i128\fix-test-user-store.sql', [System.Text.Encoding]::UTF8) | `
--     mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
-- =============================================
SET NAMES utf8mb4;
START TRANSACTION;

-- ---------- §1 前置校验：目标门店必须存在且有效（不合法则本脚本不产生任何写入） ----------
SELECT '=== PRE-CHECK target store ===' AS phase;
SELECT id, store_name, store_code, status, deleted, tenant_id
FROM pos_store
WHERE id = 1;
-- 期望：1 行，status=1，deleted=0，tenant_id=1
-- 若为 0 行或 deleted=1 → 立即中止，不要继续执行（UPDATE 的 JOIN 守卫同样会拦截）

SELECT '=== PRE-CHECK affected users ===' AS phase;
SELECT id, username, tenant_id, status, store_id, deleted
FROM sys_user
WHERE username IN ('clerk01','manager01','boss01','operator01')
ORDER BY id;
-- 期望：4 行，store_id 均为 NULL

-- ---------- §2 写入（JOIN 目标门店做 fail-fast 守卫 + 排除 admin） ----------
UPDATE sys_user u
JOIN pos_store s
 ON s.id = 1
 AND s.deleted = 0
 AND s.status = 1
 AND s.tenant_id = u.tenant_id
SET u.store_id = s.id
WHERE u.tenant_id = 1
  AND u.deleted = 0
  AND u.username IN ('clerk01','manager01','boss01','operator01')
  AND u.username <> 'admin'
  AND u.store_id IS NULL;
SELECT 'updated_users' AS phase, ROW_COUNT() AS affected;
-- 期望 affected = 4
-- 若 = 0：说明门店 1 不合法 / 租户不匹配 / 账号已绑定 → 手工 ROLLBACK; 不要 COMMIT

COMMIT;

-- ---------- §3 验证 SELECT ----------
SELECT '=== V1 四个测试账号 store_id ===' AS phase;
SELECT id, username, store_id, tenant_id, status, deleted
FROM sys_user
WHERE username IN ('clerk01','manager01','boss01','operator01')
ORDER BY id;
-- 期望：4 行，store_id 全为 1

SELECT '=== V2 admin 未被误伤（应仍为 NULL） ===' AS phase;
SELECT id, username, store_id FROM sys_user WHERE username = 'admin';

SELECT '=== V3 模拟 DataScope 归并结果（storeIds 应为 {1}，不再为空） ===' AS phase;
SELECT u.username,
       GROUP_CONCAT(DISTINCT srd.data_scope)       AS scopes,
       GROUP_CONCAT(DISTINCT srd.data_scope_value) AS scope_values,
       u.store_id                                  AS fallback_store_id,
       CASE WHEN u.store_id IS NULL THEN 'EMPTY->-9999'
            ELSE CONCAT('IN(', u.store_id, ')') END AS effective_store_filter
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role_data_scope srd ON srd.role_id = ur.role_id
WHERE u.username IN ('clerk01','manager01','boss01','operator01')
GROUP BY u.id, u.username, u.store_id
ORDER BY u.username;
-- 期望：4 行 effective_store_filter 均为 IN(1)

SELECT '=== V4 可见数据量（store_id IN (1)） ===' AS phase;
SELECT 'pos_order' AS tbl, COUNT(*) AS visible_rows FROM pos_order WHERE deleted = 0 AND store_id = 1
UNION ALL SELECT 'crm_member', COUNT(*) FROM crm_member WHERE deleted = 0 AND store_id = 1;
