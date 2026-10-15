-- ====================================================================
-- M09-02 裂变邀请权限 & 菜单路由注册
-- 适用：InviteController（/m09/invite/*）
-- 权限：mkt:invite（菜单） + mkt:invite:list + mkt:invite:edit
-- ====================================================================

-- A. 注册权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id) VALUES
('mkt:invite',        '裂变邀请',   'menu', NULL),
('mkt:invite:list',   '邀请列表',   'button', NULL),
('mkt:invite:edit',   '邀请编辑',   'button', NULL);

-- B. 注册菜单路由
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, component_path, icon, sort_order)
SELECT id, '/m09/invite', 'views/m09-invite/index.vue', 'TrendCharts', 10
FROM sys_permission WHERE permission_code = 'mkt:invite';

-- C. 分配给 ADMIN / SUPER_ADMIN / BOSS 角色（permission_code IN）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('ADMIN','SUPER_ADMIN','BOSS','STORE_ADMIN')
  AND p.permission_code IN ('mkt:invite', 'mkt:invite:list', 'mkt:invite:edit')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );
