-- ============================================================================
-- fix-batch2b.sql · 营销权限矩阵补齐（batch2-B）
-- ----------------------------------------------------------------------------
-- 来源      : .audit-tmp/batch2b-report.md（batch2-B 调查报告）
-- 拍板      : 用户 2026-11-08 六项裁决
--             1) mkt:dispatch:edit 不给 OPERATOR（只读）
--             2) mkt:pricing:*:edit 不给 OPERATOR（只读）
--             3) 旧 /market/* 路由本批不动，下一批 Phase 3 统一删除
--             4) mkt:activity 本批新建 1 个粗码，下一批 Phase 3 拆分
--             5) CLERK 给 market:coupon:list + market:point:list
--             6) 执行前已确认运行镜像与源码一致（jar sha256 一致）
-- 基线 commit: 02d1da8ce6c2ea60a67bfbfba6efae8aa534b6dc
-- DB 快照   : backup/before-batch2b.sql（mysqldump 10.13, 2026-11-08 23:44:00）
-- 目标环境  : meiyetec-mysql (mysql:8.0.46) / 库 meiyetec / tenant_id=1
-- 执行方式  : docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec \
--                --default-character-set=utf8mb4 < fix-batch2b.sql
--              Windows PowerShell 建议：
--                cmd /c "docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec --default-character-set=utf8mb4 < fix-batch2b.sql"
-- 回滚方式  : rollback-batch2b.sql（同目录，幂等；注意已知限制一节）
-- 幂等性    : 全部写入语句均带 WHERE NOT EXISTS，可重复执行
-- 影响面    : 仅 sys_permission / sys_role_permission / sys_menu_permission
--              不改前端代码、不改后端代码、不删除旧 /market/* 菜单行
-- 已知限制  : 回滚后若后端补上 @RequirePermission 注解并重启，
--              PermissionAutoRegistrar 会把新码以 API 类型重新登记（无法自动区分）
-- ============================================================================

SET NAMES utf8mb4;
SET @tenant := 1;

START TRANSACTION;

-- ----------------------------------------------------------------------------
-- Part 1 · 新建 10 个页面准入粗码（permission_type='button'）
--   说明：粗码仅用于前端路由守卫（frontend/.../router/index.ts），后端无任何
--   @RequirePermission 引用（已全量 grep 确认），因此 BOSS 绑定后仍是只读语义。
--   parent_id=1204（marketing 菜单权限 id）：对齐已有菜单码 mkt.channel 的父级风格；
--   菜单树只读取 permission_type='menu' 的行，故此值不影响菜单渲染。
-- ----------------------------------------------------------------------------

-- P1-01 mkt:effect  ← router:99 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:effect', '营销效果（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:effect');

-- P1-02 mkt:channel  ← router:136 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:channel', '营销渠道（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:channel');

-- P1-03 mkt:template  ← router:137 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:template', '消息模板（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:template');

-- P1-04 mkt:touch-task  ← router:138 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:touch-task', '触达任务（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:touch-task');

-- P1-05 mkt:birthday  ← router:139 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:birthday', '生日提醒（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:birthday');

-- P1-06 mkt:journey  ← router:140 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:journey', '营销旅程（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:journey');

-- P1-07 mkt:conversion  ← router:142 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:conversion', '触达效果（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:conversion');

-- P1-08 mkt:pricing  ← router:143 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:pricing', '动态定价（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:pricing');

-- P1-09 mkt:dispatch  ← router:145 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:dispatch', '业务策略下发（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:dispatch');

-- P1-10 mkt:activity  ← router:157 守卫码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:activity', '营销活动通用（页面准入）', 'button', 1204, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:activity');

-- ----------------------------------------------------------------------------
-- Part 2 · 新建 10 个细分码（permission_type='button'，parent_id=0）
--   说明：与已有细分码（mkt:channel:list 等 parent_id=0）保持一致；
--   这批码本批不挂注解，仅先行建码，供下一批后端补 @RequirePermission 使用。
-- ----------------------------------------------------------------------------

-- P2-01 mkt:birthday:list
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:birthday:list', '生日提醒-查询', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:birthday:list');

-- P2-02 mkt:birthday:edit
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:birthday:edit', '生日提醒-编辑', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:birthday:edit');

-- P2-03 mkt:member-day:list
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:member-day:list', '会员日-查询', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:member-day:list');

-- P2-04 mkt:member-day:edit
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:member-day:edit', '会员日-编辑', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:member-day:edit');

-- P2-05 mkt:ecommerce:list
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:ecommerce:list', '电商集成-查询', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:ecommerce:list');

-- P2-06 mkt:ecommerce:edit
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:ecommerce:edit', '电商集成-编辑', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:ecommerce:edit');

-- P2-07 mkt:pricing:rule:list
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:pricing:rule:list', '动态定价规则-查询', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:pricing:rule:list');

-- P2-08 mkt:pricing:rule:edit
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:pricing:rule:edit', '动态定价规则-编辑', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:pricing:rule:edit');

-- P2-09 mkt:dispatch:list
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:dispatch:list', '业务策略下发-查询', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:dispatch:list');

-- P2-10 mkt:dispatch:edit
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt:dispatch:edit', '业务策略下发-编辑', 'button', 0, NULL, NULL, 0, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt:dispatch:edit');

-- ----------------------------------------------------------------------------
-- Part 3 · 菜单行（sys_permission(type=menu) + sys_menu_permission）
--   3.1 新建 12 条菜单权限码（报告 §6：parent_id=1204，sort_order 11~22）
--   3.2 为 12 条新码创建 sys_menu_permission 行（route_path 与前端路由一致）
--   3.3 7 条已存在菜单行（mkt.effect 等）做幂等自愈补齐，不重复插入
-- ----------------------------------------------------------------------------

-- 3.1 菜单权限码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.promotion', '营销活动', 'menu', 1204, '/mkt/promotion', 'Promotion', 11, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.promotion');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.coupon', '优惠券', 'menu', 1204, '/mkt/coupon', 'Present', 12, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.coupon');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.points', '积分规则', 'menu', 1204, '/mkt/points', 'Medal', 13, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.points');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.birthday', '生日提醒', 'menu', 1204, '/mkt/birthday', 'Calendar', 14, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.birthday');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.invite', '裂变邀请', 'menu', 1204, '/m09/invite', 'Share', 15, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.invite');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.dispatch', '业务策略下发', 'menu', 1204, '/m12/dispatch', 'Connection', 16, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.dispatch');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.member-day', '会员日自动化', 'menu', 1204, '/member-day', 'Star', 17, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.member-day');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.style-gallery', '款式图库', 'menu', 1204, '/style-gallery', 'Picture', 18, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.style-gallery');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.style-gallery.quick-order', '款式图库快速下单', 'menu', 1204, '/style-gallery/quick-order', 'Goods', 19, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.style-gallery.quick-order');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.subscription', '订阅会员制', 'menu', 1204, '/subscription', 'Trophy', 20, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.subscription');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.lifecycle', '会员生命周期看板', 'menu', 1204, '/lifecycle', 'Histogram', 21, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.lifecycle');
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'mkt.ecommerce', '电商集成', 'menu', 1204, '/ecommerce', 'ShoppingCart', 22, 1, 0
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'mkt.ecommerce');

-- 3.2 菜单 UI 行（component_path 沿用现有 11 行的 NULL 约定）
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/promotion', NULL, NULL, 'Promotion', NULL, 0, 1, 11
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.promotion'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/coupon', NULL, NULL, 'Present', NULL, 0, 1, 12
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.coupon'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/points', NULL, NULL, 'Medal', NULL, 0, 1, 13
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.points'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/birthday', NULL, NULL, 'Calendar', NULL, 0, 1, 14
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.birthday'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/m09/invite', NULL, NULL, 'Share', NULL, 0, 1, 15
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.invite'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/m12/dispatch', NULL, NULL, 'Connection', NULL, 0, 1, 16
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.dispatch'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/member-day', NULL, NULL, 'Star', NULL, 0, 1, 17
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.member-day'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/style-gallery', NULL, NULL, 'Picture', NULL, 0, 1, 18
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.style-gallery'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/style-gallery/quick-order', NULL, NULL, 'Goods', NULL, 0, 1, 19
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.style-gallery.quick-order'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/subscription', NULL, NULL, 'Trophy', NULL, 0, 1, 20
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.subscription'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/lifecycle', NULL, NULL, 'Histogram', NULL, 0, 1, 21
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.lifecycle'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/ecommerce', NULL, NULL, 'ShoppingCart', NULL, 0, 1, 22
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.ecommerce'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);

-- 3.3 已存在 7 条菜单行的幂等自愈（当前 DB 已存在，正常执行为 no-op）
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/effect', NULL, NULL, 'DataAnalysis', NULL, 0, 1, 4
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.effect'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/channel', NULL, NULL, 'Connection', NULL, 0, 1, 5
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.channel'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/template', NULL, NULL, 'ChatLineRound', NULL, 0, 1, 6
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.template'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/touch-task', NULL, NULL, 'Bell', NULL, 0, 1, 7
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.touch-task'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/journey', NULL, NULL, 'Star', NULL, 0, 1, 8
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.journey'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/conversion', NULL, NULL, 'DataAnalysis', NULL, 0, 1, 9
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.conversion'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);
INSERT INTO sys_menu_permission (permission_id, route_path, component_path, redirect_path, icon, active_paths, hidden, keep_alive, sort_order)
SELECT p.id, '/mkt/pricing', NULL, NULL, 'PriceTag', NULL, 0, 1, 10
  FROM sys_permission p
 WHERE p.permission_code = 'mkt.pricing'
   AND NOT EXISTS (SELECT 1 FROM sys_menu_permission mp WHERE mp.permission_id = p.id);

-- ----------------------------------------------------------------------------
-- Part 4 · 角色绑定
--   规则（来源：报告 §5 矩阵 + 用户 6 项裁决）：
--     ADMIN    = 39 营销码 + 12 遗留 CRUD 码                      → 51
--     BOSS     = 21 只读码 + 2 跨域只读码                         → 23
--     MANAGER  = 39 营销码 + 12 遗留 CRUD 码 + 2 跨域只读码       → 53
--     OPERATOR = 39 营销码 − 5 个受限 edit 码 + 2 跨域只读码      → 36
--     CLERK    = 2 跨域只读码                                     → 2
--   OPERATOR 受限 edit 码（裁决 1、2）：
--     mkt:pricing:experiment:edit / mkt:pricing:rule:edit /
--     mkt:dispatch:edit / mkt:ecommerce:edit / subscription:plan:edit
--   另有：10 个粗码绑 ADMIN/BOSS/MANAGER/OPERATOR（前端页面准入）
--   菜单码：ADMIN/BOSS 绑 12 条新菜单；MANAGER/OPERATOR 绑 12 新 + 7 已存在
-- ----------------------------------------------------------------------------

-- ---- ADMIN ----
-- ADMIN · 10 个页面准入粗码（前端守卫）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity')
 WHERE r.role_code = 'ADMIN'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- ADMIN · 规格码 51 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('market:activity:add', 'market:activity:delete', 'market:activity:detail', 'market:activity:edit', 'market:activity:list', 'market:activity:stat', 'market:coupon:add', 'market:coupon:delete', 'market:coupon:edit', 'market:point:add', 'market:point:delete', 'market:point:edit', 'member:lifecycle:edit', 'member:lifecycle:view', 'mkt:birthday:edit', 'mkt:birthday:list', 'mkt:channel:edit', 'mkt:channel:list', 'mkt:conversion:track', 'mkt:conversion:view', 'mkt:coupon', 'mkt:dispatch:edit', 'mkt:dispatch:list', 'mkt:ecommerce:edit', 'mkt:ecommerce:list', 'mkt:effect:edit', 'mkt:effect:view', 'mkt:invite:edit', 'mkt:invite:list', 'mkt:journey:edit', 'mkt:journey:list', 'mkt:journey:trigger:edit', 'mkt:journey:trigger:list', 'mkt:member-day:edit', 'mkt:member-day:list', 'mkt:points', 'mkt:pricing:experiment:edit', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:edit', 'mkt:pricing:rule:list', 'mkt:promotion', 'mkt:template:edit', 'mkt:template:list', 'mkt:touch:task:edit', 'mkt:touch:task:list', 'style:gallery:edit', 'style:gallery:list', 'subscription:member:edit', 'subscription:member:list', 'subscription:plan:edit', 'subscription:plan:list')
 WHERE r.role_code = 'ADMIN'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- ADMIN · 菜单码 12 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce')
 WHERE r.role_code = 'ADMIN'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- BOSS ----
-- BOSS · 10 个页面准入粗码（前端守卫）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity')
 WHERE r.role_code = 'BOSS'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- BOSS · 规格码 23 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('market:coupon:list', 'market:point:list', 'member:lifecycle:view', 'mkt:birthday:list', 'mkt:channel:list', 'mkt:conversion:view', 'mkt:coupon', 'mkt:dispatch:list', 'mkt:ecommerce:list', 'mkt:effect:view', 'mkt:invite:list', 'mkt:journey:list', 'mkt:journey:trigger:list', 'mkt:member-day:list', 'mkt:points', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:list', 'mkt:promotion', 'mkt:template:list', 'mkt:touch:task:list', 'style:gallery:list', 'subscription:member:list', 'subscription:plan:list')
 WHERE r.role_code = 'BOSS'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- BOSS · 菜单码 12 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce')
 WHERE r.role_code = 'BOSS'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- MANAGER ----
-- MANAGER · 10 个页面准入粗码（前端守卫）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity')
 WHERE r.role_code = 'MANAGER'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- MANAGER · 规格码 53 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('market:activity:add', 'market:activity:delete', 'market:activity:detail', 'market:activity:edit', 'market:activity:list', 'market:activity:stat', 'market:coupon:add', 'market:coupon:delete', 'market:coupon:edit', 'market:coupon:list', 'market:point:add', 'market:point:delete', 'market:point:edit', 'market:point:list', 'member:lifecycle:edit', 'member:lifecycle:view', 'mkt:birthday:edit', 'mkt:birthday:list', 'mkt:channel:edit', 'mkt:channel:list', 'mkt:conversion:track', 'mkt:conversion:view', 'mkt:coupon', 'mkt:dispatch:edit', 'mkt:dispatch:list', 'mkt:ecommerce:edit', 'mkt:ecommerce:list', 'mkt:effect:edit', 'mkt:effect:view', 'mkt:invite:edit', 'mkt:invite:list', 'mkt:journey:edit', 'mkt:journey:list', 'mkt:journey:trigger:edit', 'mkt:journey:trigger:list', 'mkt:member-day:edit', 'mkt:member-day:list', 'mkt:points', 'mkt:pricing:experiment:edit', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:edit', 'mkt:pricing:rule:list', 'mkt:promotion', 'mkt:template:edit', 'mkt:template:list', 'mkt:touch:task:edit', 'mkt:touch:task:list', 'style:gallery:edit', 'style:gallery:list', 'subscription:member:edit', 'subscription:member:list', 'subscription:plan:edit', 'subscription:plan:list')
 WHERE r.role_code = 'MANAGER'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- MANAGER · 菜单码 19 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce', 'mkt.effect', 'mkt.channel', 'mkt.template', 'mkt.touch-task', 'mkt.journey', 'mkt.conversion', 'mkt.pricing')
 WHERE r.role_code = 'MANAGER'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- OPERATOR ----
-- OPERATOR · 10 个页面准入粗码（前端守卫）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:birthday', 'mkt:journey', 'mkt:conversion', 'mkt:pricing', 'mkt:dispatch', 'mkt:activity')
 WHERE r.role_code = 'OPERATOR'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- OPERATOR · 规格码 36 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('market:coupon:list', 'market:point:list', 'member:lifecycle:edit', 'member:lifecycle:view', 'mkt:birthday:edit', 'mkt:birthday:list', 'mkt:channel:edit', 'mkt:channel:list', 'mkt:conversion:track', 'mkt:conversion:view', 'mkt:coupon', 'mkt:dispatch:list', 'mkt:ecommerce:list', 'mkt:effect:edit', 'mkt:effect:view', 'mkt:invite:edit', 'mkt:invite:list', 'mkt:journey:edit', 'mkt:journey:list', 'mkt:journey:trigger:edit', 'mkt:journey:trigger:list', 'mkt:member-day:edit', 'mkt:member-day:list', 'mkt:points', 'mkt:pricing:experiment:list', 'mkt:pricing:rule:list', 'mkt:promotion', 'mkt:template:edit', 'mkt:template:list', 'mkt:touch:task:edit', 'mkt:touch:task:list', 'style:gallery:edit', 'style:gallery:list', 'subscription:member:edit', 'subscription:member:list', 'subscription:plan:list')
 WHERE r.role_code = 'OPERATOR'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
-- OPERATOR · 菜单码 19 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce', 'mkt.effect', 'mkt.channel', 'mkt.template', 'mkt.touch-task', 'mkt.journey', 'mkt.conversion', 'mkt.pricing')
 WHERE r.role_code = 'OPERATOR'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ---- CLERK ----
-- CLERK · 规格码 2 个
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('market:coupon:list', 'market:point:list')
 WHERE r.role_code = 'CLERK'
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

COMMIT;

-- ----------------------------------------------------------------------------
-- 执行后自检（与 verify-batch2b.sql V3 等价，此处为快速版）
-- ----------------------------------------------------------------------------
SELECT r.role_code, COUNT(*) AS bound_rows
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code IN ('ADMIN', 'BOSS', 'MANAGER', 'OPERATOR', 'CLERK')
   AND (p.permission_code LIKE 'mkt%' OR p.permission_code LIKE 'market%'
        OR p.permission_code LIKE 'member:lifecycle%' OR p.permission_code LIKE 'style:gallery%'
        OR p.permission_code LIKE 'subscription%')
 GROUP BY r.role_code ORDER BY r.role_code;
