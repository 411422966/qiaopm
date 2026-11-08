-- ============================================================================
-- verify-batch2b.sql · batch2-B 营销权限矩阵验证
-- ----------------------------------------------------------------------------
-- 基线 commit: 02d1da8ce6c2ea60a67bfbfba6efae8aa534b6dc
-- 目标环境  : meiyetec-mysql / 库 meiyetec
-- 执行方式  : docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec \
--                --default-character-set=utf8mb4 < verify-batch2b.sql
-- 只读脚本  : 仅 SELECT，不产生任何写入
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- V0 · 校验用码表（把权限码归类，供后续统计使用）
--     说明：类别可重叠 —— READ / EXCL 是 FINE 的子集，故按 8 张小表分别统计
-- ----------------------------------------------------------------------------
WITH cat_fine AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:channel:list","mkt:channel:edit","mkt:template:list","mkt:template:edit","mkt:touch:task:list","mkt:touch:task:edit","mkt:journey:list","mkt:journey:edit","mkt:journey:trigger:list","mkt:journey:trigger:edit","mkt:effect:view","mkt:effect:edit","mkt:conversion:view","mkt:conversion:track","mkt:pricing:experiment:list","mkt:pricing:experiment:edit","mkt:invite:list","mkt:invite:edit","member:lifecycle:view","member:lifecycle:edit","style:gallery:list","style:gallery:edit","subscription:plan:list","subscription:plan:edit","subscription:member:list","subscription:member:edit","mkt:promotion","mkt:coupon","mkt:points","mkt:birthday:list","mkt:birthday:edit","mkt:member-day:list","mkt:member-day:edit","mkt:ecommerce:list","mkt:ecommerce:edit","mkt:pricing:rule:list","mkt:pricing:rule:edit","mkt:dispatch:list","mkt:dispatch:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_read AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:promotion","mkt:coupon","mkt:points","mkt:channel:list","mkt:template:list","mkt:touch:task:list","mkt:journey:list","mkt:journey:trigger:list","mkt:effect:view","mkt:conversion:view","mkt:pricing:experiment:list","mkt:pricing:rule:list","mkt:invite:list","mkt:birthday:list","mkt:member-day:list","mkt:dispatch:list","mkt:ecommerce:list","member:lifecycle:view","style:gallery:list","subscription:plan:list","subscription:member:list"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_excl AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:pricing:experiment:edit","mkt:pricing:rule:edit","mkt:dispatch:edit","mkt:ecommerce:edit","subscription:plan:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_legacy AS (
  SELECT jt.permission_code FROM JSON_TABLE('["market:coupon:add","market:coupon:edit","market:coupon:delete","market:point:add","market:point:edit","market:point:delete","market:activity:list","market:activity:add","market:activity:edit","market:activity:delete","market:activity:detail","market:activity:stat"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_cross AS (
  SELECT jt.permission_code FROM JSON_TABLE('["market:coupon:list","market:point:list"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_guard AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:effect","mkt:channel","mkt:template","mkt:touch-task","mkt:birthday","mkt:journey","mkt:conversion","mkt:pricing","mkt:dispatch","mkt:activity"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_menu_new AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt.promotion","mkt.coupon","mkt.points","mkt.birthday","mkt.invite","mkt.dispatch","mkt.member-day","mkt.style-gallery","mkt.style-gallery.quick-order","mkt.subscription","mkt.lifecycle","mkt.ecommerce"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_menu_old AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt.effect","mkt.channel","mkt.template","mkt.touch-task","mkt.journey","mkt.conversion","mkt.pricing"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
)
SELECT 'FINE' AS category, COUNT(*) AS code_count FROM cat_fine
UNION ALL SELECT 'READ', COUNT(*) FROM cat_read
UNION ALL SELECT 'EXCL', COUNT(*) FROM cat_excl
UNION ALL SELECT 'LEGACY', COUNT(*) FROM cat_legacy
UNION ALL SELECT 'CROSS', COUNT(*) FROM cat_cross
UNION ALL SELECT 'GUARD', COUNT(*) FROM cat_guard
UNION ALL SELECT 'MENU_NEW', COUNT(*) FROM cat_menu_new
UNION ALL SELECT 'MENU_OLD', COUNT(*) FROM cat_menu_old;

-- ----------------------------------------------------------------------------
-- V1 · 20 个新码是否已建（GUARD 10 + FINE 10）
-- ----------------------------------------------------------------------------
SELECT p.permission_code, p.permission_type, p.parent_id, p.status, p.deleted,
       CASE WHEN p.id IS NULL THEN 'MISSING' ELSE 'OK' END AS result
  FROM (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:effect","mkt:channel","mkt:template","mkt:touch-task","mkt:birthday","mkt:journey","mkt:conversion","mkt:pricing","mkt:dispatch","mkt:activity","mkt:birthday:list","mkt:birthday:edit","mkt:member-day:list","mkt:member-day:edit","mkt:ecommerce:list","mkt:ecommerce:edit","mkt:pricing:rule:list","mkt:pricing:rule:edit","mkt:dispatch:list","mkt:dispatch:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
  ) t
  LEFT JOIN sys_permission p ON p.permission_code = t.permission_code
 ORDER BY result, t.permission_code;

-- ----------------------------------------------------------------------------
-- V2 · 12 条新菜单行 + 7 条既有菜单行是否齐备
-- ----------------------------------------------------------------------------
SELECT p.permission_code, p.permission_name, p.permission_type, p.parent_id,
       mp.id AS menu_row_id, mp.route_path, mp.icon, mp.sort_order, mp.hidden,
       CASE WHEN mp.id IS NULL THEN 'MISSING' ELSE 'OK' END AS menu_row
  FROM (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt.promotion","mkt.coupon","mkt.points","mkt.birthday","mkt.invite","mkt.dispatch","mkt.member-day","mkt.style-gallery","mkt.style-gallery.quick-order","mkt.subscription","mkt.lifecycle","mkt.ecommerce","mkt.effect","mkt.channel","mkt.template","mkt.touch-task","mkt.journey","mkt.conversion","mkt.pricing"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
  ) t
  LEFT JOIN sys_permission p ON p.permission_code = t.permission_code
  LEFT JOIN sys_menu_permission mp ON mp.permission_id = p.id
 ORDER BY mp.sort_order, t.permission_code;

-- ----------------------------------------------------------------------------
-- V3 · 各角色绑定数（规格码口径 + 粗码 + 菜单码）
--     规则：ADMIN=FINE+LEGACY / BOSS=READ+CROSS / MANAGER=FINE+LEGACY+CROSS
--           OPERATOR=FINE+CROSS−EXCL / CLERK=CROSS
-- ----------------------------------------------------------------------------
WITH cat_fine AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:channel:list","mkt:channel:edit","mkt:template:list","mkt:template:edit","mkt:touch:task:list","mkt:touch:task:edit","mkt:journey:list","mkt:journey:edit","mkt:journey:trigger:list","mkt:journey:trigger:edit","mkt:effect:view","mkt:effect:edit","mkt:conversion:view","mkt:conversion:track","mkt:pricing:experiment:list","mkt:pricing:experiment:edit","mkt:invite:list","mkt:invite:edit","member:lifecycle:view","member:lifecycle:edit","style:gallery:list","style:gallery:edit","subscription:plan:list","subscription:plan:edit","subscription:member:list","subscription:member:edit","mkt:promotion","mkt:coupon","mkt:points","mkt:birthday:list","mkt:birthday:edit","mkt:member-day:list","mkt:member-day:edit","mkt:ecommerce:list","mkt:ecommerce:edit","mkt:pricing:rule:list","mkt:pricing:rule:edit","mkt:dispatch:list","mkt:dispatch:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_read AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:promotion","mkt:coupon","mkt:points","mkt:channel:list","mkt:template:list","mkt:touch:task:list","mkt:journey:list","mkt:journey:trigger:list","mkt:effect:view","mkt:conversion:view","mkt:pricing:experiment:list","mkt:pricing:rule:list","mkt:invite:list","mkt:birthday:list","mkt:member-day:list","mkt:dispatch:list","mkt:ecommerce:list","member:lifecycle:view","style:gallery:list","subscription:plan:list","subscription:member:list"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_excl AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:pricing:experiment:edit","mkt:pricing:rule:edit","mkt:dispatch:edit","mkt:ecommerce:edit","subscription:plan:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_legacy AS (
  SELECT jt.permission_code FROM JSON_TABLE('["market:coupon:add","market:coupon:edit","market:coupon:delete","market:point:add","market:point:edit","market:point:delete","market:activity:list","market:activity:add","market:activity:edit","market:activity:delete","market:activity:detail","market:activity:stat"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_cross AS (
  SELECT jt.permission_code FROM JSON_TABLE('["market:coupon:list","market:point:list"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_guard AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:effect","mkt:channel","mkt:template","mkt:touch-task","mkt:birthday","mkt:journey","mkt:conversion","mkt:pricing","mkt:dispatch","mkt:activity"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_menu_new AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt.promotion","mkt.coupon","mkt.points","mkt.birthday","mkt.invite","mkt.dispatch","mkt.member-day","mkt.style-gallery","mkt.style-gallery.quick-order","mkt.subscription","mkt.lifecycle","mkt.ecommerce"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_menu_old AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt.effect","mkt.channel","mkt.template","mkt.touch-task","mkt.journey","mkt.conversion","mkt.pricing"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
bound AS (
  SELECT r.role_code, p.permission_code
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
   WHERE r.role_code IN ('ADMIN', 'BOSS', 'MANAGER', 'OPERATOR', 'CLERK')
),
bound_cat AS (
  SELECT b.role_code, 'FINE' AS cat FROM bound b JOIN cat_fine c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'READ' FROM bound b JOIN cat_read c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'EXCL' FROM bound b JOIN cat_excl c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'LEGACY' FROM bound b JOIN cat_legacy c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'CROSS' FROM bound b JOIN cat_cross c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'GUARD' FROM bound b JOIN cat_guard c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'MENU_NEW' FROM bound b JOIN cat_menu_new c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'MENU_OLD' FROM bound b JOIN cat_menu_old c ON c.permission_code = b.permission_code
),
pivot AS (
  SELECT role_code,
         SUM(CASE WHEN cat = 'FINE' THEN 1 ELSE 0 END) AS fine_cnt,
         SUM(CASE WHEN cat = 'READ' THEN 1 ELSE 0 END) AS read_cnt,
         SUM(CASE WHEN cat = 'EXCL' THEN 1 ELSE 0 END) AS excl_cnt,
         SUM(CASE WHEN cat = 'LEGACY' THEN 1 ELSE 0 END) AS legacy_cnt,
         SUM(CASE WHEN cat = 'CROSS' THEN 1 ELSE 0 END) AS cross_cnt,
         SUM(CASE WHEN cat = 'GUARD' THEN 1 ELSE 0 END) AS guard_cnt,
         SUM(CASE WHEN cat = 'MENU_NEW' THEN 1 ELSE 0 END) AS menu_new_cnt,
         SUM(CASE WHEN cat = 'MENU_OLD' THEN 1 ELSE 0 END) AS menu_old_cnt
    FROM bound_cat GROUP BY role_code
)
SELECT role_code,
       CASE role_code WHEN 'ADMIN'    THEN fine_cnt + legacy_cnt
                      WHEN 'BOSS'     THEN read_cnt + cross_cnt
                      WHEN 'MANAGER'  THEN fine_cnt + legacy_cnt + cross_cnt
                      WHEN 'OPERATOR' THEN fine_cnt + cross_cnt - excl_cnt
                      WHEN 'CLERK'    THEN cross_cnt END AS spec_actual,
       CASE role_code WHEN 'ADMIN' THEN 51 WHEN 'BOSS' THEN 23 WHEN 'MANAGER' THEN 53
                      WHEN 'OPERATOR' THEN 36 WHEN 'CLERK' THEN 2 END AS spec_expected,
       fine_cnt, read_cnt, excl_cnt, legacy_cnt, cross_cnt,
       guard_cnt, menu_new_cnt, menu_old_cnt,
       CASE
         WHEN role_code = 'ADMIN'    AND fine_cnt = 39 AND legacy_cnt = 12 THEN 'PASS'
         WHEN role_code = 'BOSS'     AND read_cnt = 21 AND cross_cnt = 2  THEN 'PASS'
         WHEN role_code = 'MANAGER'  AND fine_cnt = 39 AND legacy_cnt = 12 AND cross_cnt = 2 THEN 'PASS'
         WHEN role_code = 'OPERATOR' AND fine_cnt = 34 AND cross_cnt = 2 AND excl_cnt = 0 THEN 'PASS'
         WHEN role_code = 'CLERK'    AND cross_cnt = 2 THEN 'PASS'
         ELSE 'FAIL' END AS spec_result
  FROM pivot
 ORDER BY FIELD(role_code, 'ADMIN','BOSS','MANAGER','OPERATOR','CLERK');

-- ----------------------------------------------------------------------------
-- V3b · 与用户口径（51 / 22 / 40 / 36 / 2）对账
--      口径裁决（用户 2026-11-09，取 A）：保留实际 51 / 23 / 53 / 36 / 2，不做裁剪。
--      本段把「用户口径」「库内实际」「偏差」「说明」四列显式打出，作为长期可追溯的偏差登记。
--      spec_actual 计算口径与 V3 完全一致：
--        ADMIN = FINE + LEGACY / BOSS = READ + CROSS / MANAGER = FINE + LEGACY + CROSS
--        OPERATOR = FINE + CROSS − EXCL / CLERK = CROSS
-- ----------------------------------------------------------------------------
WITH cat_fine AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:channel:list","mkt:channel:edit","mkt:template:list","mkt:template:edit","mkt:touch:task:list","mkt:touch:task:edit","mkt:journey:list","mkt:journey:edit","mkt:journey:trigger:list","mkt:journey:trigger:edit","mkt:effect:view","mkt:effect:edit","mkt:conversion:view","mkt:conversion:track","mkt:pricing:experiment:list","mkt:pricing:experiment:edit","mkt:invite:list","mkt:invite:edit","member:lifecycle:view","member:lifecycle:edit","style:gallery:list","style:gallery:edit","subscription:plan:list","subscription:plan:edit","subscription:member:list","subscription:member:edit","mkt:promotion","mkt:coupon","mkt:points","mkt:birthday:list","mkt:birthday:edit","mkt:member-day:list","mkt:member-day:edit","mkt:ecommerce:list","mkt:ecommerce:edit","mkt:pricing:rule:list","mkt:pricing:rule:edit","mkt:dispatch:list","mkt:dispatch:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_read AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:promotion","mkt:coupon","mkt:points","mkt:channel:list","mkt:template:list","mkt:touch:task:list","mkt:journey:list","mkt:journey:trigger:list","mkt:effect:view","mkt:conversion:view","mkt:pricing:experiment:list","mkt:pricing:rule:list","mkt:invite:list","mkt:birthday:list","mkt:member-day:list","mkt:dispatch:list","mkt:ecommerce:list","member:lifecycle:view","style:gallery:list","subscription:plan:list","subscription:member:list"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_excl AS (
  SELECT jt.permission_code FROM JSON_TABLE('["mkt:pricing:experiment:edit","mkt:pricing:rule:edit","mkt:dispatch:edit","mkt:ecommerce:edit","subscription:plan:edit"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_legacy AS (
  SELECT jt.permission_code FROM JSON_TABLE('["market:coupon:add","market:coupon:edit","market:coupon:delete","market:point:add","market:point:edit","market:point:delete","market:activity:list","market:activity:add","market:activity:edit","market:activity:delete","market:activity:detail","market:activity:stat"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
cat_cross AS (
  SELECT jt.permission_code FROM JSON_TABLE('["market:coupon:list","market:point:list"]', '$[*]' COLUMNS (permission_code VARCHAR(100) PATH '$')) jt
),
bound AS (
  SELECT r.role_code, p.permission_code
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
   WHERE r.role_code IN ('ADMIN', 'BOSS', 'MANAGER', 'OPERATOR', 'CLERK')
),
bound_cat AS (
  SELECT b.role_code, 'FINE' AS cat FROM bound b JOIN cat_fine c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'READ' FROM bound b JOIN cat_read c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'EXCL' FROM bound b JOIN cat_excl c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'LEGACY' FROM bound b JOIN cat_legacy c ON c.permission_code = b.permission_code
  UNION ALL SELECT b.role_code, 'CROSS' FROM bound b JOIN cat_cross c ON c.permission_code = b.permission_code
),
pivot AS (
  SELECT role_code,
         SUM(CASE WHEN cat = 'FINE' THEN 1 ELSE 0 END) AS fine_cnt,
         SUM(CASE WHEN cat = 'READ' THEN 1 ELSE 0 END) AS read_cnt,
         SUM(CASE WHEN cat = 'EXCL' THEN 1 ELSE 0 END) AS excl_cnt,
         SUM(CASE WHEN cat = 'LEGACY' THEN 1 ELSE 0 END) AS legacy_cnt,
         SUM(CASE WHEN cat = 'CROSS' THEN 1 ELSE 0 END) AS cross_cnt
    FROM bound_cat GROUP BY role_code
),
expectation (role_code, user_expect, note) AS (
  SELECT jt.role_code, jt.user_expect, jt.note FROM JSON_TABLE('[
    {"role_code":"ADMIN","user_expect":51,"note":"一致"},
    {"role_code":"BOSS","user_expect":22,"note":"报告 5.1 记 21 只读码，矩阵另给 2 个跨域只读码 market:coupon:list / market:point:list，实际 23"},
    {"role_code":"MANAGER","user_expect":40,"note":"报告清单C 的 MANAGER 行为 OPERATOR 行复制，未含 12 个 market:* 遗留 CRUD 码；矩阵对 MANAGER 判应为实际 53"},
    {"role_code":"OPERATOR","user_expect":36,"note":"一致"},
    {"role_code":"CLERK","user_expect":2,"note":"一致"}
  ]', '$[*]' COLUMNS (role_code VARCHAR(20) PATH '$.role_code', user_expect INT PATH '$.user_expect', note VARCHAR(300) PATH '$.note')) jt
),
actual AS (
  SELECT role_code,
         CASE role_code WHEN 'ADMIN'    THEN fine_cnt + legacy_cnt
                        WHEN 'BOSS'     THEN read_cnt + cross_cnt
                        WHEN 'MANAGER'  THEN fine_cnt + legacy_cnt + cross_cnt
                        WHEN 'OPERATOR' THEN fine_cnt + cross_cnt - excl_cnt
                        WHEN 'CLERK'    THEN cross_cnt END AS spec_actual
    FROM pivot
)
SELECT a.role_code,
       e.user_expect,
       a.spec_actual,
       a.spec_actual - e.user_expect AS delta,
       CASE WHEN a.spec_actual = e.user_expect THEN '一致'
            ELSE '已登记偏差（口径 A：不裁剪）' END AS verdict,
       e.note
  FROM actual a
  JOIN expectation e ON e.role_code = a.role_code
 ORDER BY FIELD(a.role_code, 'ADMIN','BOSS','MANAGER','OPERATOR','CLERK');

-- ----------------------------------------------------------------------------
-- V4 · 菜单可见性模拟（复刻 RbacService.listVisibleMenuByUserId）
-- ----------------------------------------------------------------------------
SELECT DISTINCT u.username, p.permission_code AS menu_code, mp.route_path, mp.sort_order
  FROM sys_user_role ur
  JOIN sys_user u ON u.id = ur.user_id
  JOIN sys_role r ON r.id = ur.role_id
  JOIN sys_role_permission rp ON rp.role_id = r.id
  JOIN sys_permission p ON p.id = rp.permission_id AND p.permission_type = 'menu' AND p.deleted = 0
  JOIN sys_menu_permission mp ON mp.permission_id = p.id
 WHERE u.username IN ('admin', 'manager01', 'operator01', 'boss01', 'clerk01')
   AND (mp.hidden IS NULL OR mp.hidden = 0)
   AND mp.permission_id IN (
     SELECT p2.id FROM sys_permission p2
      WHERE p2.permission_code IN ('mkt.promotion', 'mkt.coupon', 'mkt.points', 'mkt.birthday', 'mkt.invite', 'mkt.dispatch', 'mkt.member-day', 'mkt.style-gallery', 'mkt.style-gallery.quick-order', 'mkt.subscription', 'mkt.lifecycle', 'mkt.ecommerce', 'mkt.effect', 'mkt.channel', 'mkt.template', 'mkt.touch-task', 'mkt.journey', 'mkt.conversion', 'mkt.pricing'))
 ORDER BY u.username, mp.sort_order;

-- ----------------------------------------------------------------------------
-- V5 · 前端路由守卫模拟（复刻 router/index.ts 的 meta.permission 判定）
-- ----------------------------------------------------------------------------
WITH route_guard (route_path, guard_code) AS (
  SELECT jt.route_path, jt.guard_code FROM JSON_TABLE('[{"route_path":"/mkt/promotion","guard_code":"mkt:promotion"},{"route_path":"/mkt/coupon","guard_code":"mkt:coupon"},{"route_path":"/mkt/points","guard_code":"mkt:points"},{"route_path":"/mkt/effect","guard_code":"mkt:effect"},{"route_path":"/mkt/channel","guard_code":"mkt:channel"},{"route_path":"/mkt/template","guard_code":"mkt:template"},{"route_path":"/mkt/touch-task","guard_code":"mkt:touch-task"},{"route_path":"/mkt/birthday","guard_code":"mkt:birthday"},{"route_path":"/mkt/journey","guard_code":"mkt:journey"},{"route_path":"/m09/invite","guard_code":"mkt:invite:list"},{"route_path":"/mkt/conversion","guard_code":"mkt:conversion"},{"route_path":"/mkt/pricing","guard_code":"mkt:pricing"},{"route_path":"/m12/dispatch","guard_code":"mkt:dispatch"},{"route_path":"/member-day","guard_code":"mkt:activity"},{"route_path":"/style-gallery","guard_code":"mkt:activity"},{"route_path":"/style-gallery/quick-order","guard_code":"mkt:activity"},{"route_path":"/subscription","guard_code":"mkt:activity"},{"route_path":"/ecommerce","guard_code":"mkt:activity"},{"route_path":"/lifecycle","guard_code":"mkt:activity"}]', '$[*]' COLUMNS (route_path VARCHAR(255) PATH '$.route_path', guard_code VARCHAR(100) PATH '$.guard_code')) jt
),
u_roles AS (
  SELECT u.username, GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.role_code) AS role_codes
    FROM sys_user_role ur
    JOIN sys_user u ON u.id = ur.user_id
    JOIN sys_role r ON r.id = ur.role_id
   WHERE u.username IN ('admin', 'manager01', 'operator01', 'boss01', 'clerk01')
   GROUP BY u.username
),
u_perms AS (
  SELECT DISTINCT u.username, p.permission_code
    FROM sys_user_role ur
    JOIN sys_user u ON u.id = ur.user_id
    JOIN sys_permission p ON p.id IN (
      SELECT rp.permission_id FROM sys_role_permission rp
       WHERE rp.role_id = ur.role_id)
   WHERE u.username IN ('admin', 'manager01', 'operator01', 'boss01', 'clerk01')
)
-- 说明：按“用户权限集”判定（与前端 hasPermission 语义一致：多角色取并集）
SELECT rg.route_path, rg.guard_code, ur.username, ur.role_codes,
       CASE WHEN up.permission_code IS NULL THEN 'DENY(403)' ELSE 'PASS' END AS guard_result
  FROM route_guard rg
  CROSS JOIN u_roles ur
  LEFT JOIN u_perms up ON up.username = ur.username AND up.permission_code = rg.guard_code
 ORDER BY ur.username, rg.route_path;

-- ----------------------------------------------------------------------------
-- V6 · 孤儿/残留检查
-- ----------------------------------------------------------------------------
SELECT 'orphan_permission_market_list' AS check_item, COUNT(*) AS cnt
  FROM sys_permission WHERE permission_code = 'market:list'
UNION ALL SELECT 'menu_root_routepath_dup_market_coupon', COUNT(*)
  FROM sys_menu_permission WHERE route_path = '/market/coupon'
UNION ALL SELECT 'legacy_market_menu_rows_kept', COUNT(*)
  FROM sys_menu_permission WHERE route_path IN ('/market/coupon','/market/points','/market/activity');

-- ----------------------------------------------------------------------------
-- V7 · 只读断言：BOSS 不得持有任何写码；OPERATOR 不得持有 5 个受限 edit 码
-- ----------------------------------------------------------------------------
SELECT r.role_code, p.permission_code
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE r.role_code IN ('BOSS', 'OPERATOR')
   AND (p.permission_code LIKE '%:edit' OR p.permission_code LIKE '%:add'
        OR p.permission_code LIKE '%:delete' OR p.permission_code LIKE '%:track')
 ORDER BY r.role_code, p.permission_code;
-- 期望：BOSS 一行都不出现；OPERATOR 仅出现 mkt:conversion:track（转化打点，非配置写）

-- ----------------------------------------------------------------------------
-- V8 · 中文名称写入编码校验（HEX 应为 UTF-8 字节序，例：生日提醒 = E8839FE69C97FE59198）
-- ----------------------------------------------------------------------------
SELECT p.permission_code, p.permission_name, HEX(p.permission_name) AS name_hex
  FROM sys_permission p
 WHERE p.permission_code IN ('mkt.birthday', 'mkt.member-day', 'mkt.style-gallery')
    OR p.permission_code IN ('mkt.birthday:list', 'mkt:dispatch:edit')
 ORDER BY p.permission_code;
