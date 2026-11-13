-- =============================================
-- 补 /ltv-churn 菜单入口（dev.ltv-churn）（2026-11-13）
--
-- 背景：
--   前端路由已存在 /ltv-churn（frontend/meiyetec-web/src/router/index.ts:164，
--   guard = crm:tag），Layout.vue 的 fallbackMenus 也已硬编码该入口
--   （components/Layout.vue:297），但后端动态菜单表里没有对应记录。
--   后端菜单树基于 sys_menu_permission + sys_permission.parent_id 推树
--   （RbacService.buildMenuTree），动态菜单一旦非空就会覆盖 fallbackMenus
--   （Layout.vue:328-334），导致 /ltv-churn 在侧边栏无入口。
--
-- 菜单码约定：沿用 dev.* 系列（dev.gray-release / dev.ltv-training / ...），
--   挂在「研发工具」组（sys_permission.id = 1210, permission_code = 'dev'）下。
--
-- 角色绑定依据（2026-11-13 实测 sys_role_permission）：
--   dev.ltv-training (id=1279) 现绑 role 1 (SUPER_ADMIN) + 8 (ADMIN)
--   dev            (id=1210) 现绑 role 1 (SUPER_ADMIN) + 8 (ADMIN)
--   crm:tag        (id=1591, /ltv-churn 的前端路由守卫) 现绑 role 5 (MANAGER) + 6 (BOSS) + 8 (ADMIN)
--
--   本次绑定：SUPER_ADMIN / ADMIN / MANAGER / BOSS
--   不绑 OPERATOR(role 7)：dev.ltv-training 没绑它，且它也没有 crm:tag
--     —— 绑了就会出现「菜单可见但点击跳 /403」的坏体验。
--     守卫码按决策不改，故此处不绑。
--
--   同时补绑父级 dev(1210) 给 MANAGER / BOSS：菜单树按 parent_id 拼装，
--   父节点不可见时子节点会被丢弃，MANAGER / BOSS 看不到「研发工具」组。
--   注：已确认后端无任何 @RequirePermission("dev") / 'dev' 系列校验，
--       绑定 1210 不会额外开放后端接口。
--
-- 用法：
--   [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | `
--     mysql -uroot -proot123456 -P3307 meiyetec
--
-- 幂等：可重复执行（INSERT IGNORE + NOT EXISTS 守卫）
-- 回滚：见文末 ROLLBACK 段
-- =============================================

SET NAMES utf8mb4;

USE meiyetec;

-- =============================================
-- A. sys_permission 新增菜单行
--    sort_order = 12：紧接 dev.care-plan(11) 之后，
--    排在 dev:intl-payment(101) 等扩展菜单之前
-- =============================================
INSERT IGNORE INTO sys_permission
  (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted)
SELECT 'dev.ltv-churn', 'LTV/Churn 分析', 'menu', p.id, '/ltv-churn', 'DataLine', 12, 1, 0
FROM sys_permission p
WHERE p.permission_code = 'dev' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM sys_permission WHERE permission_code = 'dev.ltv-churn') t
  );

-- =============================================
-- B. sys_menu_permission 新增菜单映射行
--    route_path = '/ltv-churn'：Layout.vue 的 resolvePath 对以 '/' 开头的
--    子路径原样返回，因此最终菜单 index 就是 /ltv-churn
--    icon = 'DataLine'：取自 Layout.vue fallbackMenus 中 /ltv-churn 的图标
-- =============================================
INSERT IGNORE INTO sys_menu_permission
  (permission_id, route_path, component_path, redirect_path, icon, hidden, keep_alive, sort_order)
SELECT id, '/ltv-churn', NULL, NULL, 'DataLine', 0, 1, 12
FROM sys_permission
WHERE permission_code = 'dev.ltv-churn' AND deleted = 0;

-- =============================================
-- C. 角色绑定：dev.ltv-churn → SUPER_ADMIN / ADMIN / MANAGER / BOSS
-- =============================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'BOSS')
  AND p.permission_code = 'dev.ltv-churn'
  AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM (
      SELECT role_id, permission_id FROM sys_role_permission
    ) t WHERE t.role_id = r.id AND t.permission_id = p.id
  );

-- =============================================
-- D. 补绑父级「研发工具」dev(1210) 给 MANAGER / BOSS
--    菜单树按 sys_permission.parent_id 拼装，父节点不可见则子节点被丢弃
--    —— 不补这一步，MANAGER / BOSS 侧边栏不会出现「研发工具」组
-- =============================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('MANAGER', 'BOSS')
  AND p.permission_code = 'dev'
  AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM (
      SELECT role_id, permission_id FROM sys_role_permission
    ) t WHERE t.role_id = r.id AND t.permission_id = p.id
  );

-- =============================================
-- E. 验证
-- =============================================
SELECT '=== E1. 新增的 sys_permission 行 ===' AS '';
SELECT id, permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted
FROM sys_permission
WHERE permission_code = 'dev.ltv-churn';

SELECT '=== E2. 新增的 sys_menu_permission 行 ===' AS '';
SELECT id, permission_id, route_path, icon, sort_order, hidden, keep_alive
FROM sys_menu_permission
WHERE permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'dev.ltv-churn' AND deleted = 0);

SELECT '=== E3. dev.ltv-churn 的角色绑定 ===' AS '';
SELECT r.role_code, r.role_name
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id = r.id
JOIN sys_permission p ON rp.permission_id = p.id
WHERE p.permission_code = 'dev.ltv-churn'
ORDER BY r.id;

SELECT '=== E4. 父级 dev(1210) 的角色绑定 ===' AS '';
SELECT r.role_code, r.role_name
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id = r.id
JOIN sys_permission p ON rp.permission_id = p.id
WHERE p.permission_code = 'dev'
ORDER BY r.id;

SELECT '=== E5. 研发工具组下的子菜单排序（应含 dev.ltv-churn @ 12）===' AS '';
SELECT p.permission_code, p.permission_name, smp.route_path, smp.icon, smp.sort_order
FROM sys_permission p
JOIN sys_menu_permission smp ON smp.permission_id = p.id
WHERE p.parent_id = (SELECT id FROM sys_permission WHERE permission_code = 'dev' AND deleted = 0)
  AND p.deleted = 0
ORDER BY smp.sort_order;

-- =============================================
-- ROLLBACK（按需手动执行）
-- =============================================
-- DELETE FROM sys_role_permission WHERE permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'dev.ltv-churn');
-- DELETE FROM sys_menu_permission WHERE permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'dev.ltv-churn');
-- DELETE FROM sys_permission WHERE permission_code = 'dev.ltv-churn';
-- DELETE FROM sys_role_permission
--   WHERE permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'dev')
--     AND role_id IN (SELECT id FROM sys_role WHERE role_code IN ('MANAGER','BOSS'));
