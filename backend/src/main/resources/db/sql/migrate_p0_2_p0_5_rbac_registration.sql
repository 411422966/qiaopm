-- ============================================================
-- P0-2 + P0-5 RBAC 注册（幂等，分步骤执行避免自引用子查询错误）
-- ============================================================

USE meiyetec;

-- ============================================================
-- P0-2 技师等级管理
-- ============================================================

-- 1a. sys_permission：注册菜单权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted, create_time)
VALUES
    ('employee:level',         '技师等级',     'menu', 0, '/technician/level',      'Medal',       91, 1, 0, NOW()),
    ('employee:level:benefit', '等级权益',     'menu', 0, '/technician/benefit',    'Present',     92, 1, 0, NOW()),
    ('employee:level:eval',    '等级评定',     'menu', 0, '/technician/evaluation', 'Trophy',     93, 1, 0, NOW());

-- 1b. sys_menu_permission：注册路由 UI 信息
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive, create_time)
SELECT id, '/technician/level',      'Medal',   91, 0, 1, NOW() FROM sys_permission WHERE permission_code='employee:level'
ON DUPLICATE KEY UPDATE icon=VALUES(icon), sort_order=VALUES(sort_order), update_time=NOW();

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive, create_time)
SELECT id, '/technician/benefit',    'Present',  92, 0, 1, NOW() FROM sys_permission WHERE permission_code='employee:level:benefit'
ON DUPLICATE KEY UPDATE icon=VALUES(icon), sort_order=VALUES(sort_order), update_time=NOW();

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive, create_time)
SELECT id, '/technician/evaluation', 'Trophy',   93, 0, 1, NOW() FROM sys_permission WHERE permission_code='employee:level:eval'
ON DUPLICATE KEY UPDATE icon=VALUES(icon), sort_order=VALUES(sort_order), update_time=NOW();

-- 1c. sys_role_permission：分配给 ADMIN / SUPER_ADMIN / HQ_ADMIN / STORE_ADMIN
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'HQ_ADMIN', 'STORE_ADMIN')
  AND p.permission_code IN ('employee:level', 'employee:level:benefit', 'employee:level:eval')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- ============================================================
-- P0-5 耗材绑定管理
-- ============================================================

-- 2a. sys_permission：先注册父菜单（不含 parent_id 自引用）
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted, create_time)
VALUES
    ('material:binding',     '项目耗材配置', 'menu', 0, '/material/service-material', 'SetUp', 80, 1, 0, NOW());

-- 2b. sys_permission：注册按钮权限（parent_id 在上一步之后才能引用）
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted, create_time)
SELECT 'material:binding:add', '耗材绑定-新增', 'button',
       (SELECT id FROM sys_permission WHERE permission_code='material:binding' LIMIT 1),
       '/material/service-material', 'Plus', 1, 1, 0, NOW()
FROM sys_permission WHERE permission_code='material:binding' AND NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code='material:binding:add'
);

INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted, create_time)
SELECT 'material:binding:edit', '耗材绑定-编辑', 'button',
       (SELECT id FROM sys_permission WHERE permission_code='material:binding' LIMIT 1),
       '/material/service-material', 'Edit', 2, 1, 0, NOW()
FROM sys_permission WHERE permission_code='material:binding' AND NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code='material:binding:edit'
);

INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted, create_time)
SELECT 'material:binding:del', '耗材绑定-删除', 'button',
       (SELECT id FROM sys_permission WHERE permission_code='material:binding' LIMIT 1),
       '/material/service-material', 'Delete', 3, 1, 0, NOW()
FROM sys_permission WHERE permission_code='material:binding' AND NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code='material:binding:del'
);

-- 2c. sys_menu_permission：注册路由 UI 信息
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive, create_time)
SELECT id, '/material/service-material', 'SetUp', 80, 0, 1, NOW() FROM sys_permission WHERE permission_code='material:binding'
ON DUPLICATE KEY UPDATE icon=VALUES(icon), sort_order=VALUES(sort_order), update_time=NOW();

-- 2d. sys_role_permission：分配给 ADMIN / SUPER_ADMIN / HQ_ADMIN / STORE_ADMIN
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'HQ_ADMIN', 'STORE_ADMIN')
  AND p.permission_code IN ('material:binding', 'material:binding:add', 'material:binding:edit', 'material:binding:del')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- ============================================================
-- 验证查询
-- ============================================================
SELECT '=== P0-2 技师等级权限 ===' AS step;
SELECT permission_code, permission_name, permission_type FROM sys_permission WHERE permission_code LIKE 'employee:level%';

SELECT '=== P0-5 耗材绑定权限 ===' AS step;
SELECT permission_code, permission_name, permission_type FROM sys_permission WHERE permission_code LIKE 'material:binding%';

SELECT '=== sys_menu_permission 记录数 ===' AS step;
SELECT COUNT(*) AS menu_perm_count FROM sys_menu_permission;

SELECT '=== sys_role_permission 记录数 ===' AS step;
SELECT COUNT(*) AS role_perm_count FROM sys_role_permission;
