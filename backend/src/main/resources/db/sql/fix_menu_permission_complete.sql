-- =============================================
-- 修复：补全 sys_permission + sys_menu_permission
--
-- 背景：当前 sys_permission 只有 13 条（9 menu + 4 button），
--       sys_menu_permission 也只有 9 条路由条目，
--       即使是 ADMIN / SUPER_ADMIN 也只能看到 9 个菜单。
--
-- 本 SQL 补全以下模块（让 ADMIN 看到所有菜单，符合"管理员可见全部"的预期）：
--   1. 储值管理 /wallet
--   2. 套餐次卡 /package
--   3. 基础资料 /biz (service-item, treatment-plan)
--   4. 客户运营 /crm (care/*, cross-*, intel/*)
--   5. 组织架构 /org (hq, brand, store)
--   6. 医美合规 /mh (visual, consent)
--   7. 研发工具 /dev (gray-release, ltv-training, datahub, ...)
--   8. 钱包 / 预约 / 库存 / 收银 等按钮级权限（button 类型）
--
-- 用法：
--   [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | `
--     mysql -uroot -proot123456 -P3307 meiyetec
--
-- 已知表结构（DESCRIBE sys_menu_permission）：
--   id, permission_id, route_path, component_path, redirect_path,
--   icon, active_paths, hidden, keep_alive, sort_order, create_time, update_time
-- =============================================

USE meiyetec;

-- =============================================
-- A. 补全 sys_permission（菜单 + 按钮）
-- =============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id) VALUES
-- 储值管理
('wallet',                  '储值管理',        'menu', NULL),
('wallet:list',             '储值流水',        'menu', NULL),
-- 套餐次卡
('package',                 '套餐次卡',        'menu', NULL),
('package:list',            '套餐管理',        'menu', NULL),
-- 基础资料（biz）
('biz',                     '基础资料',        'menu', NULL),
('biz:service-item',        '服务项目',        'menu', NULL),
('biz:treatment-plan',      '疗程方案',        'menu', NULL),
-- 客户运营（crm）
('crm',                     '客户运营',        'menu', NULL),
('crm:care',                '客户运营看板',    'menu', NULL),
('crm:follow-task',         '回访任务',        'menu', NULL),
('crm:follow-plan',         '回访计划',        'menu', NULL),
('crm:complaint',           '投诉工单',        'menu', NULL),
('crm:review',              '客户评价',        'menu', NULL),
('crm:review-tpl',          '评价模板',        'menu', NULL),
('crm:cross-rule',          '跨店规则',        'menu', NULL),
('crm:cross-record',        '跨店核销',        'menu', NULL),
('crm:rfm',                 'RFM 分群',        'menu', NULL),
('crm:ltv',                 'LTV 预测',        'menu', NULL),
-- 组织架构（org）
('org',                     '组织架构',        'menu', NULL),
('org:hq',                  '总部看板',        'menu', NULL),
('org:brand',               '品牌管理',        'menu', NULL),
('org:store',               '门店管理',        'menu', NULL),
-- 医美合规（mh）
('mh',                      '医美合规',        'menu', NULL),
('mh:visual',               '视觉档案',        'menu', NULL),
('mh:consent',              '医美合规',        'menu', NULL),
-- 研发工具（dev）
('dev',                     '研发工具',        'menu', NULL),
('dev:gray-release',        '灰度发布控制台',  'menu', NULL),
('dev:ltv-training',        'LTV流失预测',     'menu', NULL),
('dev:datahub',             '数据中台',        'menu', NULL),
('dev:care-memberday',      '配方护理会员日',  'menu', NULL),
('dev:p1-suite',            'P1套件合集',      'menu', NULL),
('dev:p2-suite',            'P2套件合集',      'menu', NULL),
('dev:p3-suite',            'P3套件合集',      'menu', NULL),
('dev:gap-suite',           '补齐套件',        'menu', NULL),
-- 数据中台（mkt）
('mkt:effect',              '营销效果',        'menu', NULL),
('mkt:channel',             '渠道配置',        'menu', NULL),
('mkt:template',            '消息模板',        'menu', NULL),
('mkt:touch-task',          '触达任务',        'menu', NULL),
('mkt:journey',             '营销旅程',        'menu', NULL),
('mkt:conversion',          '触达效果',        'menu', NULL),
('mkt:pricing',             '动态定价',        'menu', NULL),
-- 数字人民币 / 商户资质
('pay:dcep',                '数字人民币',      'menu', NULL),
('sys:qualification',       '商户资质材料',    'menu', NULL),
('sys:login-log',           '登录日志',        'menu', NULL),
-- 客流预测
('dev:forecast',            '客流预测与智能排班','menu', NULL);

-- A.2 按钮（button）— 让角色绑定有完整覆盖
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id) VALUES
('pos:order:add',           '新增订单',        'button', NULL),
('pos:order:pay',           '订单收款',        'button', NULL),
('pos:order:refund',        '订单退款',        'button', NULL),
('pos:order:void',          '订单作废',        'button', NULL),
('pos:order:list',          '订单查询',        'button', NULL),
('pos:order:stat',          '收银统计',        'button', NULL),
('pos:refund',              '退款管理',        'button', NULL),
('pos:reconciliation:list', '支付对账',        'button', NULL),
('booking:add',             '新增预约',        'button', NULL),
('booking:edit',            '编辑预约',        'button', NULL),
('booking:delete',          '删除预约',        'button', NULL),
('booking:confirm',         '确认预约',        'button', NULL),
('booking:cancel',          '取消预约',        'button', NULL),
('booking:checkin',         '签到',            'button', NULL),
('booking:start',           '开始服务',        'button', NULL),
('booking:complete',        '完成服务',        'button', NULL),
('booking:noshow',          '标记爽约',        'button', NULL),
('booking:schedule:list',   '排班列表',        'button', NULL),
('booking:schedule:add',    '新增排班',        'button', NULL),
('booking:schedule:edit',   '编辑排班',        'button', NULL),
('booking:schedule:delete', '删除排班',        'button', NULL),
('wallet:recharge',         '储值充值',        'button', NULL),
('wallet:consume',          '储值消费',        'button', NULL),
('wallet:refund',           '储值退款',        'button', NULL),
('wallet:promotion',        '充值赠送',        'button', NULL),
('package:add',             '新增套餐',        'button', NULL),
('package:edit',            '编辑套餐',        'button', NULL),
('package:delete',          '删除套餐',        'button', NULL),
('package:purchase',        '购买套餐',        'button', NULL),
('package:redeem',          '核销套餐',        'button', NULL),
('package:freeze',          '冻结套餐',        'button', NULL),
('package:transfer',        '转让套餐',        'button', NULL),
('package:audit',           '套餐审计',        'button', NULL),
('stock:add',               '新增库存',        'button', NULL),
('stock:edit',              '编辑库存',        'button', NULL),
('stock:delete',            '删除库存',        'button', NULL),
('stock:inbound',           '入库',            'button', NULL),
('stock:outbound',          '出库',            'button', NULL),
('stock:check',             '盘点',            'button', NULL),
('stock:alert',             '库存预警',        'button', NULL),
('stock:stat',              '库存统计',        'button', NULL),
('stock:export',            '库存导出',        'button', NULL),
('stock:consumption',       '消耗记录',        'button', NULL),
('stock:record',            '出入库记录',      'button', NULL),
('commission:list',         '提成列表',        'button', NULL),
('commission:rule:list',    '提成规则',        'button', NULL),
('commission:rule:add',     '新增规则',        'button', NULL),
('commission:rule:edit',    '编辑规则',        'button', NULL),
('commission:rule:delete',  '删除规则',        'button', NULL),
('commission:record:list',  '提成记录',        'button', NULL),
('commission:salary:list',  '工资报表',        'button', NULL),
('commission:salary:confirm','工资确认',       'button', NULL),
('commission:salary:pay',   '工资发放',        'button', NULL),
('commission:settlement:list','结算管理',      'button', NULL),
('commission:settlement:audit','结算审核',     'button', NULL),
('commission:batch',        '结算批次',        'button', NULL),
('commission:reversal',     '提成冲正',        'button', NULL),
('report:dashboard',        '数据看板',        'button', NULL),
('report:biz:list',         '营业报表',        'button', NULL),
('report:export:add',       '新增导出',        'button', NULL),
('report:export:download',  '导出下载',        'button', NULL),
('report:export:list',      '导出管理',        'button', NULL),
('report:task',             '导出任务',        'button', NULL),
('report:caliber',          '指标口径',        'button', NULL),
('market:list',             '营销列表',        'button', NULL),
('market:coupon:list',      '优惠券',          'button', NULL),
('market:coupon:add',       '新增优惠券',      'button', NULL),
('market:coupon:edit',      '编辑优惠券',      'button', NULL),
('market:coupon:delete',    '删除优惠券',      'button', NULL),
('market:point:list',       '积分商城',        'button', NULL),
('market:point:add',        '新增积分',        'button', NULL),
('market:point:edit',       '编辑积分',        'button', NULL),
('market:point:delete',     '删除积分',        'button', NULL),
('market:activity:list',    '营销活动',        'button', NULL),
('market:activity:add',     '新增活动',        'button', NULL),
('market:activity:edit',    '编辑活动',        'button', NULL),
('market:activity:delete',  '删除活动',        'button', NULL),
('mkt:promotion',           '促销活动',        'button', NULL),
('mkt:coupon',              '优惠券',          'button', NULL),
('mkt:points',              '积分',            'button', NULL),
('sys:config:list',         '系统配置',        'button', NULL),
('sys:config:edit',         '编辑配置',        'button', NULL),
('sys:tenant:list',         '租户列表',        'button', NULL),
('sys:tenant:add',          '新增租户',        'button', NULL),
('sys:tenant:edit',         '编辑租户',        'button', NULL),
('sys:tenant:delete',       '删除租户',        'button', NULL),
('sys:tenant:freeze',       '冻结租户',        'button', NULL),
('sys:log:list',            '操作日志',        'button', NULL),
('sys:log:export',          '日志导出',        'button', NULL),
('sys:log:clear',           '清空日志',        'button', NULL),
('sys:login-log:list',      '登录日志',        'button', NULL),
('sys:login-log:export',    '登录日志导出',    'button', NULL),
('sys:login-log:clear',     '清空登录日志',    'button', NULL),
('sys:dcep:view',           '数字人民币查看',  'button', NULL),
('sys:dcep:edit',           '数字人民币编辑',  'button', NULL),
('sys:dcep:test',           '数字人民币测试',  'button', NULL),
('employee:list',           '员工列表',        'button', NULL),
('employee:add',            '新增员工',        'button', NULL),
('employee:edit',           '编辑员工',        'button', NULL),
('employee:delete',         '删除员工',        'button', NULL),
('employee:reset-password', '重置密码',        'button', NULL);

-- =============================================
-- B. 补全 sys_menu_permission（菜单路由 + icon + 排序）
--    注意：menu 标题从 sys_permission.permission_name 关联获得，
--          本表只存路由 + 组件 + 图标 + 排序
-- =============================================

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/wallet',              'Wallet',         4 FROM sys_permission WHERE permission_code = 'wallet';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/wallet/recharge',     'Document',       4 FROM sys_permission WHERE permission_code = 'wallet:list';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/package',             'Tickets',        5 FROM sys_permission WHERE permission_code = 'package';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/package/list',        'Document',       5 FROM sys_permission WHERE permission_code = 'package:list';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/biz',                 'Document',       7 FROM sys_permission WHERE permission_code = 'biz';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/biz/service-item',    'Service',        7 FROM sys_permission WHERE permission_code = 'biz:service-item';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/biz/treatment-plan',  'Document',       7 FROM sys_permission WHERE permission_code = 'biz:treatment-plan';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/commission',          'TrendCharts',    8 FROM sys_permission WHERE permission_code = 'commission';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/commission/rules',    'Document',       8 FROM sys_permission WHERE permission_code = 'commission:rule:list';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/commission/records',  'Document',       8 FROM sys_permission WHERE permission_code = 'commission:record:list';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/commission/salary',   'DataAnalysis',   8 FROM sys_permission WHERE permission_code = 'commission:salary:list';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/commission/settlement','Money',          8 FROM sys_permission WHERE permission_code = 'commission:settlement:list';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/crm',                 'Service',       12 FROM sys_permission WHERE permission_code = 'crm';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/crm/care/overview',   'DataBoard',     12 FROM sys_permission WHERE permission_code = 'crm:care';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/crm/care/follow-task','Document',      12 FROM sys_permission WHERE permission_code = 'crm:follow-task';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/crm/care/complaint',  'Warning',       12 FROM sys_permission WHERE permission_code = 'crm:complaint';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/crm/cross-rule',      'Connection',    12 FROM sys_permission WHERE permission_code = 'crm:cross-rule';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/intel/rfm',           'Filter',        12 FROM sys_permission WHERE permission_code = 'crm:rfm';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/org',                 'OfficeBuilding',13 FROM sys_permission WHERE permission_code = 'org';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/org/hq',              'DataBoard',     13 FROM sys_permission WHERE permission_code = 'org:hq';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/org/brand',           'Shop',          13 FROM sys_permission WHERE permission_code = 'org:brand';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/org/store',           'Shop',          13 FROM sys_permission WHERE permission_code = 'org:store';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mh',                  'Medal',         11 FROM sys_permission WHERE permission_code = 'mh';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mh/visual',           'Picture',       11 FROM sys_permission WHERE permission_code = 'mh:visual';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mh/consent',          'Document',      11 FROM sys_permission WHERE permission_code = 'mh:consent';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev',                 'Tools',         99 FROM sys_permission WHERE permission_code = 'dev';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/gray-release',    'Switch',        99 FROM sys_permission WHERE permission_code = 'dev:gray-release';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/ltv-training',    'DataAnalysis',  99 FROM sys_permission WHERE permission_code = 'dev:ltv-training';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/datahub',         'Connection',    99 FROM sys_permission WHERE permission_code = 'dev:datahub';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/care-memberday',  'Present',       99 FROM sys_permission WHERE permission_code = 'dev:care-memberday';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/p1-suite',        'Star',          99 FROM sys_permission WHERE permission_code = 'dev:p1-suite';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/p2-suite',        'Star',          99 FROM sys_permission WHERE permission_code = 'dev:p2-suite';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/p3-suite',        'Star',          99 FROM sys_permission WHERE permission_code = 'dev:p3-suite';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/dev/gap-suite',       'Star',          99 FROM sys_permission WHERE permission_code = 'dev:gap-suite';

INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/effect',          'DataAnalysis',  10 FROM sys_permission WHERE permission_code = 'mkt:effect';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/channel',         'Connection',    10 FROM sys_permission WHERE permission_code = 'mkt:channel';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/template',        'ChatLineRound', 10 FROM sys_permission WHERE permission_code = 'mkt:template';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/touch-task',      'Bell',          10 FROM sys_permission WHERE permission_code = 'mkt:touch-task';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/journey',         'Star',          10 FROM sys_permission WHERE permission_code = 'mkt:journey';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/conversion',      'DataAnalysis',  10 FROM sys_permission WHERE permission_code = 'mkt:conversion';
INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, '/mkt/pricing',         'PriceTag',      10 FROM sys_permission WHERE permission_code = 'mkt:pricing';

-- =============================================
-- C. ADMIN 角色自动获得新加的 menu 类型权限
--    不重复绑定已存在的 menu 类型权限
-- =============================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.permission_type = 'menu'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- =============================================
-- D. 验证
-- =============================================
SELECT '=== sys_permission 总数 ===' AS '';
SELECT permission_type, COUNT(*) AS cnt FROM sys_permission GROUP BY permission_type ORDER BY permission_type;

SELECT '=== sys_menu_permission 总数 ===' AS '';
SELECT COUNT(*) AS menu_count FROM sys_menu_permission;

SELECT '=== ADMIN 实际看到的菜单（按 sort_order）===' AS '';
SELECT p.permission_name, smp.route_path, smp.sort_order
FROM sys_role_permission rp
INNER JOIN sys_role r ON rp.role_id = r.id
INNER JOIN sys_permission p ON rp.permission_id = p.id
INNER JOIN sys_menu_permission smp ON smp.permission_id = p.id
WHERE r.role_code = 'ADMIN' AND p.permission_type = 'menu'
ORDER BY smp.sort_order, p.permission_name;
