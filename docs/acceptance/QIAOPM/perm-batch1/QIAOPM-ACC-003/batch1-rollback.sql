-- QIAOPM 权限体系对齐 批次一 —— 回滚脚本
-- 仅在 batch1-fix.sql 执行后出问题时使用
-- 执行顺序：先 4.1 删绑定，再 4.2 删新建码（顺序不可颠倒）
-- 来源：v3 草案 §4，commit 270e8e3，审计证据 audit/evidence/F-perm-route-db-diff/
-- 执行方式：Get-Content backup\batch1-rollback.sql -Raw -Encoding UTF8 | docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
--
-- 说明：
--   4.1 删本批在 6 角色上的绑定，但保留各角色批前就有的历史绑定（NOT 条件即历史绑定白名单）。
--   4.2 删本批新建的 10 个权限码；schedule:list / schedule:edit 批前已存在，勿删。
--   sys_role_permission 对 sys_permission 无外键，但先做 4.1 可避免残留孤儿绑定。
-- 本文件不含 COMMIT / START TRANSACTION，事务边界由执行命令控制

-- ============================================================
-- §4.1 先删本批在 6 角色上的绑定（排除需保留的历史绑定）
-- ============================================================
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR','STORE_ADMIN')
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
  AND NOT (r.role_code='ADMIN'       AND p.permission_code IN ('member:list','member:add','member:edit','member:delete','material:binding:edit'))
  AND NOT (r.role_code='BOSS'        AND p.permission_code='member:list')
  AND NOT (r.role_code='MANAGER'     AND p.permission_code='member:list')
  AND NOT (r.role_code='CLERK'       AND p.permission_code='member:list')
  AND NOT (r.role_code='STORE_ADMIN' AND p.permission_code IN ('material:binding:edit','pos:refund:audit:rule'));
-- 注：BOSS 的 package:list 是本批新增绑定（历史 0 绑定）→ 回滚时正常删除

-- ============================================================
-- §4.2 再删本批新建的 10 个权限码（sys_role_permission 无外键，必须先做 4.1）
--     schedule:list / schedule:edit 本批未新建，勿删
-- ============================================================
DELETE FROM sys_permission
WHERE permission_code IN (
  'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist','pos:list',
  'stock:expiry','stock:list','stock:material:binding','stock:material:stat',
  'stock:service-material:rule','wms:transfer');
