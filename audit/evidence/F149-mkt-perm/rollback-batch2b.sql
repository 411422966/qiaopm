-- ============================================================================
-- rollback-batch2b.sql · 回滚 fix-batch2b.sql（batch2-B）
-- ----------------------------------------------------------------------------
-- 基线 commit: 02d1da8ce6c2ea60a67bfbfba6efae8aa534b6dc
-- 目标环境  : meiyetec-mysql / 库 meiyetec
-- 执行方式  : docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec \
--                --default-character-set=utf8mb4 < rollback-batch2b.sql
-- 幂等性    : 全部 DELETE 带条件，重复执行安全
-- 保留策略  : 2026-11-08 执行前实测存在的绑定一律保留（角色 × 码 白名单）
-- 已知限制  : 若后端已补 @RequirePermission 注解并重启，PermissionAutoRegistrar
--              会把被删的新码以 permission_type='API' 重新登记，需人工再删
-- ============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ----------------------------------------------------------------------------
-- R1 · 删除本批新建 32 个权限码的全部角色绑定
--     （20 个按钮码 + 12 个菜单码）
-- ----------------------------------------------------------------------------
DELETE rp FROM sys_role_permission rp
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt:birthday:list', 'mkt:birthday:edit', 'mkt:member-day:list', 'mkt:member-day:edit', 'mkt:ecommerce:list', 'mkt:ecommerce:edit', 'mkt:pricing:rule:list', 'mkt:pricing:rule:edit', 'mkt:dispatch:list', 'mkt:dispatch:edit', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');

-- ----------------------------------------------------------------------------
-- R2 · 删除本批为“已存在权限码”新增的角色绑定
--     只删本批新增的组合；2026-11-08 执行前已存在的绑定保留
-- ----------------------------------------------------------------------------

-- R2 · ADMIN · 本批新增绑定 66 条（删除后该角色回到执行前状态）
DELETE rp FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code = 'ADMIN'
   AND p.permission_code IN ('market:activity:add', 'market:activity:delete', 'market:activity:detail', 'market:activity:edit', 'market:activity:stat', 'market:coupon:add', 'market:coupon:delete', 'market:coupon:edit', 'market:point:add', 'market:point:delete', 'market:point:edit', 'mkt:birthday:edit', 'mkt:birthday:list', 'mkt:channel:edit', 'mkt:channel:list', 'mkt:conversion:track', 'mkt:conversion:view', 'mkt:dispatch:edit', 'mkt:dispatch:list', 'mkt:ecommerce:edit', 'mkt:ecommerce:list', 'mkt:effect:edit', 'mkt:effect:view', 'mkt:invite:edit', 'mkt:journey:edit', 'mkt:journey:list', 'mkt:journey:trigger:edit', 'mkt:journey:trigger:list', 'mkt:member-day:edit', 'mkt:member-day:list', 'mkt:pricing:experiment:edit', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:edit', 'mkt:pricing:rule:list', 'mkt:template:edit', 'mkt:template:list', 'mkt:touch:task:edit', 'mkt:touch:task:list', 'style:gallery:edit', 'style:gallery:list', 'subscription:member:edit', 'subscription:member:list', 'subscription:plan:edit', 'subscription:plan:list', 'mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');

-- R2 · BOSS · 本批新增绑定 38 条（删除后该角色回到执行前状态）
DELETE rp FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code = 'BOSS'
   AND p.permission_code IN ('mkt:birthday:list', 'mkt:channel:list', 'mkt:conversion:view', 'mkt:dispatch:list', 'mkt:ecommerce:list', 'mkt:effect:view', 'mkt:journey:list', 'mkt:journey:trigger:list', 'mkt:member-day:list', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:list', 'mkt:template:list', 'mkt:touch:task:list', 'style:gallery:list', 'subscription:member:list', 'subscription:plan:list', 'mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');

-- R2 · MANAGER · 本批新增绑定 76 条（删除后该角色回到执行前状态）
DELETE rp FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code = 'MANAGER'
   AND p.permission_code IN ('market:activity:add', 'market:activity:delete', 'market:activity:detail', 'market:activity:edit', 'market:activity:stat', 'market:coupon:add', 'market:coupon:delete', 'market:coupon:edit', 'market:point:add', 'market:point:delete', 'market:point:edit', 'member:lifecycle:edit', 'member:lifecycle:view', 'mkt:birthday:edit', 'mkt:birthday:list', 'mkt:channel:edit', 'mkt:channel:list', 'mkt:conversion:track', 'mkt:conversion:view', 'mkt:dispatch:edit', 'mkt:dispatch:list', 'mkt:ecommerce:edit', 'mkt:ecommerce:list', 'mkt:effect:edit', 'mkt:effect:view', 'mkt:invite:edit', 'mkt:invite:list', 'mkt:journey:edit', 'mkt:journey:list', 'mkt:journey:trigger:edit', 'mkt:journey:trigger:list', 'mkt:member-day:edit', 'mkt:member-day:list', 'mkt:pricing:experiment:edit', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:edit', 'mkt:pricing:rule:list', 'mkt:template:edit', 'mkt:template:list', 'mkt:touch:task:edit', 'mkt:touch:task:list', 'style:gallery:edit', 'style:gallery:list', 'subscription:member:edit', 'subscription:member:list', 'subscription:plan:edit', 'subscription:plan:list', 'mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce', 'mkt.effect', 'mkt.channel', 'mkt.template', 'mkt.touch-task', 'mkt.journey', 'mkt.conversion', 'mkt.pricing');

-- R2 · OPERATOR · 本批新增绑定 63 条（删除后该角色回到执行前状态）
DELETE rp FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code = 'OPERATOR'
   AND p.permission_code IN ('market:coupon:list', 'market:point:list', 'mkt:birthday:edit', 'mkt:birthday:list', 'mkt:channel:edit', 'mkt:channel:list', 'mkt:conversion:track', 'mkt:conversion:view', 'mkt:coupon', 'mkt:dispatch:list', 'mkt:ecommerce:list', 'mkt:effect:edit', 'mkt:effect:view', 'mkt:invite:edit', 'mkt:invite:list', 'mkt:journey:edit', 'mkt:journey:list', 'mkt:journey:trigger:edit', 'mkt:journey:trigger:list', 'mkt:member-day:edit', 'mkt:member-day:list', 'mkt:points', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:list', 'mkt:promotion', 'mkt:template:edit', 'mkt:template:list', 'mkt:touch:task:edit', 'mkt:touch:task:list', 'style:gallery:edit', 'style:gallery:list', 'subscription:member:edit', 'subscription:member:list', 'subscription:plan:list', 'mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce', 'mkt.effect', 'mkt.channel', 'mkt.template', 'mkt.touch-task', 'mkt.journey', 'mkt.conversion', 'mkt.pricing');

-- R2 · CLERK · 本批新增绑定 2 条（删除后该角色回到执行前状态）
DELETE rp FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code = 'CLERK'
   AND p.permission_code IN ('market:coupon:list', 'market:point:list');

-- ----------------------------------------------------------------------------
-- R3 · 删除本批新增的 12 条 sys_menu_permission 菜单行
--     （7 条已存在菜单行 mkt.effect 等不删）
-- ----------------------------------------------------------------------------
DELETE mp FROM sys_menu_permission mp
  JOIN sys_permission p ON p.id = mp.permission_id
 WHERE p.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');

-- ----------------------------------------------------------------------------
-- R4 · 删除本批新建的 32 个 sys_permission 行
-- ----------------------------------------------------------------------------
DELETE FROM sys_permission WHERE permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt:birthday:list', 'mkt:birthday:edit', 'mkt:member-day:list', 'mkt:member-day:edit', 'mkt:ecommerce:list', 'mkt:ecommerce:edit', 'mkt:pricing:rule:list', 'mkt:pricing:rule:edit', 'mkt:dispatch:list', 'mkt:dispatch:edit', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');

COMMIT;

-- ----------------------------------------------------------------------------
-- 回滚后自检：应剩余 0 条新码绑定、0 条新菜单行
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS leftover_new_permissions
  FROM sys_permission
 WHERE permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity', 'mkt:birthday:list', 'mkt:birthday:edit', 'mkt:member-day:list', 'mkt:member-day:edit', 'mkt:ecommerce:list', 'mkt:ecommerce:edit', 'mkt:pricing:rule:list', 'mkt:pricing:rule:edit', 'mkt:dispatch:list', 'mkt:dispatch:edit', 'mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');

SELECT COUNT(*) AS leftover_new_menu_rows
  FROM sys_menu_permission mp
  JOIN sys_permission p ON p.id = mp.permission_id
 WHERE p.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce');
