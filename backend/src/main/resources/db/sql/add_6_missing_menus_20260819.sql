-- =============================================
-- 补全 6 个遗漏的菜单项（2026-08-19）
--
-- 背景：
--   Layout.vue 的 fallbackMenus（前端硬编码兜底）已配置 6 个新菜单，
--   但 sys_permission + sys_menu_permission 表里没有对应记录，
--   导致「后端动态菜单」覆盖 fallback，用户看不到。
--
-- 涉及：
--   1. crm:follow-rule   -> /crm/care/follow-rule  (SetUp)
--   2. dev:intl-payment  -> /intl-payment         (CreditCard)
--   3. dev:intl-booking  -> /intl-booking         (Calendar)
--   4. dev:ai-scheduler  -> /ai-scheduler         (TrendCharts)
--   5. dev:risk-control  -> /risk-control         (Warning)
--   6. dev:google-maps   -> /google-maps          (MapLocation)
--
-- 用法：
--   [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | `
--     mysql -uroot -proot123456 -P3307 meiyetec
-- =============================================

USE meiyetec;

-- =============================================
-- A. sys_permission 补 6 条
-- =============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, status, deleted) VALUES
('crm:follow-rule',  '回访任务规则',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm'   AND deleted=0) t), 1, 0),
('dev:intl-payment', '国际支付',      'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dev'   AND deleted=0) t), 1, 0),
('dev:intl-booking', '国际预约',      'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dev'   AND deleted=0) t), 1, 0),
('dev:ai-scheduler', 'AI 智能排班',   'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dev'   AND deleted=0) t), 1, 0),
('dev:risk-control', '资金风控',      'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dev'   AND deleted=0) t), 1, 0),
('dev:google-maps',  'Google Maps',   'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dev'   AND deleted=0) t), 1, 0);

-- =============================================
-- B. sys_menu_permission 补 6 条
-- =============================================
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)
SELECT id, '/crm/care/follow-rule', 'SetUp',       99 FROM sys_permission WHERE permission_code='crm:follow-rule'  AND deleted=0;
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)
SELECT id, '/intl-payment',         'CreditCard',  100 FROM sys_permission WHERE permission_code='dev:intl-payment' AND deleted=0;
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)
SELECT id, '/intl-booking',         'Calendar',    101 FROM sys_permission WHERE permission_code='dev:intl-booking' AND deleted=0;
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)
SELECT id, '/ai-scheduler',         'TrendCharts', 102 FROM sys_permission WHERE permission_code='dev:ai-scheduler' AND deleted=0;
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)
SELECT id, '/risk-control',         'Warning',     103 FROM sys_permission WHERE permission_code='dev:risk-control' AND deleted=0;
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)
SELECT id, '/google-maps',          'MapLocation', 104 FROM sys_permission WHERE permission_code='dev:google-maps'  AND deleted=0;

-- =============================================
-- C. 给 ADMIN 角色绑定
-- =============================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code='ADMIN'
  AND p.permission_code IN (
    'crm:follow-rule',
    'dev:intl-payment',
    'dev:intl-booking',
    'dev:ai-scheduler',
    'dev:risk-control',
    'dev:google-maps'
  )
  AND p.deleted=0;

-- =============================================
-- D. 验证
-- =============================================
SELECT '=== ADMIN 可见的 6 个新增菜单 ===' AS '';
SELECT p.permission_code, p.permission_name, smp.route_path, smp.icon
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id=r.id
JOIN sys_permission p ON rp.permission_id=p.id
JOIN sys_menu_permission smp ON smp.permission_id=p.id
WHERE r.role_code='ADMIN'
  AND p.permission_code IN (
    'crm:follow-rule',
    'dev:intl-payment',
    'dev:intl-booking',
    'dev:ai-scheduler',
    'dev:risk-control',
    'dev:google-maps'
  )
ORDER BY smp.sort_order;