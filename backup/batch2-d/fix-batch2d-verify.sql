-- =============================================================================
-- QIAOPM 批次二-D 修复验证脚本（只读）
-- 用法：
--   Get-Content backup\batch2-d\fix-batch2d-verify.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
--
-- §A 修复前基线（执行 fix 前后各跑一次，用于对比）
-- §B 修复后计数核对（期望 ADMIN 33 / BOSS 25 / MANAGER 8 / OPERATOR 8）
-- §C 明细核对 + 全局总量
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §A.1 本批 33 码存在性：期望 33
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS a1_codes_exist_should_be_33
FROM sys_permission
WHERE deleted = 0
  AND permission_code IN (
    'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
    'report:export:list','report:export:add','report:export:download','report:task',
    'sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:permission:list',
    'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze',
    'sys:log:list','sys:log:export','sys:log:clear',
    'sys:login-log:list','sys:login-log:export','sys:login-log:clear',
    'sys:config:list','sys:config:edit',
    'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
    'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
  );

-- -----------------------------------------------------------------------------
-- §A.2 修复前本批绑定数（修复前应为 0，修复后为 74）
-- -----------------------------------------------------------------------------
SELECT r.role_code, COUNT(*) AS batch2d_binding_count
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','OPERATOR')
  AND p.permission_code IN (
    'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
    'report:export:list','report:export:add','report:export:download','report:task',
    'sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:permission:list',
    'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze',
    'sys:log:list','sys:log:export','sys:log:clear',
    'sys:login-log:list','sys:login-log:export','sys:login-log:clear',
    'sys:config:list','sys:config:edit',
    'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
    'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
  )
GROUP BY r.role_code
ORDER BY r.role_code;

-- -----------------------------------------------------------------------------
-- §B 修复后计数核对（关键自检）
--   期望：ADMIN 33 / BOSS 25 / MANAGER 8 / OPERATOR 8，合计 74
-- -----------------------------------------------------------------------------
SELECT r.role_code,
       COUNT(*) AS bound_codes,
       CASE r.role_code
         WHEN 'ADMIN'   THEN 'expect 33'
         WHEN 'BOSS'    THEN 'expect 25'
         WHEN 'MANAGER' THEN 'expect 8'
         WHEN 'OPERATOR' THEN 'expect 8'
       END AS expectation,
       CASE
         WHEN (r.role_code = 'ADMIN'   AND COUNT(*) = 33)
           OR (r.role_code = 'BOSS'    AND COUNT(*) = 25)
           OR (r.role_code = 'MANAGER' AND COUNT(*) = 8)
           OR (r.role_code = 'OPERATOR' AND COUNT(*) = 8)
         THEN 'PASS' ELSE 'FAIL'
       END AS verdict
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','OPERATOR')
  AND p.permission_code IN (
    'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
    'report:export:list','report:export:add','report:export:download','report:task',
    'sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:permission:list',
    'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze',
    'sys:log:list','sys:log:export','sys:log:clear',
    'sys:login-log:list','sys:login-log:export','sys:login-log:clear',
    'sys:config:list','sys:config:edit',
    'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
    'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
  )
GROUP BY r.role_code
ORDER BY r.role_code;

-- -----------------------------------------------------------------------------
-- §C.1 明细（按角色 × 码列出，用于人工核对矩阵）
-- -----------------------------------------------------------------------------
SELECT r.role_code, p.permission_code
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','OPERATOR')
  AND p.permission_code IN (
    'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
    'report:export:list','report:export:add','report:export:download','report:task',
    'sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:permission:list',
    'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze',
    'sys:log:list','sys:log:export','sys:log:clear',
    'sys:login-log:list','sys:login-log:export','sys:login-log:clear',
    'sys:config:list','sys:config:edit',
    'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
    'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
  )
ORDER BY r.role_code, p.permission_code;

-- -----------------------------------------------------------------------------
-- §C.2 全局绑定总量（修复前 593 → 修复后 667）
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS total_role_permission FROM sys_role_permission;

-- §C.3 各角色总绑定量（参考）
SELECT r.role_code, COUNT(*) AS total_bound
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
GROUP BY r.role_code
ORDER BY total_bound DESC;

-- -----------------------------------------------------------------------------
-- §C.4 重复绑定检查（唯一键 uk_role_permission 保证为 0）
-- -----------------------------------------------------------------------------
SELECT role_id, permission_id, COUNT(*) AS dup
FROM sys_role_permission
GROUP BY role_id, permission_id
HAVING COUNT(*) > 1;

-- -----------------------------------------------------------------------------
-- §C.5 孤儿行检查（permission_id 在 sys_permission 中不存在 → 期望 0）
-- -----------------------------------------------------------------------------
SELECT rp.id, rp.role_id, rp.permission_id
FROM sys_role_permission rp
LEFT JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.id IS NULL;
