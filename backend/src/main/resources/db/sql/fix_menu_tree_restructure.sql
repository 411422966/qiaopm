-- =============================================
-- 完整重构 v3：菜单树形结构 + 统一冒号分隔符
--
-- 修复：permission_code 统一使用冒号分隔（:），
--       与 router meta.permission 和 init_rbac_v2.sql 保持一致。
--
-- 菜单级 permission_code：使用冒号分隔，不含动作（如 pos:checkout）
-- 按钮级 permission_code：由 init_rbac_v2.sql 等文件定义
-- =============================================

USE meiyetec;
SET NAMES utf8mb4;

-- =============================================
-- 第 1 步：清理（menu 类型权限码、菜单路由、菜单绑定）
-- =============================================
DELETE FROM sys_menu_permission;
DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_type='menu');
DELETE FROM sys_permission WHERE permission_type='menu';

-- =============================================
-- 第 2 步：重建一级菜单（16 个，单词格式，无分隔符）
-- =============================================

-- 01 首页
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('dashboard', '首页', 'menu', 0, '/dashboard', 'HomeFilled', 1);

-- 02 收银结算
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('pos', '收银结算', 'menu', 0, '/pos/checkout', 'Money', 50);

-- 03 预约管理
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('booking', '预约管理', 'menu', 0, '/booking/calendar', 'Calendar', 40);

-- 04 会员管理
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('member', '会员管理', 'menu', 0, '/member', 'User', 30);

-- 05 储值管理
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('wallet', '储值管理', 'menu', 0, '/wallet/recharge', 'Wallet', 31);

-- 06 套餐次卡
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('package', '套餐次卡', 'menu', 0, '/package', 'Tickets', 32);

-- 07 库存管理
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('inventory', '库存管理', 'menu', 0, '/stock/list', 'Box', 60);

-- 08 基础资料
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('biz', '基础资料', 'menu', 0, '/biz/service-item', 'Document', 70);

-- 09 员工提成
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('commission', '员工提成', 'menu', 0, '/commission/rules', 'TrendCharts', 65);

-- 10 营销工具（合并原 market + mkt）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('marketing', '营销工具', 'menu', 0, '/market/coupon', 'PriceTag', 80);

-- 11 医美合规
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('mh', '医美合规', 'menu', 0, '/mh/visual', 'Medal', 11);

-- 12 客户运营
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('crm', '客户运营', 'menu', 0, '/crm/care/overview', 'Service', 12);

-- 13 组织架构
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('org', '组织架构', 'menu', 0, '/org/store', 'OfficeBuilding', 13);

-- 14 数据报表
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('report', '数据报表', 'menu', 0, '/report/dashboard', 'DataLine', 90);

-- 15 系统设置
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('setting', '系统设置', 'menu', 0, '/sys/setting', 'Setting', 14);

-- 16 研发工具
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('dev', '研发工具', 'menu', 0, '/dev/gray-release', 'Tools', 99);

-- =============================================
-- 第 3 步（补充）：缺失的独立菜单（路由有权限但无父级菜单，按功能就近挂靠）
-- 这些条目不存在于原 16 个一级菜单下，但 router 路由需要对应权限
-- =============================================

-- 考勤管理（归入 employee 一级菜单更合理，此处先独立插入，再追加到 employee 父级）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('employee', '员工管理', 'menu', 0, '/attendance/index', 'User', 20);

-- 技师等级（归入 employee）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('iot', 'IoT 设备', 'menu', 0, '/iot/management', 'Monitor', 15);

-- 区块链溯源
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('blockchain', '区块链', 'menu', 0, '/blockchain/trace', 'Connection', 16);

-- 营销子分类（mkt:promotion, mkt:coupon, mkt:points 已在 marketing 子菜单，但需要独立权限码）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
VALUES ('mkt', '营销中心', 'menu', 0, '/mkt/promotion', 'Promotion', 81);

-- =============================================
-- 第 4 步：重建子菜单（冒号分隔 permission_code）
-- =============================================

-- 02 收银结算 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'pos:checkout', '收银台', 'menu', id, '/pos/checkout', 'Money', 1 FROM sys_permission WHERE permission_code='pos' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'pos:orders', '订单列表', 'menu', id, '/pos/orders', 'Document', 2 FROM sys_permission WHERE permission_code='pos' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'pos:stats', '收银统计', 'menu', id, '/pos/stats', 'DataAnalysis', 3 FROM sys_permission WHERE permission_code='pos' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'pos:refund', '退款单', 'menu', id, '/pos/refund', 'Refresh', 4 FROM sys_permission WHERE permission_code='pos' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'pos:reconciliation', '支付对账', 'menu', id, '/pos/reconciliation', 'CreditCard', 5 FROM sys_permission WHERE permission_code='pos' AND permission_type='menu';

-- 03 预约管理 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'booking:calendar', '预约日历', 'menu', id, '/booking/calendar', 'Calendar', 1 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'booking:list', '预约列表', 'menu', id, '/booking/list', 'Document', 2 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'booking:schedule', '排班管理', 'menu', id, '/booking/schedule', 'Calendar', 3 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'booking:resource', '预约资源', 'menu', id, '/booking/resource', 'Management', 4 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'booking:waitlist', '候补队列', 'menu', id, '/booking/waitlist', 'List', 5 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'booking:no-show', '爽约管理', 'menu', id, '/booking/no-show', 'Warning', 6 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';

-- 05 储值管理 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'wallet:recharge', '储值充值', 'menu', id, '/wallet/recharge', 'Document', 1 FROM sys_permission WHERE permission_code='wallet' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'wallet:transactions', '储值流水', 'menu', id, '/wallet/transactions', 'Document', 2 FROM sys_permission WHERE permission_code='wallet' AND permission_type='menu';

-- 06 套餐次卡 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'package:list', '套餐管理', 'menu', id, '/package/list', 'Document', 1 FROM sys_permission WHERE permission_code='package' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'package:member-orders', '会员套餐查询', 'menu', id, '/package/member-orders', 'Document', 2 FROM sys_permission WHERE permission_code='package' AND permission_type='menu';

-- 07 库存管理 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:list', '库存列表', 'menu', id, '/stock/list', 'Document', 1 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:product', '商品耗材', 'menu', id, '/stock/product', 'Goods', 2 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:record', '出入库记录', 'menu', id, '/stock/record', 'Document', 3 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:consumption', '消耗记录', 'menu', id, '/stock/consumption', 'Document', 4 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:check', '盘点管理', 'menu', id, '/stock/check', 'Document', 5 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:alert', '库存预警', 'menu', id, '/stock/alert', 'Warning', 6 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:stat', '库存统计', 'menu', id, '/stock/stats', 'DataAnalysis', 7 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';

-- 08 基础资料 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'biz:service:item', '服务项目', 'menu', id, '/biz/service-item', 'Service', 1 FROM sys_permission WHERE permission_code='biz' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'biz:treatment:plan', '疗程方案', 'menu', id, '/biz/treatment-plan', 'Document', 2 FROM sys_permission WHERE permission_code='biz' AND permission_type='menu';

-- 09 员工提成 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'commission:rules', '提成规则', 'menu', id, '/commission/rules', 'Document', 1 FROM sys_permission WHERE permission_code='commission' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'commission:records', '提成记录', 'menu', id, '/commission/records', 'Document', 2 FROM sys_permission WHERE permission_code='commission' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'commission:salary', '工资报表', 'menu', id, '/commission/salary', 'DataAnalysis', 3 FROM sys_permission WHERE permission_code='commission' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'commission:settlement', '结算管理', 'menu', id, '/commission/settlement', 'Money', 4 FROM sys_permission WHERE permission_code='commission' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'commission:batch', '提成结算批次', 'menu', id, '/commission/batch', 'Document', 5 FROM sys_permission WHERE permission_code='commission' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'commission:reversal', '提成冲正', 'menu', id, '/commission/reversal', 'Refresh', 6 FROM sys_permission WHERE permission_code='commission' AND permission_type='menu';

-- 10 营销工具 子菜单（合并原 market + mkt，全部挂在 marketing 下）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'market:coupon', '优惠券', 'menu', id, '/market/coupon', 'Present', 1 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'market:points', '积分商城', 'menu', id, '/market/points', 'Medal', 2 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'market:activity', '营销活动', 'menu', id, '/market/activity', 'Promotion', 3 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:effect', '营销效果', 'menu', id, '/mkt/effect', 'DataAnalysis', 4 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:channel', '渠道配置', 'menu', id, '/mkt/channel', 'Connection', 5 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:template', '消息模板', 'menu', id, '/mkt/template', 'ChatLineRound', 6 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:touch-task', '触达任务', 'menu', id, '/mkt/touch-task', 'Bell', 7 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:journey', '营销旅程', 'menu', id, '/mkt/journey', 'Star', 8 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:conversion', '触达效果', 'menu', id, '/mkt/conversion', 'DataAnalysis', 9 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:pricing', '动态定价', 'menu', id, '/mkt/pricing', 'PriceTag', 10 FROM sys_permission WHERE permission_code='marketing' AND permission_type='menu';

-- 11 医美合规 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mh:visual', '视觉档案', 'menu', id, '/mh/visual', 'Picture', 1 FROM sys_permission WHERE permission_code='mh' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mh:consent', '合规记录', 'menu', id, '/mh/consent', 'Document', 2 FROM sys_permission WHERE permission_code='mh' AND permission_type='menu';

-- 12 客户运营 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:care:overview', '客户运营看板', 'menu', id, '/crm/care/overview', 'DataBoard', 1 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:follow:task', '回访任务', 'menu', id, '/crm/care/follow-task', 'Document', 2 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:follow:plan', '回访计划', 'menu', id, '/crm/care/follow-plan', 'Calendar', 3 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:complaint', '投诉工单', 'menu', id, '/crm/care/complaint', 'Warning', 4 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:review', '客户评价', 'menu', id, '/crm/care/review', 'Star', 5 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:review:tpl', '评价模板', 'menu', id, '/crm/care/review-tpl', 'Document', 6 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:cross:rule', '跨店规则', 'menu', id, '/crm/cross-rule', 'Connection', 7 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:cross:record', '跨店核销', 'menu', id, '/crm/cross-record', 'Document', 8 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:rfm', 'RFM 分群', 'menu', id, '/intel/rfm', 'Filter', 9 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:ltv', 'LTV 预测', 'menu', id, '/intel/ltv', 'TrendCharts', 10 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';

-- 13 组织架构 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'org:hq', '总部看板', 'menu', id, '/org/hq', 'DataBoard', 1 FROM sys_permission WHERE permission_code='org' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'org:brand', '品牌管理', 'menu', id, '/org/brand', 'Shop', 2 FROM sys_permission WHERE permission_code='org' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'org:store', '门店管理', 'menu', id, '/org/store', 'Shop', 3 FROM sys_permission WHERE permission_code='org' AND permission_type='menu';

-- 14 数据报表 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'report:dashboard', '数据看板', 'menu', id, '/report/dashboard', 'DataBoard', 1 FROM sys_permission WHERE permission_code='report' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'report:biz', '营业报表', 'menu', id, '/report/biz', 'TrendCharts', 2 FROM sys_permission WHERE permission_code='report' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'report:export', '导出管理', 'menu', id, '/report/export', 'Download', 3 FROM sys_permission WHERE permission_code='report' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'report:task', '导出任务', 'menu', id, '/report/task', 'Document', 4 FROM sys_permission WHERE permission_code='report' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'report:caliber', '指标口径', 'menu', id, '/report/caliber', 'Document', 5 FROM sys_permission WHERE permission_code='report' AND permission_type='menu';

-- 15 系统设置 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:setting', '店铺配置', 'menu', id, '/sys/setting', 'Shop', 1 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:tenant', '租户管理', 'menu', id, '/sys/tenant', 'OfficeBuilding', 2 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:log', '操作日志', 'menu', id, '/sys/log', 'Document', 3 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:login-log', '登录日志', 'menu', id, '/sys/login-log', 'Document', 4 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:payment', '支付配置', 'menu', id, '/sys/payment', 'Money', 5 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:qualification', '商户资质材料', 'menu', id, '/sys/qualification', 'Document', 6 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'sys:dcep', '数字人民币', 'menu', id, '/pay/dcep', 'Money', 7 FROM sys_permission WHERE permission_code='setting' AND permission_type='menu';

-- 16 研发工具 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:gray-release', '灰度发布控制台', 'menu', id, '/dev/gray-release', 'Switch', 1 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:ltv-training', 'LTV流失预测', 'menu', id, '/dev/ltv-training', 'DataAnalysis', 2 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:datahub', '数据中台', 'menu', id, '/dev/datahub', 'Connection', 3 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:care-memberday', '配方护理会员日', 'menu', id, '/dev/care-memberday', 'Present', 4 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:p1-suite', 'P1套件合集', 'menu', id, '/dev/p1-suite', 'Star', 5 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:p2-suite', 'P2套件合集', 'menu', id, '/dev/p2-suite', 'Star', 6 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:p3-suite', 'P3套件合集', 'menu', id, '/dev/p3-suite', 'Star', 7 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:gap-suite', '补齐套件', 'menu', id, '/dev/gap-suite', 'Star', 8 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:forecast', '客流预测与智能排班', 'menu', id, '/dev/forecast', 'TrendCharts', 9 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:pricelist', '电子价目表卡项套餐', 'menu', id, '/dev/pricelist', 'PriceTag', 10 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'dev:care-plan', '护肤方案共享套餐', 'menu', id, '/dev/care-plan', 'Present', 11 FROM sys_permission WHERE permission_code='dev' AND permission_type='menu';

-- 员工管理 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:attendance', '考勤打卡', 'menu', id, '/attendance/index', 'Clock', 1 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:attendance:list', '考勤记录', 'menu', id, '/attendance/records', 'Document', 2 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:leave', '请假管理', 'menu', id, '/attendance/leave', 'Calendar', 3 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:attendance:stat', '考勤统计', 'menu', id, '/attendance/statistics', 'DataAnalysis', 4 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:level', '技师等级', 'menu', id, '/technician/level', 'Medal', 5 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:level:benefit', '等级权益配置', 'menu', id, '/technician/benefit', 'Tickets', 6 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'employee:level:evaluation', '等级评定', 'menu', id, '/technician/evaluation', 'DataAnalysis', 7 FROM sys_permission WHERE permission_code='employee' AND permission_type='menu';

-- IoT 设备 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'iot:device:manage', 'IoT设备联动', 'menu', id, '/iot/management', 'Monitor', 1 FROM sys_permission WHERE permission_code='iot' AND permission_type='menu';

-- 区块链 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'blockchain:trace', '区块链溯源', 'menu', id, '/blockchain/trace', 'Connection', 1 FROM sys_permission WHERE permission_code='blockchain' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'blockchain:monitor', '区块链监控大屏', 'menu', id, '/blockchain/monitor', 'DataLine', 2 FROM sys_permission WHERE permission_code='blockchain' AND permission_type='menu';

-- 营销中心 子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:promotion', '营销活动', 'menu', id, '/mkt/promotion', 'Promotion', 1 FROM sys_permission WHERE permission_code='mkt' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:coupon', '优惠券', 'menu', id, '/mkt/coupon', 'Present', 2 FROM sys_permission WHERE permission_code='mkt' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'mkt:points', '积分规则', 'menu', id, '/mkt/points', 'Medal', 3 FROM sys_permission WHERE permission_code='mkt' AND permission_type='menu';

-- 补充 CRM 缺失子菜单（路由有权限但 menu tree 缺失）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:detection', '肤质档案', 'menu', id, '/crm/detection', 'Picture', 11 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'crm:style:library', '款式偏好', 'menu', id, '/crm/style-package', 'Star', 12 FROM sys_permission WHERE permission_code='crm' AND permission_type='menu';

-- 补充库存管理缺失子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:material:binding', '耗材绑定配置', 'menu', id, '/material/binding', 'Connection', 8 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:material:stat', '耗材消耗统计', 'menu', id, '/material/statistics', 'DataAnalysis', 9 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:expiry', '效期追踪管理', 'menu', id, '/material/expiry', 'Clock', 10 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'stock:service-material:rule', '项目耗材扣减', 'menu', id, '/material/service-material', 'List', 11 FROM sys_permission WHERE permission_code='inventory' AND permission_type='menu';

-- 补充资源预约子菜单
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, icon, sort_order)
SELECT 'resource:schedule', '双资源排程', 'menu', id, '/resource/schedule', 'Calendar', 1 FROM sys_permission WHERE permission_code='booking' AND permission_type='menu';

-- =============================================
-- 第 4 步：绑定路由
-- =============================================
INSERT INTO sys_menu_permission (permission_id, route_path, icon, sort_order)
SELECT id, path, icon, sort_order FROM sys_permission WHERE permission_type='menu';

-- =============================================
-- 第 5 步：绑定 ADMIN / SUPER_ADMIN（拿全部 menu）
-- =============================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('ADMIN','SUPER_ADMIN') AND p.permission_type='menu'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=r.id AND existing.permission_id=p.id);

-- =============================================
-- 第 6 步：验证
-- =============================================

SELECT '=== 菜单统计 ===' AS '';
SELECT
  (SELECT COUNT(*) FROM sys_permission WHERE permission_type='menu' AND (parent_id=0 OR parent_id IS NULL)) AS 一级菜单,
  (SELECT COUNT(*) FROM sys_permission WHERE permission_type='menu' AND parent_id!=0 AND parent_id IS NOT NULL) AS 子菜单,
  (SELECT COUNT(*) FROM sys_permission WHERE permission_type='menu') AS menu总数;

SELECT '=== ADMIN 一级菜单 ===' AS '';
SELECT permission_code, permission_name, sort_order FROM sys_permission
WHERE permission_type='menu' AND (parent_id=0 OR parent_id IS NULL)
ORDER BY sort_order;

SELECT '=== ADMIN 完整树 ===' AS '';
SELECT
  parent.permission_name AS 一级,
  child.permission_name AS 子菜单,
  child.permission_code AS 权限码,
  child.path AS 路由,
  child.sort_order AS 子排序
FROM sys_permission child
INNER JOIN sys_permission parent ON child.parent_id = parent.id
INNER JOIN sys_role_permission rp ON rp.permission_id = child.id
INNER JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_code='ADMIN' AND child.permission_type='menu'
ORDER BY parent.sort_order, child.sort_order;
