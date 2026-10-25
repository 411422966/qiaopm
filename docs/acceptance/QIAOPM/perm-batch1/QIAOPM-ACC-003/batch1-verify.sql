-- QIAOPM 权限体系对齐 批次一 —— 只读校验 SQL（不修改任何数据）
-- 来源：v3 草案，commit 270e8e3，审计证据 audit/evidence/F-perm-route-db-diff/
-- 内容范围：v3 草案 §0（0.1~0.4 执行前前置检查）+ §3（3.1~3.4 执行后校验）
-- 执行方式：Get-Content backup\batch1-verify.sql -Raw -Encoding UTF8 | docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
--
-- 本文件全部为 SELECT，只读，可重复执行。
-- §0 段：执行 backup/batch1-fix.sql 之前跑，核对库内现状与快照一致。
-- §3 段：执行 backup/batch1-fix.sql 之后跑，核对绑定数与矩阵。
-- 预期输出值见每段 SQL 末尾的「期望」注释，完整预期值记录在 v3 草案 §0 / §3。
-- 执行前必须先用 backup/meiyetec-20261025-recovery.sql 做全库备份

-- ============================================================
-- §0 前置检查（只读，先跑）
-- ============================================================

-- 0.1 本批 12 个「可能需新建」的码：预期 10 个不存在 + 2 个已存在且零绑定
SELECT p.permission_code, p.permission_type, p.status, p.deleted,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) AS bound_roles
FROM sys_permission p
WHERE p.permission_code IN (
  'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist',
  'pos:list','stock:expiry','stock:list','stock:material:binding',
  'stock:material:stat','stock:service-material:rule','wms:transfer',
  'schedule:list','schedule:edit')
ORDER BY p.permission_code;
-- 期望：schedule:list(id 1462) / schedule:edit(id 1463) 有行，type=button，bound_roles=0；其余 10 个无行

-- 0.2 6 个目标角色存在且启用（应返回 6 行，status=1, deleted=0）
SELECT id, role_code, role_name, status, deleted
FROM sys_role
WHERE role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR','STORE_ADMIN')
ORDER BY role_code;
-- 期望 id：ADMIN=8, BOSS=6, MANAGER=5, CLERK=4, OPERATOR=7, STORE_ADMIN=2

-- 0.3 当前历史绑定快照（留痕用；§2 会保留/补齐这些绑定）
SELECT r.role_code, p.permission_code
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR','STORE_ADMIN')
  AND p.permission_code IN (
    'material:binding:edit','member:add','member:delete','member:edit','member:list',
    'pos:refund:audit:rule','package:list')
ORDER BY r.role_code, p.permission_code;
-- 期望：ADMIN 5 行 / BOSS 1 行(member:list) / CLERK 1 行 / MANAGER 1 行 / OPERATOR 0 行 / STORE_ADMIN 2 行
--      （package:list 当前 0 绑定 → v3 为 BOSS 新增）

-- 0.4 permission_type 脏数据（登记用，本批不修）
SELECT permission_code, permission_type FROM sys_permission
WHERE permission_type NOT IN ('menu','button') OR permission_type IS NULL;
-- 已知：booking:available/package:refund=0；pos:order:refund/pos:refund/stock:consumption/stock:record=NULL；
--       material:binding:edit='1318'；14 个 'API'

-- ============================================================
-- §3 执行后校验（只读）
-- ============================================================

-- 3.1 各角色在本批 93 码集合内的绑定数
--     期望：ADMIN 93 / BOSS 20 / MANAGER 65 / CLERK 20 / OPERATOR 20 / STORE_ADMIN 25
SELECT r.role_code, COUNT(DISTINCT p.permission_code) AS bound_codes
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR','STORE_ADMIN')
  AND r.deleted = 0 AND p.deleted = 0
  AND p.permission_code IN (
    'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist',
    'booking:add','booking:available','booking:calendar','booking:checkin','booking:complete',
    'booking:delete','booking:detail','booking:edit','booking:export','booking:list',
    'booking:schedule:list','booking:stat','booking:update',
    'material:binding:edit','material:binding:list','material:cost:list','material:deduct:manage',
    'material:package:edit','material:package:list',
    'member:add','member:batch','member:delete','member:edit','member:export','member:freeze',
    'member:import','member:level:edit','member:level:list','member:lifecycle:edit',
    'member:lifecycle:view','member:list','member:message:edit','member:message:list',
    'member:order:list','member:profile','member:stat','member:style-preference:edit',
    'member:style-preference:list','member:tag',
    'package:add','package:delete','package:edit','package:freeze','package:list',
    'package:purchase','package:redeem','package:refund','package:suspend','package:transfer',
    'pos:list','pos:order:add','pos:order:list','pos:order:pay','pos:order:refund','pos:order:stat',
    'pos:order:void','pos:reconciliation:handle','pos:reconciliation:list','pos:reconciliation:run',
    'pos:refund','pos:refund:audit','pos:refund:audit:finance','pos:refund:audit:hq',
    'pos:refund:audit:rule','pos:refund:audit:store','pos:service:list',
    'schedule:list','schedule:edit',
    'stock:alert','stock:check','stock:consumption','stock:expiry','stock:expiry:edit',
    'stock:expiry:view','stock:export','stock:inbound','stock:list','stock:material:binding',
    'stock:material:stat','stock:outbound','stock:product:add','stock:product:delete',
    'stock:product:edit','stock:product:import','stock:product:list','stock:record',
    'stock:replenish','stock:service-material:rule','stock:stat','wms:transfer')
GROUP BY r.role_code ORDER BY r.role_code;

-- 3.2 25 条本批路由的守卫码 × 6 角色矩阵（期望与 v3 草案 §5 逐格一致）
SELECT p.permission_code,
       MAX(r.role_code='ADMIN')       AS ADMIN,
       MAX(r.role_code='BOSS')        AS BOSS,
       MAX(r.role_code='MANAGER')     AS MANAGER,
       MAX(r.role_code='CLERK')       AS CLERK,
       MAX(r.role_code='OPERATOR')    AS OPERATOR,
       MAX(r.role_code='STORE_ADMIN') AS STORE_ADMIN
FROM sys_permission p
JOIN sys_role_permission rp ON rp.permission_id = p.id
JOIN sys_role r ON r.id = rp.role_id
WHERE p.deleted = 0 AND r.deleted = 0
  AND p.permission_code IN (
    'member:list','booking:list','booking:schedule:list','biz:booking:resource',
    'biz:booking:waitlist','biz:booking:no-show',
    'pos:list','pos:order:list','pos:order:stat','pos:refund','pos:reconciliation:list',
    'stock:list','stock:check','stock:alert','stock:stat','wms:transfer',
    'stock:material:binding','stock:material:stat','stock:expiry','stock:service-material:rule')
GROUP BY p.permission_code ORDER BY p.permission_code;

-- 3.2b package 路由守卫码 package:list（v3：BOSS 也应为 1）
SELECT p.permission_code,
       MAX(r.role_code='ADMIN') AS ADMIN, MAX(r.role_code='BOSS') AS BOSS,
       MAX(r.role_code='MANAGER') AS MANAGER, MAX(r.role_code='CLERK') AS CLERK,
       MAX(r.role_code='OPERATOR') AS OPERATOR, MAX(r.role_code='STORE_ADMIN') AS STORE_ADMIN
FROM sys_permission p
JOIN sys_role_permission rp ON rp.permission_id = p.id
JOIN sys_role r ON r.id = rp.role_id
WHERE p.deleted = 0 AND r.deleted = 0 AND p.permission_code='package:list'
GROUP BY p.permission_code;
-- 期望：ADMIN=1, BOSS=1, 其余=0

-- 3.3 排班后端接口权限（schedule:list / schedule:edit）
SELECT p.permission_code,
       MAX(r.role_code='ADMIN') AS ADMIN, MAX(r.role_code='BOSS') AS BOSS,
       MAX(r.role_code='MANAGER') AS MANAGER, MAX(r.role_code='CLERK') AS CLERK,
       MAX(r.role_code='OPERATOR') AS OPERATOR, MAX(r.role_code='STORE_ADMIN') AS STORE_ADMIN
FROM sys_permission p
JOIN sys_role_permission rp ON rp.permission_id = p.id
JOIN sys_role r ON r.id = rp.role_id
WHERE p.deleted = 0 AND r.deleted = 0
  AND p.permission_code IN ('schedule:list','schedule:edit')
GROUP BY p.permission_code;
-- 期望：schedule:list → ADMIN=1 BOSS=1 MANAGER=1 CLERK=1 OPERATOR=0 STORE_ADMIN=0
--       schedule:edit → ADMIN=1 BOSS=0 MANAGER=1 CLERK=0 OPERATOR=0 STORE_ADMIN=0

-- 3.4 反向核对：本批新建码的绑定数（12 码应全部 > 0）
SELECT p.permission_code, COUNT(rp.role_id) AS bound_roles
FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN (
  'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist','pos:list',
  'stock:expiry','stock:list','stock:material:binding','stock:material:stat',
  'stock:service-material:rule','wms:transfer','schedule:list','schedule:edit')
GROUP BY p.permission_code ORDER BY p.permission_code;
-- 期望：12 行，bound_roles 均 > 0
