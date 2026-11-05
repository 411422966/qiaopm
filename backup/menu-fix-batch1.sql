-- ====================================================================
-- menu-fix-batch1.sql — I124-b 菜单补齐 · 批次 1
--
-- 范围（用户 2026-11-06 拍板）:
--   块 0  备份表（sys_role_permission / sys_menu_permission / sys_permission）
--   块 1  MANAGER 绑定「客户运营」「员工提成」顶级分类 + 其子菜单 + 配套守卫码
--   块 5.4 修复 13 个守卫码断裂（ADMIN / BOSS）
--   块 6  验证（6.1 可见菜单总数 / 6.2 顶级分类是否出现）
--
-- 明确不在本批次范围（留作 I124-c 战役蓝图）:
--   块 2  孤儿菜单行清理（crm.rfm hidden=1）
--   块 3  permission_type button -> menu 升级（会导致角色权限配置页显示混乱）
--   块 4  新建缺失权限码（52 条）
--   块 5.1/5.2/5.3  其余角色绑定
--
-- 关键事实（本次执行前已核实）:
--   - 菜单可见性 JOIN: backend/.../SysMenuPermissionMapper.java:14-24
--       sys_menu_permission smp
--       JOIN sys_permission p        ON smp.permission_id = p.id
--       JOIN sys_role_permission rp  ON p.id = rp.permission_id
--       JOIN sys_user_role ur        ON rp.role_id = ur.role_id
--       WHERE ur.user_id = ? AND p.permission_type='menu' AND p.deleted=0
--         AND (smp.hidden IS NULL OR smp.hidden=0)
--     => 顶级分类必须与子菜单同时绑定，否则子菜单被当成根节点平铺。
--   - 菜单树拼装依赖 sys_permission.parent_id
--     backend/.../RbacService.java:70-116
--   - 前端路由守卫: frontend/.../stores/permission.ts hasPermission()
--     命中 state.permissions（登录时由后端灌入）或通配 '*'
--   - 后端接口守卫: backend/.../security/PermissionAspect.java:47-55
--     只校验 code 是否在权限集合，不校验 permission_type
--   - '*' 通配只给 SUPER_ADMIN / PLATFORM_ADMIN
--     backend/.../security/RbacContextLoader.java:78-91
--     backend/.../service/AuthService.java:63-72
--     => ADMIN / BOSS 必须显式补齐守卫码
--
-- 目标库: 127.0.0.1:3307 / meiyetec
-- 执行: mysql -h 127.0.0.1 -P 3307 -u root -p meiyetec < backup/menu-fix-batch1.sql
-- 幂等: 全部 INSERT 带 NOT EXISTS 判重，可重复执行
-- 回滚: 见文末「回滚段」（默认注释）
-- ====================================================================

SET @db = DATABASE();

-- ====================================================================
-- 块 0: 备份（必须先跑）
-- ====================================================================

CREATE TABLE IF NOT EXISTS backup_sys_role_permission_bak_20261106 AS
  SELECT * FROM sys_role_permission;

CREATE TABLE IF NOT EXISTS backup_sys_menu_permission_bak_20261106 AS
  SELECT * FROM sys_menu_permission;

CREATE TABLE IF NOT EXISTS backup_sys_permission_bak_20261106 AS
  SELECT * FROM sys_permission;

SELECT 'BACKUP DONE' AS step,
       (SELECT COUNT(*) FROM backup_sys_role_permission_bak_20261106) AS role_perm_rows,
       (SELECT COUNT(*) FROM backup_sys_menu_permission_bak_20261106) AS menu_rows,
       (SELECT COUNT(*) FROM backup_sys_permission_bak_20261106)    AS perm_rows;

-- ====================================================================
-- 块 1: MANAGER 绑定「客户运营」「员工提成」
--
-- 现状（执行前核实）:
--   sys_permission 1203 commission '员工提成' / 1206 crm '客户运营'
--     -> sys_menu_permission 已有行（id 266 / 269, hidden=0）
--     -> 但 MANAGER 未绑定 -> 左侧菜单看不到这两个顶级分类
--   子菜单（parent_id=1203 / 1206, permission_type='menu'）
--     全部已有 sys_menu_permission 行 -> 只需补角色绑定
--
-- 1.1 顶级分类
-- ====================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'MANAGER'
   AND p.permission_code IN ('crm', 'commission')
   AND p.deleted = 0
   AND NOT EXISTS (
     SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ====================================================================
-- 1.2 「客户运营」子菜单（parent_id = 1206）
--     1253 crm.care-overview / 1254 crm.follow-task / 1255 crm.follow-plan
--     1256 crm.complaint     / 1257 crm.review      / 1258 crm.review-tpl
--     1259 crm.cross-rule     / 1260 crm.cross-record / 1262 crm.ltv
--     排除 1261 crm.rfm：前端路由已注释（router/index.ts intel/rfm 为注释行）
--     -> 属块 2 清理范围，本批次只是不给 MANAGER 绑，不改 hidden
-- ====================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r JOIN sys_permission p ON p.parent_id = 1206
 WHERE r.role_code = 'MANAGER'
   AND p.permission_type = 'menu'
   AND p.permission_code <> 'crm.rfm'
   AND p.deleted = 0
   AND NOT EXISTS (
     SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ====================================================================
-- 1.3 「员工提成」子菜单（parent_id = 1203）
--     1235 commission.rules / 1236 .records / 1237 .salary
--     1238 .settlement / 1239 .batch / 1240 .reversal
-- ====================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r JOIN sys_permission p ON p.parent_id = 1203
 WHERE r.role_code = 'MANAGER'
   AND p.permission_type = 'menu'
   AND p.deleted = 0
   AND NOT EXISTS (
     SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ====================================================================
-- 1.4 配套守卫码（否则菜单可见、点进去被前端路由守卫拦成 403）
--
-- 1.4.1 CRM 侧守卫码
--     前端路由 -> meta.permission 对照:
--       /crm/care/overview   -> crm:follow-up
--       /crm/care/follow-task -> crm:follow-up
--       /crm/care/follow-plan -> crm:follow-plan
--       /crm/care/complaint   -> crm:complaint
--       /crm/care/review      -> crm:review
--       /crm/care/review-tpl  -> crm:review-tpl
--       /crm/cross-rule       -> crm:cross-rule
--       /crm/cross-record     -> crm:cross-record
--       /intel/ltv            -> crm:tag
--     这批码执行前已绑定 MANAGER，本段为幂等兜底。
-- ====================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'MANAGER'
   AND p.permission_code IN (
     'crm:follow-up', 'crm:follow-plan', 'crm:complaint',
     'crm:review', 'crm:review-tpl', 'crm:cross-rule',
     'crm:cross-record', 'crm:tag')
   AND p.deleted = 0
   AND NOT EXISTS (
     SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ====================================================================
-- 1.4.2 提成侧守卫码 —— 块 1.3 的必要配套
--     前端路由 -> meta.permission:
--       /commission/rules      -> commission:rule:list
--       /commission/records    -> commission:record:list
--       /commission/salary     -> commission:salary:list
--       /commission/settlement -> commission:settlement:list
--       /commission/batch       -> commission:batch
--       /commission/reversal    -> commission:reversal
--     后端 controller 同样用这 6 个码（CommissionController.java:47/97/213/319/568）
--     这 6 个码执行前 roles=NONE（守卫码断裂），不给 MANAGER 补的话
--     1.3 绑完菜单点进去就是 403，块 1 目的落空。
-- ====================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code = 'MANAGER'
   AND p.permission_code IN (
     'commission:rule:list', 'commission:record:list', 'commission:salary:list',
     'commission:settlement:list', 'commission:batch', 'commission:reversal')
   AND p.deleted = 0
   AND NOT EXISTS (
     SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ====================================================================
-- 块 5.4: 修复守卫码断裂（ADMIN / BOSS）
--
-- 断裂码（执行前 roles=NONE，共 13 个）:
--   commission:rule:list / commission:record:list / commission:salary:list
--   commission:settlement:list / commission:batch / commission:reversal
--   market:coupon:list / market:point:list / market:activity:list
--   mkt:coupon / mkt:points / mkt:promotion
--   mkt:invite:list
--
-- 后端 @RequirePermission 实际使用位置:
--   CommissionController.java  : commission:rule:list / record:list / salary:list
--                                / settlement:list / batch / reversal
--   CouponController.java     : market:coupon:list
--   PointController.java      : market:point:list
--   PointGoodsController.java : market:point:list
--   MarketActivityController.java : market:activity:list
--   MktCouponController.java  : mkt:coupon
--   MktPointsController.java  : mkt:points
--   MktPromotionController.java: mkt:promotion
--   InviteController.java / MemberInviteController.java : mkt:invite:list
--
-- 不补 SUPER_ADMIN：通配 '*' 已放行（RbacContextLoader.java:78-91）
-- 不补 CLERK / OPERATOR / STORE_ADMIN：按用户拍板，本批次不扩权
-- ====================================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r CROSS JOIN sys_permission p
 WHERE r.role_code IN ('ADMIN', 'BOSS')
   AND p.permission_code IN (
     'commission:rule:list', 'commission:record:list', 'commission:salary:list',
     'commission:settlement:list', 'commission:batch', 'commission:reversal',
     'market:coupon:list', 'market:point:list', 'market:activity:list',
     'mkt:coupon', 'mkt:points', 'mkt:promotion', 'mkt:invite:list')
   AND p.deleted = 0
   AND NOT EXISTS (
     SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- ====================================================================
-- 块 6: 验证
-- ====================================================================

-- --------------------------------------------------------------------
-- 6.1 manager01 (user_id=900004, role MANAGER) 可见菜单行总数
--     口径与 SysMenuPermissionMapper.listVisibleMenuByUserId 一致
-- --------------------------------------------------------------------
SELECT COUNT(*) AS manager01_visible_menu_rows
  FROM sys_menu_permission smp
  JOIN sys_permission p      ON p.id = smp.permission_id
  JOIN sys_role_permission rp ON p.id = rp.permission_id
  JOIN sys_user_role ur      ON rp.role_id = ur.role_id
 WHERE ur.user_id = 900004
   AND p.permission_type = 'menu'
   AND p.deleted = 0
   AND (smp.hidden IS NULL OR smp.hidden = 0);

-- --------------------------------------------------------------------
-- 6.2 manager01 的顶级分类是否出现「客户运营」「员工提成」及其子项数
-- --------------------------------------------------------------------
SELECT p.permission_code, p.permission_name, COUNT(smp.id) AS child_count
  FROM sys_permission p
  JOIN sys_role_permission rp ON rp.permission_id = p.id
  JOIN sys_user_role ur       ON ur.role_id = rp.role_id
  LEFT JOIN sys_menu_permission smp ON smp.permission_id = p.id
 WHERE ur.user_id = 900004
   AND p.permission_code IN ('crm', 'commission')
 GROUP BY p.permission_code, p.permission_name;

-- --------------------------------------------------------------------
-- 6.3 manager01 可见的顶级分类清单（改后应含 crm / commission）
-- --------------------------------------------------------------------
SELECT p.permission_code, p.permission_name, COUNT(smp.id) AS visible_children
  FROM sys_permission p
  JOIN sys_role_permission rp ON rp.permission_id = p.id
  JOIN sys_user_role ur       ON ur.role_id = rp.role_id
  JOIN sys_menu_permission smp ON smp.permission_id = p.id
 WHERE ur.user_id = 900004
   AND p.permission_type = 'menu'
   AND p.deleted = 0
   AND (smp.hidden IS NULL OR smp.hidden = 0)
   AND (p.parent_id IS NULL OR p.parent_id = 0)
 GROUP BY p.permission_code, p.permission_name
 ORDER BY p.id;

-- --------------------------------------------------------------------
-- 6.4 13 个守卫码断裂是否已修复（期望 roles 不再出现 NONE）
-- --------------------------------------------------------------------
SELECT p.permission_code, p.permission_type,
       GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
  FROM sys_permission p
  LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
  LEFT JOIN sys_role r ON r.id = rp.role_id
 WHERE p.permission_code IN (
       'commission:rule:list', 'commission:record:list', 'commission:salary:list',
       'commission:settlement:list', 'commission:batch', 'commission:reversal',
       'market:coupon:list', 'market:point:list', 'market:activity:list',
       'mkt:coupon', 'mkt:points', 'mkt:promotion', 'mkt:invite:list')
 GROUP BY p.permission_code, p.permission_type
 ORDER BY p.permission_code;

-- --------------------------------------------------------------------
-- 6.5 绑定增量统计（本次脚本实际新增行数）
-- --------------------------------------------------------------------
SELECT 'role_permission delta' AS metric,
       (SELECT COUNT(*) FROM sys_role_permission) -
       (SELECT COUNT(*) FROM backup_sys_role_permission_bak_20261106) AS delta_rows;

-- ====================================================================
-- 回滚段（默认注释，按需手动执行）
--   DELETE rp FROM sys_role_permission rp
--     JOIN backup_sys_role_permission_bak_20261106 b USING (role_id, permission_id)
--    WHERE b.id IS NULL;
--   DELETE smp FROM sys_menu_permission smp
--     LEFT JOIN backup_sys_menu_permission_bak_20261106 b USING (id)
--    WHERE b.id IS NULL;
-- ====================================================================
