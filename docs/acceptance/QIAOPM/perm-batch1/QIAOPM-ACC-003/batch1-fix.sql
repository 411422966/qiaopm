-- QIAOPM 权限体系对齐 批次一：补齐缺失码 + 六角色授权
-- 来源：v3 草案，commit 270e8e3，审计证据 audit/evidence/F-perm-route-db-diff/
-- 执行方式：Get-Content backup\batch1-fix.sql -Raw -Encoding UTF8 | docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
-- 回滚方式：backup/batch1-rollback.sql
-- 执行前必须先用 backup/meiyetec-20261025-recovery.sql 做全库备份
--
-- 内容范围：v3 草案 §1（新建权限码，12 条幂等 INSERT，实际生效 10 条）
--           + v3 草案 §2（六角色绑定，6 段 INSERT）
-- 本批码空间：93 码 = 原 91 码 + schedule:list + schedule:edit（新建 10，复用 2）
-- 幂等性：sys_permission 用 WHERE NOT EXISTS，sys_role_permission 用 ON DUPLICATE KEY UPDATE，可重复执行
-- SUPER_ADMIN 不补绑定（通配 *）
-- 本文件不含 COMMIT / START TRANSACTION，事务边界由执行命令控制
-- 执行前请先跑 backup/batch1-verify.sql 的 §0 前置检查

-- ============================================================
-- §1 新建权限码（12 条幂等 INSERT，实际生效 10 条）
-- ============================================================

-- §1.1 业务/预约域（3 个，DB 缺失）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'biz:booking:no-show', '预约业务-爽约管理', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='biz:booking:no-show');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'biz:booking:resource', '预约业务-预约资源', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='biz:booking:resource');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'biz:booking:waitlist', '预约业务-候补队列', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='biz:booking:waitlist');

-- §1.2 收银（1 个，DB 缺失）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'pos:list', '收银-收银台', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='pos:list');

-- §1.3 库存/耗材（5 个，DB 缺失）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'stock:expiry', '库存-效期追踪', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='stock:expiry');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'stock:list', '库存-列表', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='stock:list');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'stock:material:binding', '库存-耗材绑定', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='stock:material:binding');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'stock:material:stat', '库存-耗材统计', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='stock:material:stat');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'stock:service-material:rule', '库存-项目耗材扣减规则', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='stock:service-material:rule');

-- §1.4 仓储（1 个，DB 缺失）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'wms:transfer', '仓储-跨店调拨', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='wms:transfer');

-- §1.5 排班（2 个：DB 已存在 id=1462/1463，type=button，绑定数 0 → 预期 INSERT 影响 0 行）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'schedule:list', '排班-排班列表', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='schedule:list');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status)
SELECT 'schedule:edit', '排班-排班编辑', 'button', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='schedule:edit');

-- ============================================================
-- §2 六角色绑定
-- ============================================================
-- SUPER_ADMIN 不补（通配 *）。
-- 每段 IN 列表 = 本批新增 + 该角色历史已有且合法的绑定 → §3.1 的 COUNT 与本节注释数严格相等。

-- §2.1 ADMIN —— 全量 93 码（含 package:* 10 码 + schedule 2 码）      期望绑定数 = 93
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='ADMIN' AND r.deleted=0 AND p.deleted=0
  AND p.permission_code IN (
    'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist',
    'booking:add','booking:available','booking:calendar','booking:checkin','booking:complete',
    'booking:delete','booking:detail','booking:edit','booking:export','booking:list',
    'booking:schedule:list','booking:stat','booking:update',
    'material:binding:edit','material:binding:list','material:cost:list','material:deduct:manage',
    'material:package:edit','material:package:list',
    'member:add','member:batch','member:delete','member:edit','member:export','member:freeze',
    'member:import','member:level:edit','member:level:list','member:lifecycle:edit',
    'member:lifecycle:view','member:list','member:message:edit','member:message:list',
    'member:order:list','member:profile','member:stat','member:style-preference:edit',
    'member:style-preference:list','member:tag',
    'package:add','package:delete','package:edit','package:freeze','package:list',
    'package:purchase','package:redeem','package:refund','package:suspend','package:transfer',
    'pos:list','pos:order:add','pos:order:list','pos:order:pay','pos:order:refund','pos:order:stat',
    'pos:order:void','pos:reconciliation:handle','pos:reconciliation:list','pos:reconciliation:run',
    'pos:refund','pos:refund:audit','pos:refund:audit:finance','pos:refund:audit:hq',
    'pos:refund:audit:rule','pos:refund:audit:store','pos:service:list',
    'schedule:list','schedule:edit',
    'stock:alert','stock:check','stock:consumption','stock:expiry','stock:expiry:edit',
    'stock:expiry:view','stock:export','stock:inbound','stock:list','stock:material:binding',
    'stock:material:stat','stock:outbound','stock:product:add','stock:product:delete',
    'stock:product:edit','stock:product:import','stock:product:list','stock:record',
    'stock:replenish','stock:service-material:rule','stock:stat','wms:transfer')
ON DUPLICATE KEY UPDATE id = sys_role_permission.id;

-- §2.2 BOSS —— 会员/套餐/预约只读 + 排班读（20 码）                   期望绑定数 = 20
--      v3 变化：加回 'package:list'（BOSS 是老板，套餐管理为核心业务页面）
--      不含 member:export（Q3 已确认）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='BOSS' AND r.deleted=0 AND p.deleted=0
  AND p.permission_code IN (
    'member:list','member:profile','member:stat','member:order:list','member:level:list',
    'member:message:list','member:tag','member:style-preference:list','member:lifecycle:view',
    'package:list',
    'booking:list','booking:calendar','booking:available','booking:detail',
    'booking:schedule:list','booking:stat',
    'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist',
    'schedule:list')
ON DUPLICATE KEY UPDATE id = sys_role_permission.id;

-- §2.3 MANAGER —— booking/stock/pos/material 全量 + wms + 会员读 5 码 + 排班读写
--                                                            期望绑定数 = 65
--      相对 v1 的变化：−pos:refund:audit:finance −pos:refund:audit:hq −pos:refund:audit:store
--                     +schedule:list +schedule:edit +wms:transfer +member 5 码
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='MANAGER' AND r.deleted=0 AND p.deleted=0
  AND p.permission_code IN (
    'booking:add','booking:available','booking:calendar','booking:checkin','booking:complete',
    'booking:delete','booking:detail','booking:edit','booking:export','booking:list',
    'booking:schedule:list','booking:stat','booking:update',
    'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist',
    'stock:alert','stock:check','stock:consumption','stock:expiry','stock:expiry:edit',
    'stock:expiry:view','stock:export','stock:inbound','stock:list','stock:material:binding',
    'stock:material:stat','stock:outbound','stock:product:add','stock:product:delete',
    'stock:product:edit','stock:product:import','stock:product:list','stock:record',
    'stock:replenish','stock:service-material:rule','stock:stat',
    'pos:list','pos:order:add','pos:order:list','pos:order:pay','pos:order:refund','pos:order:stat',
    'pos:order:void','pos:reconciliation:handle','pos:reconciliation:list','pos:reconciliation:run',
    'pos:refund','pos:refund:audit','pos:refund:audit:rule','pos:service:list',
    'material:binding:edit','material:binding:list','material:cost:list','material:deduct:manage',
    'material:package:edit','material:package:list',
    'wms:transfer','schedule:list','schedule:edit',
    'member:list','member:stat','member:level:list','member:tag','member:order:list')
ON DUPLICATE KEY UPDATE id = sys_role_permission.id;

-- §2.4 CLERK —— 收银/预约只读 + 排班读 + 会员读 5 码（20 码）          期望绑定数 = 20
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='CLERK' AND r.deleted=0 AND p.deleted=0
  AND p.permission_code IN (
    'pos:list','pos:order:list','pos:order:stat','pos:reconciliation:list','pos:service:list',
    'booking:list','booking:calendar','booking:available','booking:detail',
    'booking:schedule:list','booking:stat',
    'biz:booking:no-show','biz:booking:resource','biz:booking:waitlist',
    'schedule:list',
    'member:list','member:stat','member:level:list','member:tag','member:order:list')
ON DUPLICATE KEY UPDATE id = sys_role_permission.id;

-- §2.5 OPERATOR —— 会员域（20 码，本轮无变化）                          期望绑定数 = 20
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='OPERATOR' AND r.deleted=0 AND p.deleted=0
  AND p.permission_code IN (
    'member:add','member:batch','member:delete','member:edit','member:export','member:freeze',
    'member:import','member:level:edit','member:level:list','member:lifecycle:edit',
    'member:lifecycle:view','member:list','member:message:edit','member:message:list',
    'member:order:list','member:profile','member:stat','member:style-preference:edit',
    'member:style-preference:list','member:tag')
ON DUPLICATE KEY UPDATE id = sys_role_permission.id;

-- §2.6 STORE_ADMIN —— 库存全量 + wms + 门店级退款审批 + 2 个历史码      期望绑定数 = 25
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='STORE_ADMIN' AND r.deleted=0 AND p.deleted=0
  AND p.permission_code IN (
    'stock:alert','stock:check','stock:consumption','stock:expiry','stock:expiry:edit',
    'stock:expiry:view','stock:export','stock:inbound','stock:list','stock:material:binding',
    'stock:material:stat','stock:outbound','stock:product:add','stock:product:delete',
    'stock:product:edit','stock:product:import','stock:product:list','stock:record',
    'stock:replenish','stock:service-material:rule','stock:stat',
    'wms:transfer','pos:refund:audit:store',
    'material:binding:edit','pos:refund:audit:rule')
ON DUPLICATE KEY UPDATE id = sys_role_permission.id;
