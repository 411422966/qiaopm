-- =============================================
-- I128-c 验证脚本
-- 2026-11-05
-- 编码：UTF-8 无 BOM
-- =============================================
SET NAMES utf8mb4;

-- ---------- A 段：剩余全表 ----------
SELECT '=== A. 剩余 sys_role_data_scope ===' AS section;
SELECT srd.id, srd.role_id, sr.role_code, srd.data_scope, srd.data_scope_value, srd.remark
FROM sys_role_data_scope srd
LEFT JOIN sys_role sr ON sr.id = srd.role_id
ORDER BY srd.id;

-- ---------- B 段：1 对 1 断言（期望每个 role 恰好 1 条；有 0 条或 >1 条即为失败） ----------
SELECT '=== B. 每角色映射条数（期望全为 1） ===' AS section;
SELECT sr.id AS role_id, sr.role_code, COUNT(srd.id) AS scope_cnt
FROM sys_role sr
LEFT JOIN sys_role_data_scope srd ON srd.role_id = sr.id
GROUP BY sr.id, sr.role_code
HAVING COUNT(srd.id) <> 1;

-- ---------- C 段：期望值逐角色比对（mismatch 必须为空） ----------
SELECT '=== C. 期望值比对（mismatch 必须为空） ===' AS section;
SELECT sr.role_code, srd.data_scope AS actual_scope, srd.remark AS actual_remark,
       CASE sr.role_code
         WHEN 'SUPER_ADMIN'  THEN 'ALL' WHEN 'ADMIN'      THEN 'ALL'
         WHEN 'STORE_ADMIN'  THEN 'STORE' WHEN 'EMPLOYEE'   THEN 'STORE'
         WHEN 'CLERK'        THEN 'STORE' WHEN 'MANAGER'    THEN 'STORE'
         WHEN 'BOSS'         THEN 'STORE' WHEN 'OPERATOR'   THEN 'STORE'
       END AS expected_scope,
       CASE WHEN srd.data_scope <> CASE sr.role_code
         WHEN 'SUPER_ADMIN'  THEN 'ALL'   WHEN 'ADMIN'      THEN 'ALL'
         WHEN 'STORE_ADMIN'  THEN 'STORE' WHEN 'EMPLOYEE'   THEN 'STORE'
         WHEN 'CLERK'        THEN 'STORE' WHEN 'MANAGER'    THEN 'STORE'
         WHEN 'BOSS'         THEN 'STORE' WHEN 'OPERATOR'   THEN 'STORE'
       END THEN 'MISMATCH' ELSE 'OK' END AS verdict
FROM sys_role sr
LEFT JOIN sys_role_data_scope srd ON srd.role_id = sr.id
ORDER BY sr.id;

-- ---------- D 段：安全检查 —— 模拟 RbacContextLoader 归并后的有效 scope ----------
SELECT '=== D. 有效 scope 模拟（manager01 必须为 STORE，不得为 ALL） ===' AS section;
SELECT u.id AS user_id, u.username,
       GROUP_CONCAT(srd.id ORDER BY srd.id) AS scope_ids,
       GROUP_CONCAT(srd.data_scope ORDER BY srd.id) AS scopes,
       CASE WHEN SUM(srd.data_scope = 'ALL') > 0 THEN 'ALL'
            WHEN SUM(srd.data_scope = 'BRAND') > 0 THEN 'BRAND'
            WHEN SUM(srd.data_scope = 'STORE') > 0 THEN 'STORE'
            ELSE 'SELF' END AS effective_scope,
       CASE WHEN SUM(srd.data_scope = 'ALL') > 0 AND sr.role_code NOT IN ('SUPER_ADMIN','ADMIN')
            THEN 'VIOLATION-cross-tenant' ELSE 'ok' END AS verdict
FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
JOIN sys_role sr ON sr.id = ur.role_id
JOIN sys_role_data_scope srd ON srd.role_id = ur.role_id
GROUP BY u.id, u.username, sr.role_code
ORDER BY u.id;

-- ---------- E 段：白名单外禁止出现 ALL（期望为空） ----------
SELECT '=== E. 非平台角色携带 ALL（期望为空） ===' AS section;
SELECT srd.id, srd.role_id, sr.role_code, srd.data_scope, srd.remark
FROM sys_role_data_scope srd
JOIN sys_role sr ON sr.id = srd.role_id
WHERE srd.data_scope = 'ALL'
  AND sr.role_code NOT IN ('SUPER_ADMIN','ADMIN');

-- ---------- F 段：孤儿行检查（role_id 不存在于 sys_role，期望为空） ----------
SELECT '=== F. 孤儿 scope 行（期望为空） ===' AS section;
SELECT srd.id, srd.role_id, srd.data_scope, srd.remark
FROM sys_role_data_scope srd
LEFT JOIN sys_role sr ON sr.id = srd.role_id
WHERE sr.id IS NULL;

-- ---------- G 段：确认 FINANCE / PLATFORM_ADMIN 角色确不存在（无需重建） ----------
SELECT '=== G. FINANCE / PLATFORM_ADMIN 是否存在（期望无行） ===' AS section;
SELECT id, role_code, role_name FROM sys_role
WHERE role_code IN ('FINANCE','PLATFORM_ADMIN');

-- ---------- H 段：目标行终态确认 ----------
SELECT '=== H. id=4 / id=5 必须不存在，CLERK 必须有 STORE 行，MANAGER 不得有 ALL 行 ===' AS section;
SELECT (SELECT COUNT(*) FROM sys_role_data_scope WHERE id IN (4,5)) AS dirty_rows_remaining,
       (SELECT COUNT(*) FROM sys_role_data_scope srd
          JOIN sys_role sr ON sr.id = srd.role_id
         WHERE sr.role_code='CLERK' AND srd.data_scope='STORE') AS clerk_store_rows,
       (SELECT COUNT(*) FROM sys_role_data_scope srd
          JOIN sys_role sr ON sr.id = srd.role_id
         WHERE sr.role_code='MANAGER' AND srd.data_scope='ALL') AS manager_all_rows;
