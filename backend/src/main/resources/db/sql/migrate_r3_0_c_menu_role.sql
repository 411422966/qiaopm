-- =============================================
-- 美业 SaaS - R3.0-C 菜单角色化与命名优化
-- 2026-08-16
-- 依赖：init_rbac_v2, init_rbac_v3_0_* 已执行
-- =============================================

USE meiyetec;

-- =============================================
-- 1. 新增 sys_role：5 个预置业务角色
-- =============================================
INSERT IGNORE INTO sys_role (role_code, role_name, description, status) VALUES
('CLERK',          '店员',     '一线操作员：收银/预约/会员/库存/基础资料', 1),
('MANAGER',        '店长',     '门店管理者：店员 + 提成/报表/营销/医美合规', 1),
('BOSS',           '老板',     '企业主：店长 + 客户运营/组织架构/系统设置', 1),
('OPERATOR',       '运营',     '运营专员：数据报表/营销工具/客户运营', 1),
('ADMIN',          '系统管理员', '技术管理员：全部菜单，含研发工具', 1);

-- =============================================
-- 2. 数据范围（全部为 STORE，ADMIN 为 ALL）
-- =============================================
INSERT IGNORE INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '店员仅当前门店数据' FROM sys_role r WHERE r.role_code = 'CLERK';

INSERT IGNORE INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '店长仅当前门店数据' FROM sys_role r WHERE r.role_code = 'MANAGER';

INSERT IGNORE INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '老板可见全部门店' FROM sys_role r WHERE r.role_code = 'BOSS';

INSERT IGNORE INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '运营可见全部门店' FROM sys_role r WHERE r.role_code = 'OPERATOR';

INSERT IGNORE INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'ALL', NULL, '管理员跨租户' FROM sys_role r WHERE r.role_code = 'ADMIN';

-- =============================================
-- 3. sys_menu_permission 更新菜单名称
-- =============================================

-- 3.1 商品/耗材 → 商品耗材
UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET p.permission_name = '商品耗材'
WHERE p.permission_code = 'stock:list';

-- 3.2 LTV + Churn 模型训练 → LTV流失预测
UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET p.permission_name = 'LTV流失预测'
WHERE p.permission_code = 'dev:ltv-training';

-- 3.3 护肤方案 + 共享套餐 → 护肤方案共享套餐
UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET p.permission_name = '护肤方案共享套餐'
WHERE p.permission_code = 'dev:care-plan';

-- 3.4 电子价目表 + 卡项套餐 → 电子价目表卡项套餐
UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET p.permission_name = '电子价目表卡项套餐'
WHERE p.permission_code = 'dev:pricelist';

-- 3.5 P1/P2/P3/补齐套件 统一改为 套件合集
UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET p.permission_name = '套件合集'
WHERE p.permission_code IN ('dev:p1-suite', 'dev:p2-suite', 'dev:p3-suite', 'dev:gap-suite');

-- =============================================
-- 4. 菜单权限码绑定（按角色）
--
-- 角色菜单映射：
-- CLERK    : 首页 / 收银结算 / 预约管理 / 会员管理 / 储值管理 / 套餐次卡 / 库存管理 / 基础资料
-- MANAGER  : CLERK + 员工提成 / 数据报表 / 营销工具 / 医美合规
-- BOSS     : MANAGER + 客户运营 / 组织架构 / 系统设置
-- OPERATOR : 首页 / 数据报表 / 营销工具 / 客户运营
-- ADMIN    : 全部（含研发工具）
-- =============================================

-- --- CLERK ---
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'CLERK'
  AND p.permission_code IN (
    -- 首页
    'dashboard',
    -- 收银结算（全部子菜单）
    'pos', 'pos:list', 'pos:order:add', 'pos:order:pay', 'pos:order:refund', 'pos:order:void', 'pos:order:list', 'pos:order:stat',
    'pos:refund', 'pos:reconciliation:list',
    -- 预约管理（全部子菜单）
    'booking', 'booking:list', 'booking:add', 'booking:edit', 'booking:delete', 'booking:confirm', 'booking:cancel', 'booking:checkin', 'booking:start', 'booking:complete', 'booking:noshow',
    'booking:schedule:list', 'booking:schedule:add', 'booking:schedule:edit', 'booking:schedule:delete',
    -- 会员管理
    'member', 'member:list',
    -- 储值管理
    'wallet', 'wallet:list', 'wallet:recharge', 'wallet:consume', 'wallet:refund', 'wallet:promotion',
    -- 套餐次卡
    'package', 'package:list', 'package:add', 'package:edit', 'package:delete', 'package:purchase', 'package:redeem', 'package:freeze', 'package:transfer', 'package:audit',
    -- 库存管理（全部子菜单）
    'inventory', 'stock:list', 'stock:add', 'stock:edit', 'stock:delete', 'stock:inbound', 'stock:outbound', 'stock:check', 'stock:alert', 'stock:stat', 'stock:export',
    'stock:consumption', 'stock:record',
    -- 基础资料
    'biz:service-item', 'biz:treatment-plan',
    'dev:pricelist', 'dev:care-plan'
  );

-- --- MANAGER ---
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'MANAGER'
  AND p.permission_code IN (
    -- CLERK 的全部
    'dashboard',
    'pos', 'pos:list', 'pos:order:add', 'pos:order:pay', 'pos:order:refund', 'pos:order:void', 'pos:order:list', 'pos:order:stat',
    'pos:refund', 'pos:reconciliation:list',
    'booking', 'booking:list', 'booking:add', 'booking:edit', 'booking:delete', 'booking:confirm', 'booking:cancel', 'booking:checkin', 'booking:start', 'booking:complete', 'booking:noshow',
    'booking:schedule:list', 'booking:schedule:add', 'booking:schedule:edit', 'booking:schedule:delete',
    'member', 'member:list',
    'wallet', 'wallet:list', 'wallet:recharge', 'wallet:consume', 'wallet:refund', 'wallet:promotion',
    'package', 'package:list', 'package:add', 'package:edit', 'package:delete', 'package:purchase', 'package:redeem', 'package:freeze', 'package:transfer', 'package:audit',
    'inventory', 'stock:list', 'stock:add', 'stock:edit', 'stock:delete', 'stock:inbound', 'stock:outbound', 'stock:check', 'stock:alert', 'stock:stat', 'stock:export',
    'stock:consumption', 'stock:record',
    'biz:service-item', 'biz:treatment-plan',
    'dev:pricelist', 'dev:care-plan',
    -- MANAGER 独有
    -- 员工提成
    'commission', 'commission:list',
    'commission:rule:list', 'commission:rule:add', 'commission:rule:edit', 'commission:rule:delete',
    'commission:record:list', 'commission:salary:list', 'commission:salary:confirm', 'commission:salary:pay',
    'commission:settlement:list', 'commission:settlement:audit',
    'commission:batch', 'commission:reversal',
    -- 数据报表
    'report', 'report:list', 'report:dashboard', 'report:biz:list', 'report:export:add', 'report:export:download', 'report:export:list',
    'report:task', 'report:caliber',
    -- 营销工具
    'marketing', 'market:list',
    'market:coupon:list', 'market:coupon:add', 'market:coupon:edit', 'market:coupon:delete',
    'market:point:list', 'market:point:add', 'market:point:edit', 'market:point:delete',
    'market:activity:list', 'market:activity:add', 'market:activity:edit', 'market:activity:delete',
    'mkt:promotion', 'mkt:coupon', 'mkt:points', 'mkt:effect',
    'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:journey', 'mkt:conversion', 'mkt:pricing',
    -- 医美合规
    'mh:visual', 'mh:consent'
  );

-- --- BOSS ---
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'BOSS'
  AND p.permission_code IN (
    -- MANAGER 的全部
    'dashboard',
    'pos', 'pos:list', 'pos:order:add', 'pos:order:pay', 'pos:order:refund', 'pos:order:void', 'pos:order:list', 'pos:order:stat',
    'pos:refund', 'pos:reconciliation:list',
    'booking', 'booking:list', 'booking:add', 'booking:edit', 'booking:delete', 'booking:confirm', 'booking:cancel', 'booking:checkin', 'booking:start', 'booking:complete', 'booking:noshow',
    'booking:schedule:list', 'booking:schedule:add', 'booking:schedule:edit', 'booking:schedule:delete',
    'member', 'member:list',
    'wallet', 'wallet:list', 'wallet:recharge', 'wallet:consume', 'wallet:refund', 'wallet:promotion',
    'package', 'package:list', 'package:add', 'package:edit', 'package:delete', 'package:purchase', 'package:redeem', 'package:freeze', 'package:transfer', 'package:audit',
    'inventory', 'stock:list', 'stock:add', 'stock:edit', 'stock:delete', 'stock:inbound', 'stock:outbound', 'stock:check', 'stock:alert', 'stock:stat', 'stock:export',
    'stock:consumption', 'stock:record',
    'biz:service-item', 'biz:treatment-plan',
    'dev:pricelist', 'dev:care-plan',
    'commission', 'commission:list',
    'commission:rule:list', 'commission:rule:add', 'commission:rule:edit', 'commission:rule:delete',
    'commission:record:list', 'commission:salary:list', 'commission:salary:confirm', 'commission:salary:pay',
    'commission:settlement:list', 'commission:settlement:audit',
    'commission:batch', 'commission:reversal',
    'report', 'report:list', 'report:dashboard', 'report:biz:list', 'report:export:add', 'report:export:download', 'report:export:list',
    'report:task', 'report:caliber',
    'marketing', 'market:list',
    'market:coupon:list', 'market:coupon:add', 'market:coupon:edit', 'market:coupon:delete',
    'market:point:list', 'market:point:add', 'market:point:edit', 'market:point:delete',
    'market:activity:list', 'market:activity:add', 'market:activity:edit', 'market:activity:delete',
    'mkt:promotion', 'mkt:coupon', 'mkt:points', 'mkt:effect',
    'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:journey', 'mkt:conversion', 'mkt:pricing',
    'mh:visual', 'mh:consent',
    -- BOSS 独有
    -- 客户运营
    'crm:follow-up', 'crm:complaint', 'crm:review', 'crm:review-tpl', 'crm:cross-rule', 'crm:cross-record', 'crm:rfm', 'crm:tag',
    'crm:package-share',
    -- 组织架构
    'org:hq', 'org:brand', 'org:store',
    -- 系统设置
    'setting', 'sys:config:list', 'sys:config:edit',
    'sys:tenant:list', 'sys:tenant:add', 'sys:tenant:edit', 'sys:tenant:delete', 'sys:tenant:freeze',
    'sys:log:list', 'sys:log:export', 'sys:log:clear',
    'sys:login-log:list', 'sys:login-log:export', 'sys:login-log:clear',
    'sys:dcep:view', 'sys:dcep:edit', 'sys:dcep:test', 'sys:dcep:qualification',
    'employee', 'employee:list', 'employee:add', 'employee:edit', 'employee:delete', 'employee:reset-password'
  );

-- --- OPERATOR ---
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'OPERATOR'
  AND p.permission_code IN (
    -- 首页
    'dashboard',
    -- 数据报表
    'report', 'report:list', 'report:dashboard', 'report:biz:list', 'report:export:add', 'report:export:download', 'report:export:list',
    'report:task', 'report:caliber',
    -- 营销工具
    'marketing', 'market:list',
    'market:coupon:list', 'market:coupon:add', 'market:coupon:edit', 'market:coupon:delete',
    'market:point:list', 'market:point:add', 'market:point:edit', 'market:point:delete',
    'market:activity:list', 'market:activity:add', 'market:activity:edit', 'market:activity:delete',
    'mkt:promotion', 'mkt:coupon', 'mkt:points', 'mkt:effect',
    'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:journey', 'mkt:conversion', 'mkt:pricing',
    -- 客户运营
    'crm:follow-up', 'crm:complaint', 'crm:review', 'crm:review-tpl', 'crm:cross-rule', 'crm:cross-record', 'crm:rfm', 'crm:tag',
    'crm:package-share'
  );

-- --- ADMIN ---
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code NOT IN (
    -- ADMIN 排除但不存在的权限码（通过全量授予实现）
  );
-- ADMIN 全量授予：除了 SUPER_ADMIN / PLATFORM_ADMIN 的系统内置角色权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- =============================================
-- 5. sys_menu_permission 菜单绑定
--    为新角色在 sys_menu_permission 中补充菜单项
--    说明：sys_menu_permission.permission_id 需要指向 sys_permission
--          此处仅更新已有菜单的 sort_order，不新增记录
-- =============================================

-- 排序优化：按模块 + 角色优先级排
-- 首页 = 0, 收银 = 1, 预约 = 2, 会员 = 3, 储值 = 4, 套餐 = 5, 库存 = 6, 基础资料 = 7,
-- 提成 = 8, 报表 = 9, 营销 = 10, 医美 = 11, 客户运营 = 12, 组织 = 13, 设置 = 14, 研发 = 99

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 0
WHERE p.permission_code = 'dashboard';

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 1
WHERE p.permission_code IN ('pos', 'pos:list');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 2
WHERE p.permission_code IN ('booking', 'booking:list');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 3
WHERE p.permission_code IN ('member', 'member:list');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 4
WHERE p.permission_code IN ('wallet', 'wallet:list');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 5
WHERE p.permission_code IN ('package', 'package:list');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 6
WHERE p.permission_code IN ('inventory', 'stock:list', 'stock:consumption', 'stock:record');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 7
WHERE p.permission_code IN ('biz:service-item', 'biz:treatment-plan', 'dev:pricelist', 'dev:care-plan');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 8
WHERE p.permission_code IN ('commission', 'commission:list', 'commission:batch', 'commission:reversal');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 9
WHERE p.permission_code IN ('report', 'report:list', 'report:task', 'report:caliber');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 10
WHERE p.permission_code IN ('marketing', 'market:list', 'mkt:promotion', 'mkt:coupon', 'mkt:points',
    'mkt:effect', 'mkt:channel', 'mkt:template', 'mkt:touch-task', 'mkt:journey', 'mkt:conversion', 'mkt:pricing');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 11
WHERE p.permission_code IN ('mh:visual', 'mh:consent');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 12
WHERE p.permission_code IN ('crm:follow-up', 'crm:cross-rule', 'crm:cross-record', 'crm:rfm', 'crm:tag',
    'crm:package-share');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 13
WHERE p.permission_code IN ('org:hq', 'org:brand', 'org:store');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 14
WHERE p.permission_code IN ('setting', 'employee', 'sys:dcep:qualification');

UPDATE sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
SET smp.sort_order = 99
WHERE p.permission_code LIKE 'dev:%';

-- =============================================
-- 6. 验证
-- =============================================
SELECT '=== 角色列表 ===' AS '';
SELECT id, role_code, role_name, description FROM sys_role
WHERE role_code IN ('CLERK','MANAGER','BOSS','OPERATOR','ADMIN')
ORDER BY id;

SELECT '=== 角色-菜单权限数量 ===' AS '';
SELECT sr.role_code, COUNT(DISTINCT rp.permission_id) AS perm_count
FROM sys_role sr
LEFT JOIN sys_role_permission rp ON rp.role_id = sr.id
WHERE sr.role_code IN ('CLERK','MANAGER','BOSS','OPERATOR','ADMIN')
GROUP BY sr.role_code
ORDER BY sr.id;

SELECT '=== 菜单名称更新 ===' AS '';
SELECT p.permission_code, p.permission_name
FROM sys_permission p
WHERE p.permission_code IN (
    'stock:list', 'dev:ltv-training', 'dev:care-plan', 'dev:pricelist',
    'dev:p1-suite', 'dev:p2-suite', 'dev:p3-suite', 'dev:gap-suite'
)
ORDER BY p.permission_code;

SELECT '=== 菜单排序 ===' AS '';
SELECT p.permission_code, smp.sort_order
FROM sys_menu_permission smp
INNER JOIN sys_permission p ON smp.permission_id = p.id
WHERE p.permission_code LIKE 'dev:%'
   OR p.permission_code IN ('dashboard','pos','booking','member','wallet','package','inventory',
       'report','marketing','mh:visual','crm:follow-up','org:hq','setting')
ORDER BY smp.sort_order;
