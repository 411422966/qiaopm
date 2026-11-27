-- ============================================================
-- I210 — /mh/visual 菜单重复入口修复
--  现象：sys_menu_permission 中 2 行 route_path = '/mh/visual'
--         （id=268 → permission_id=1205 'mh' 父菜单；id=314 → permission_id=1251 'mh.visual' 子菜单）
--         而 sys_permission 中 1205 与 1251 为父子两级、path 同为 '/mh/visual'
--         → buildMenuTree 拼树后出现两个指向同一路由的菜单入口
--   修复：父菜单 1205 的 route_path 改为 '/mh'（分组容器），1251 保持 '/mh/visual'
--   关联：audit/issues/I210-mh-visual-duplicate-menu-binding.md
--   幂等：WHERE 条件带原值，重复执行影响行数为 0
--   执行方式：手动执行（不放 db/migration/，避免 Flyway 启动期无人工确认自动 apply）
-- ============================================================

-- ---------- 前置检查（执行前应先跑，确认现状符合预期） ----------
-- SELECT smp.id, smp.permission_id, p.permission_code, p.parent_id, p.path, smp.route_path, smp.hidden
--   FROM sys_menu_permission smp
--   JOIN sys_permission p ON p.id = smp.permission_id
--  WHERE smp.route_path IN ('/mh/visual', '/mh');
-- 期望：id=268(route_path='/mh/visual', permission_id=1205) 与 id=314(route_path='/mh/visual', permission_id=1251) 各 1 行

-- ---------- 修复 ----------
UPDATE sys_menu_permission
   SET route_path = '/mh'
 WHERE id = 268
   AND route_path = '/mh/visual';

-- ---------- 验证（修复后应只返回 1 行） ----------
-- SELECT id, permission_id, route_path FROM sys_menu_permission WHERE route_path = '/mh/visual';
-- 期望：仅 id=314 一行

-- ============================================================
-- 回滚 SQL（逐条镜像）
-- ============================================================
-- UPDATE sys_menu_permission
--    SET route_path = '/mh/visual'
--  WHERE id = 268
--    AND route_path = '/mh';
--
-- ============================================================
-- 遗留风险（见 I210 §6）
--   父节点 route_path 现为 '/mh'，而前端 router/index.ts 无 '/mh' 路由。
--   若出现「只持 mh(1205) 而不持 mh.visual(1251)」的角色，父节点退化为可点击项 → 点击后 404。
--   当前不可达（现存角色持 1205 者均同时持 1251）。
-- 缓解：① 前端补 '/mh' → '/mh/visual' 重定向路由；② 父菜单 route_path 置 NULL（纯分组节点）。
-- ============================================================
