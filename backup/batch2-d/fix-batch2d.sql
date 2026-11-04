-- =============================================================================
-- QIAOPM 权限体系对齐 批次二-D：报表(report) + 系统(sys) 角色绑定补齐
-- 依据：audit/baseline/I124-batch2-investigation.md §6 权限矩阵 / §7 脚本设计
-- 执行方式（PowerShell）：
--   Get-Content backup\batch2-d\fix-batch2d.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
-- 验证：fix-batch2d-verify.sql
-- 回滚：fix-batch2-d-rollback.sql
--
-- 范围说明：
--   * 只写 sys_role_permission，不写 sys_permission（本批 33 码在 DB 中已全部存在）
--   * 不动任何点号码绑定（report.dashboard 等历史绑定全部保留）
--   * 不删、不改历史数据，纯 INSERT 追加
--
-- 幂等性：NOT EXISTS 子查询 + 派生表包装（规避 MySQL 1093 目标表限制），
--          重复执行影响 0 行。
--
-- 本文件不含 START TRANSACTION / COMMIT，事务边界由执行命令控制。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §0 前置检查（只读，结果需人工确认后再执行 §1）
-- -----------------------------------------------------------------------------

-- §0.1 本批 33 码存在性检查：期望返回 33 行
SELECT COUNT(*) AS should_be_33_codes
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

-- §0.2 四个目标角色存在性检查：期望返回 4 行（ADMIN/BOSS/MANAGER/OPERATOR）
SELECT role_code, id, deleted
FROM sys_role
WHERE role_code IN ('ADMIN','BOSS','MANAGER','OPERATOR');

-- §0.3 修复前零绑定确认：期望返回 0 行
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
  );

-- -----------------------------------------------------------------------------
-- §1 绑定补齐（4 段 INSERT，期望新增 33 + 25 + 8 + 8 = 74 行）
-- -----------------------------------------------------------------------------

-- §1.1 ADMIN —— 33 码（8 报表 + 25 系统/资质）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
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
    WHERE r.role_code = 'ADMIN' AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §1.2 BOSS —— 25 码（8 报表 + 17 系统/资质）
--     排除 ADMIN 专属码：sys:role:list/add/edit/delete、sys:permission:list、
--     sys:tenant:delete、sys:log:clear、sys:login-log:clear（BOSS 只读）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
     AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task',
        'sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:freeze',
        'sys:log:list','sys:log:export',
        'sys:login-log:list','sys:login-log:export',
        'sys:config:list','sys:config:edit',
        'sys:dcep:list','sys:dcep:view','sys:dcep:edit','sys:dcep:test',
        'dcep:qualification:list','dcep:qualification:edit','dcep:qualification:submit'
     )
    WHERE r.role_code = 'BOSS' AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §1.3 MANAGER —— 8 码（仅报表域）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
     AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task'
     )
    WHERE r.role_code = 'MANAGER' AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §1.4 OPERATOR —— 8 码（仅报表域）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
     AND p.permission_code IN (
        'report:dashboard','report:biz:list','report:caliber','report:caliber:edit',
        'report:export:list','report:export:add','report:export:download','report:task'
     )
    WHERE r.role_code = 'OPERATOR' AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);
