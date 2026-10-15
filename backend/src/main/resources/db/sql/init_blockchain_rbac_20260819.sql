-- ============================================
-- P2-003 区块链集成 RBAC（基于 sys_permission.path 模式）
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 权限菜单（用 path + icon 内嵌在 sys_permission）
-- ============================================
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order, status, deleted, create_time)
VALUES
    ('blockchain',         'Blockchain',         'menu', 0, '/blockchain',         'Connection', 90, 1, 0, NOW()),
    ('blockchain:trace',   'Blockchain Trace',   'menu', 0, '/blockchain/trace',   'Search',     91, 1, 0, NOW()),
    ('blockchain:monitor', 'Blockchain Monitor', 'menu', 0, '/blockchain/monitor', 'Monitor',    92, 1, 0, NOW()),
    ('blockchain:record',  'Blockchain Record',  'menu', 0, '/blockchain/record',  'Edit',       93, 1, 0, NOW()),
    ('blockchain:chain',   'Blockchain Chain',   'menu', 0, '/blockchain/chain',   'Link',       94, 1, 0, NOW())
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    path = VALUES(path),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order);

-- 把子菜单挂到 blockchain 父节点下
UPDATE sys_permission
SET parent_id = (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'blockchain') AS t)
WHERE permission_code IN ('blockchain:trace', 'blockchain:monitor', 'blockchain:record', 'blockchain:chain')
  AND deleted = 0;

-- ============================================
-- 2. 菜单 UI 扩展（sys_menu_permission）
-- ============================================
INSERT INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive, create_time, update_time)
SELECT id, path, icon, sort_order, 0, 1, NOW(), NOW()
FROM sys_permission
WHERE permission_code IN ('blockchain', 'blockchain:trace', 'blockchain:monitor', 'blockchain:record', 'blockchain:chain')
  AND deleted = 0
ON DUPLICATE KEY UPDATE
    route_path = VALUES(route_path),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order),
    update_time = NOW();

-- ============================================
-- 3. 赋权给 ADMIN / SUPER_ADMIN
-- ============================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, create_time)
SELECT r.id, p.id, NOW()
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('blockchain', 'blockchain:trace', 'blockchain:monitor', 'blockchain:record', 'blockchain:chain')
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND r.deleted = 0
  AND p.deleted = 0;

-- ============================================
-- 4. 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'blockchain%' AND deleted = 0) AS perm_count,
    (SELECT COUNT(*) FROM sys_menu_permission smp JOIN sys_permission p ON p.id = smp.permission_id WHERE p.permission_code LIKE 'blockchain%' AND p.deleted = 0) AS menu_count;
