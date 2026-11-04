-- =============================================================================
-- QIAOPM 批次二-D 回滚脚本：精确删除本批新增的角色权限绑定
-- 依据：audit/baseline/I124-batch2-investigation.md §6 权限矩阵
-- 用法：
--   Get-Content backup\batch2-d\fix-batch2-d-rollback.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
--
-- 语义：只删除 (role_code, permission_code) 精确匹配本批矩阵的绑定行，
--       不触碰任何历史点号绑定或其它权限码。
-- 幂等：重复执行影响 0 行。
-- 本文件不含 START TRANSACTION / COMMIT。
-- =============================================================================

-- §0 回滚前预览：期望 ADMIN 33 / BOSS 25 / MANAGER 8 / OPERATOR 8，合计 74 行
SELECT r.role_code, COUNT(*) AS will_be_deleted
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE (r.role_code = 'ADMIN' AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task',
        'sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:permission:list',
        'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze',
        'sys:log:list','sys:log:export','sys:log:clear',
        'sys:login-log:list','sys:login-log:export','sys:login-log:clear',
        'sys:config:list','sys:config:edit',
        'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
        'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
       ))
   OR (r.role_code = 'BOSS' AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task',
        'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:freeze',
        'sys:log:list','sys:log:export',
        'sys:login-log:list','sys:login-log:export',
        'sys:config:list','sys:config:edit',
        'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
        'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
       ))
   OR (r.role_code IN ('MANAGER','OPERATOR') AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task'
       ))
GROUP BY r.role_code
ORDER BY r.role_code;

-- §1 执行删除（期望影响 74 行）
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE (r.role_code = 'ADMIN' AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task',
        'sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:permission:list',
        'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze',
        'sys:log:list','sys:log:export','sys:log:clear',
        'sys:login-log:list','sys:login-log:export','sys:login-log:clear',
        'sys:config:list','sys:config:edit',
        'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
        'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
       ))
   OR (r.role_code = 'BOSS' AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task',
        'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:freeze',
        'sys:log:list','sys:log:export',
        'sys:login-log:list','sys:login-log:export',
        'sys:config:list','sys:config:edit',
        'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
        'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
       ))
   OR (r.role_code IN ('MANAGER','OPERATOR') AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task'
       ));

-- §2 回滚后核对：期望 0 行
SELECT r.role_code, COUNT(*) AS remaining
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','OPERATOR')
  AND p.permission_code LIKE 'report:%'
GROUP BY r.role_code;
