-- =============================================
-- 美业 SaaS 系统 - R2.0-A 权限底座迁移脚本
-- 兼容：MySQL 5.7+/8.0（utf8mb4）
-- 依赖：M01 登录认证模块已初始化
-- 实施者：T-A04 / T-A05
-- 版本：v1.0 | 2026-08-08
-- =============================================

USE meiyetec;

-- =============================================
-- 1. 角色数据范围（sys_role_data_scope）
--    描述：定义每个角色的数据可见范围，
--         ALL=租户内全部 / BRAND=品牌 /
--         STORE=门店 / SELF=本人
-- =============================================
DROP TABLE IF EXISTS sys_role_data_scope;
CREATE TABLE sys_role_data_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    data_scope VARCHAR(16) NOT NULL COMMENT '数据范围：ALL/BRAND/STORE/SELF',
    data_scope_value VARCHAR(512) DEFAULT NULL COMMENT '范围值（逗号分隔的门店/品牌ID；ALL 不填）',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_scope (role_id, data_scope),
    INDEX idx_role_id (role_id),
    INDEX idx_data_scope (data_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色数据范围表';

-- 初始化默认角色的数据范围
-- 角色 1：SUPER_ADMIN -> ALL
-- 角色 2：STORE_ADMIN -> STORE
-- 角色 3：EMPLOYEE -> STORE
INSERT INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'ALL', NULL, '租户内全部数据' FROM sys_role r WHERE r.role_code = 'SUPER_ADMIN';
INSERT INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '仅当前门店数据' FROM sys_role r WHERE r.role_code = 'STORE_ADMIN';
INSERT INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '仅当前门店数据' FROM sys_role r WHERE r.role_code = 'EMPLOYEE';

-- =============================================
-- 2. 菜单权限码扩展表（sys_menu_permission）
--    描述：与 sys_permission 联动，
--         额外记录菜单路由、图标、组件、激活路径等 UI 字段
-- =============================================
DROP TABLE IF EXISTS sys_menu_permission;
CREATE TABLE sys_menu_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    permission_id BIGINT NOT NULL COMMENT 'sys_permission.id',
    route_path VARCHAR(255) NOT NULL COMMENT '前端路由路径',
    component_path VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径(动态 import)',
    redirect_path VARCHAR(255) DEFAULT NULL COMMENT '重定向路径',
    icon VARCHAR(64) DEFAULT NULL COMMENT '图标名称',
    active_paths VARCHAR(512) DEFAULT NULL COMMENT '激活路径集合，逗号分隔，用于高亮',
    hidden TINYINT DEFAULT 0 COMMENT '是否隐藏：0 否 1 是',
    keep_alive TINYINT DEFAULT 1 COMMENT '是否缓存页面：0 否 1 是',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_permission_id (permission_id),
    INDEX idx_route_path (route_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单 UI 扩展表';

-- =============================================
-- 3. 角色路由动态渲染中间表（sys_role_menu）
--    描述：与 sys_role_permission 区分开，仅用于路由渲染，
--         按钮权限仍走 sys_role_permission。
--    原因：菜单权限需要「可见」，按钮权限需要「可执行」，
--         下放给门店管理员时常见「菜单可见但某些按钮不可点」。
-- =============================================
-- 复用 sys_role_permission 即可，本表不再新增（避免冗余）。

-- =============================================
-- 4. 权限码扩展（按钮 / 接口）
--    全部使用 INSERT IGNORE：已存在则跳过，幂等可重入
-- =============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
VALUES
-- 二级菜单（路由用）
('member:list',   '会员列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 1),
('wallet:list',   '储值查询',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dashboard') AS t), 0),
('package:list',  '套餐列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 1),
('booking:list',  '预约列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 1),
('pos:list',      '订单列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 1),
('stock:list',    '库存列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 1),
('commission:list','提成列表','menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 0),
('market:list',   '营销列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 1),
('report:list',   '报表列表',  'menu', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report') AS t), 1),

-- 会员管理 按钮
('member:stat',     '会员统计',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 50),
('member:tag',      '批量打标签','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 60),
('member:export',   '导出会员',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 70),
('member:import',   '导入会员',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 80),
('member:level:list', '会员等级-查询', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 90),
('member:level:add',  '会员等级-新增', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 91),
('member:level:edit', '会员等级-编辑', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 92),
('member:level:delete','会员等级-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 93),
('member:tag:list',   '会员标签-查询','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 100),
('member:tag:add',    '会员标签-新增','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 101),
('member:tag:edit',   '会员标签-编辑','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 102),
('member:tag:delete', '会员标签-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 103),

-- 储值管理 按钮
('wallet:recharge', '储值充值',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 0),
('wallet:consume',  '储值扣款',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 0),
('wallet:refund',   '储值退款',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 0),
('wallet:promotion','充值活动',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 0),

-- 套餐次卡 按钮
('package:add',    '新增套餐',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 10),
('package:edit',   '编辑套餐',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 11),
('package:delete', '删除套餐',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 12),
('package:purchase','套餐购买', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 20),
('package:redeem', '套餐核销',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 21),
('package:freeze', '套餐冻结',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 22),
('package:transfer','套餐转让','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 23),
('package:audit',  '套餐审计',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='package') AS t), 24),

-- 预约管理 按钮
('booking:add',    '新增预约',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 10),
('booking:edit',   '编辑预约',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 11),
('booking:delete', '删除预约',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 12),
('booking:confirm','确认预约',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 13),
('booking:cancel', '取消预约',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 14),
('booking:checkin','签到',      'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 15),
('booking:start',  '开始服务',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 16),
('booking:complete','完成服务', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 17),
('booking:noshow', '标记爽约',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 18),
('booking:schedule:list', '排班-查询','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 50),
('booking:schedule:add',  '排班-新增','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 51),
('booking:schedule:edit', '排班-编辑','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 52),
('booking:schedule:delete','排班-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 53),

-- 收银结算 按钮
('pos:order:add',   '新建订单',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 10),
('pos:order:pay',   '订单收款',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 11),
('pos:order:refund','订单退款',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 12),
('pos:order:void',  '订单作废',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 13),
('pos:order:list',  '订单查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 14),
('pos:order:stat',  '收银统计',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos') AS t), 15),

-- 库存管理 按钮
('stock:add',      '新增商品',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 10),
('stock:edit',     '编辑商品',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 11),
('stock:delete',   '删除商品',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 12),
('stock:inbound',  '入库',      'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 20),
('stock:outbound', '出库',      'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 21),
('stock:check',    '盘点',      'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 22),
('stock:alert',    '库存预警',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 23),
('stock:stat',     '库存统计',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 24),
('stock:export',   '库存导出',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 25),

-- 员工提成 按钮
('commission:rule:list',  '提成规则-查询','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 60),
('commission:rule:add',   '提成规则-新增','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 61),
('commission:rule:edit',  '提成规则-编辑','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 62),
('commission:rule:delete','提成规则-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 63),
('commission:record:list','提成记录',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 70),
('commission:salary:list','工资查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 80),
('commission:salary:confirm','工资确认','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 81),
('commission:salary:pay', '工资发放',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 82),
('commission:settlement:list','结算查询','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 90),
('commission:settlement:audit','结算审核','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 91),

-- 营销工具 按钮
('market:coupon:list', '优惠券-查询','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 10),
('market:coupon:add',  '优惠券-新增','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 11),
('market:coupon:edit', '优惠券-编辑','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 12),
('market:coupon:delete','优惠券-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 13),
('market:point:list',  '积分-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 20),
('market:point:add',   '积分-新增',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 21),
('market:point:edit',  '积分-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 22),
('market:point:delete','积分-删除',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 23),
('market:activity:list','活动-查询', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 30),
('market:activity:add', '活动-新增', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 31),
('market:activity:edit','活动-编辑', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 32),
('market:activity:delete','活动-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='marketing') AS t), 33),

-- 报表 按钮
('report:dashboard', '工作台看板','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report') AS t), 1),
('report:biz:list',  '营业报表',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report') AS t), 2),
('report:export:add','新建导出',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report') AS t), 10),
('report:export:download','下载导出','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report') AS t), 11),
('report:export:list','导出管理',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report') AS t), 12),

-- 系统设置 按钮
('sys:config:list', '配置-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 10),
('sys:config:edit', '配置-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 11),
('sys:tenant:list', '租户-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 20),
('sys:tenant:add',  '租户-新增',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 21),
('sys:tenant:edit', '租户-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 22),
('sys:tenant:delete','租户-删除','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 23),
('sys:tenant:freeze','租户-冻结','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 24),
('sys:log:list',    '日志-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 30),
('sys:log:export',  '日志-导出',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 31),
('sys:log:clear',   '日志-清理',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 32),
('sys:login-log:list','登录日志-查询','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 40),
('sys:login-log:export','登录日志-导出','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 41),
('sys:login-log:clear','登录日志-清理','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 42),
('sys:dcep:view',   '数字人民币-查看','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 50),
('sys:dcep:edit',   '数字人民币-编辑','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 51),
('sys:dcep:test',   '数字人民币-测试','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 52),
('sys:dcep:qualification','数字人民币-资质','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 53),

-- 员工 / 门店 等基础数据
('employee:list',   '员工-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 10),
('employee:add',    '员工-新增',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 11),
('employee:edit',   '员工-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 12),
('employee:delete', '员工-删除',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 13),
('employee:reset-password','员工-重置密码','button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='employee') AS t), 14),

('store:list',      '门店-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 60),
('store:add',       '门店-新增',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 61),
('store:edit',      '门店-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 62),
('store:delete',    '门店-删除',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 63),
('store:stat',      '门店-统计',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') AS t), 64),

('room:list',       '房间-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 60),
('room:add',        '房间-新增',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 61),
('room:edit',       '房间-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 62),
('room:delete',     '房间-删除',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='booking') AS t), 63),

('category:list',   '分类-查询',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 40),
('category:add',    '分类-新增',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 41),
('category:edit',   '分类-编辑',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 42),
('category:delete', '分类-删除',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 43),
('category:sort',   '分类-排序',  'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='inventory') AS t), 44),

('auth:login',      '登录',      'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='dashboard') AS t), 0);

-- =============================================
-- 5. 新增默认角色：FINANCE / PLATFORM_ADMIN
-- =============================================
INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES
('FINANCE',        '财务',          '查看报表、订单、提成相关数据'),
('PLATFORM_ADMIN', '平台管理员',    '平台级账号，跨租户运维');

-- 财务：数据范围 STORE
INSERT INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'STORE', NULL, '财务仅门店数据' FROM sys_role r WHERE r.role_code = 'FINANCE';
-- 平台管理员：数据范围 ALL
INSERT INTO sys_role_data_scope (role_id, data_scope, data_scope_value, remark)
SELECT r.id, 'ALL', NULL, '平台跨租户数据' FROM sys_role r WHERE r.role_code = 'PLATFORM_ADMIN';

-- 财务：授予报告/订单/退款/提成相关权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'FINANCE'
  AND p.permission_code IN (
    'dashboard','report:dashboard','report:biz:list','report:export:add','report:export:download','report:export:list',
    'pos:order:list','pos:order:refund','pos:order:stat',
    'wallet:list','wallet:refund',
    'commission:record:list','commission:salary:list','commission:settlement:list',
    'sys:login-log:list'
  );

-- 平台管理员：授予全部权限（与 SUPER_ADMIN 同源）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'PLATFORM_ADMIN';

-- =============================================
-- 6. 现有 SUPER_ADMIN 补齐本店超级管理员
--    （不替换原有授权，仅 INSERT IGNORE 补齐缺失）
-- =============================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- =============================================
-- 7. 验证
-- =============================================
SELECT '=== sys_role_data_scope ===' AS '';
SELECT sr.role_code, srd.data_scope, srd.data_scope_value
FROM sys_role sr
LEFT JOIN sys_role_data_scope srd ON srd.role_id = sr.id
ORDER BY sr.id;

SELECT '=== 新增权限码数量 ===' AS '';
SELECT COUNT(*) AS new_perm_count FROM sys_permission
WHERE permission_code IN (
  'member:stat','member:tag','member:export','member:import',
  'wallet:recharge','wallet:consume','wallet:refund','wallet:promotion',
  'package:add','package:edit','package:delete','package:purchase','package:redeem','package:freeze','package:transfer','package:audit',
  'booking:add','booking:edit','booking:delete','booking:confirm','booking:cancel','booking:checkin','booking:start','booking:complete','booking:noshow',
  'pos:order:add','pos:order:pay','pos:order:refund','pos:order:void','pos:order:list','pos:order:stat',
  'stock:add','stock:edit','stock:delete','stock:inbound','stock:outbound','stock:check','stock:alert','stock:stat','stock:export',
  'commission:rule:list','commission:rule:add','commission:rule:edit','commission:rule:delete','commission:record:list','commission:salary:list','commission:salary:confirm','commission:salary:pay','commission:settlement:list','commission:settlement:audit',
  'market:coupon:list','market:coupon:add','market:coupon:edit','market:coupon:delete','market:point:list','market:point:add','market:point:edit','market:point:delete','market:activity:list','market:activity:add','market:activity:edit','market:activity:delete',
  'report:dashboard','report:biz:list','report:export:add','report:export:download','report:export:list',
  'sys:config:list','sys:config:edit','sys:tenant:list','sys:tenant:add','sys:tenant:edit','sys:tenant:delete','sys:tenant:freeze','sys:log:list','sys:log:export','sys:log:clear','sys:login-log:list','sys:login-log:export','sys:login-log:clear','sys:dcep:view','sys:dcep:edit','sys:dcep:test','sys:dcep:qualification',
  'employee:list','employee:add','employee:edit','employee:delete','employee:reset-password',
  'store:list','store:add','store:edit','store:delete','store:stat',
  'room:list','room:add','room:edit','room:delete',
  'category:list','category:add','category:edit','category:delete','category:sort',
  'auth:login'
);

SELECT '=== 角色-权限码数量 ===' AS '';
SELECT sr.role_code, COUNT(rp.permission_id) AS perm_count
FROM sys_role sr
LEFT JOIN sys_role_permission rp ON rp.role_id = sr.id
GROUP BY sr.role_code
ORDER BY sr.id;
