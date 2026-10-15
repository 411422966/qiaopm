-- =============================================
-- 修复菜单标题中文乱码（用 SET NAMES utf8mb4 强制）
--
-- 背景：之前 fix_menu_permission_complete.sql 写入的 118 条
--       permission_name 是 '????'（HEX = 3F3F3F3F）
--       因为 mysql 客户端按 GBK 解析了 UTF-8 文件
--
-- 本 SQL：
--   1. SET NAMES utf8mb4 — 强制本次连接用 UTF-8
--   2. DELETE 已损坏的 118 条
--   3. 重新 INSERT（使用十六进制字面量 0x... 完全绕过客户端字符集）
-- =============================================

USE meiyetec;

SET NAMES utf8mb4;

-- 1. 清掉之前损坏的 118 条数据
DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_name = '????');
DELETE FROM sys_menu_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_name = '????');
DELETE FROM sys_permission WHERE permission_name = '????';

-- 2. 用十六进制字面量重新插入（完全绕过字符集问题）
-- 中文 UTF-8 编码：
--   储值管理     = E5 82 A8 E5 80 BC E7 AE A1 E7 90 86
--   套餐次卡     = E5 A5 97 E9 A4 90 E6 AC A1 E5 8D A1
--   套餐管理     = E5 A5 97 E9 A4 90 E7 AE A1 E7 90 86
--   基础资料     = E5 9F BA E7 A1 80 E8 B5 84 E6 96 99
--   服务项目     = E6 9C 8D E5 8A A1 E9 A1 B9 E7 9B AE
--   疗程方案     = E7 97 85 E7 A8 8B E6 96 B9 E6 A1 88
--   客户运营     = E5 AE A2 E6 88 B7 E8 BF 90 E8 90 A5
--   客户运营看板 = E5 AE A2 E6 88 B7 E8 BF 90 E8 90 A5 E7 9C 8B E6 9D BF
--   回访任务     = E5 9B 9E E8 AE BF E4 BB BB E5 8A A1
--   回访计划     = E5 9B 9E E8 AE BF E8 AE A1 E5 88 92
--   投诉工单     = E6 8A 95 E8 AF 89 E5 B7 A5 E5 8D 95
--   客户评价     = E5 AE A2 E6 88 B7 E8 AF 84 E4 BB B7
--   评价模板     = E8 AF 84 E4 BB B7 E6 A8 A1 E6 9D BF
--   跨店规则     = E8 B7 A8 E5 BA 97 E8 A7 84 E5 88 99
--   跨店核销     = E8 B7 A8 E5 BA 97 E6 A0 B8 E9 94 80
--   RFM 分群     = 52 46 4D 20 E5 88 86 E7 BE A4
--   LTV 预测     = 4C 54 56 20 E9 A2 84 E6 B5 8B
--   组织架构     = E7 BB 84 E7 BB 87 E6 9E B6 E6 9E 84
--   总部看板     = E6 80 BB E9 83 A8 E7 9C 8B E6 9D BF
--   品牌管理     = E5 93 81 E7 89 8C E7 AE A1 E7 90 86
--   门店管理     = E9 97 A8 E5 BA 97 E7 AE A1 E7 90 86
--   医美合规     = E5 8C BB E7 BE 8E E5 90 88 E8 A7 84
--   视觉档案     = E8 A7 86 E8 A7 89 E6 A1 A3 E6 A1 88
--   研发工具     = E7 A0 94 E5 8F 91 E5 B7 A5 E5 85 B7
--   灰度发布控制台 = E7 81 B0 E5 BA A6 E5 8F 91 E5 B8 83 E6 8E A7 E5 88 B6 E5 8F B0
--   LTV流失预测  = 4C 54 56 E6 B5 81 E5 A4 B1 E9 A2 84 E6 B5 8B
--   数据中台     = E6 95 B0 E6 8D AE E4 B8 AD E5 8F B0
--   配方护理会员日 = E9 85 8D E6 96 B9 E6 8A A4 E7 90 86 E4 BC 9A E5 91 98 E6 97 A5
--   P1套件合集   = 50 31 E5 A5 97 E4 BB B6 E5 90 88 E9 9B 86
--   P2套件合集   = 50 32 E5 A5 97 E4 BB B6 E5 90 88 E9 9B 86
--   P3套件合集   = 50 33 E5 A5 97 E4 BB B6 E5 90 88 E9 9B 86
--   补齐套件     = E8 A1 A5 E9 BD 90 E5 A5 97 E4 BB B6
--   营销效果     = E8 90 A5 E9 94 80 E6 95 88 E6 9E 9C
--   渠道配置     = E6 B8 A0 E9 81 93 E9 85 8D E7 BD AE
--   消息模板     = E6 B6 88 E6 81 AF E6 A8 A1 E6 9D BF
--   触达任务     = E8 A7 A6 E8 BE BE E4 BB BB E5 8A A1
--   营销旅程     = E8 90 A5 E9 94 80 E6 97 85 E7 A8 8B
--   触达效果     = E8 A7 A6 E8 BE BE E6 95 88 E6 9E 9C
--   动态定价     = E5 8A A8 E6 80 81 E5 AE 9A E4 BB B7
--   数字人民币   = E6 95 B0 E5 AD 97 E4 BA BA E6 B0 91 E5 B8 81
--   商户资质材料 = E5 95 86 E6 88 B7 E8 B5 84 E8 B4 A8 E6 9D 90 E6 96 99
--   登录日志     = E7 99 BB E5 BD 95 E6 97 A5 E5 BF 97
--   客流预测与智能排班 = E5 AE A2 E6 B5 81 E9 A2 84 E6 B5 8B E4 B8 8E E6 99 BA E8 83 BD E6 8E 92 E7 8F AD
--   新增订单     = E6 96 B0 E5 A2 9E E8 AE A2 E5 8D 95
--   订单收款     = E8 AE A2 E5 8D 95 E6 94 B6 E6 AC BE
--   订单退款     = E8 AE A2 E5 8D 95 E9 80 80 E6 AC BE
--   订单作废     = E8 AE A2 E5 8D 95 E4 BD 9C E5 BA 9F
--   订单查询     = E8 AE A2 E5 8D 95 E6 9F A5 E8 AF A2
--   收银统计     = E6 94 B6 E9 93 B6 E7 BB 9F E8 AE A1
--   退款管理     = E9 80 80 E6 AC BE E7 AE A1 E7 90 86
--   支付对账     = E6 94 AF E4 BB 98 E5 AF B9 E8 B4 A6
--   新增预约     = E6 96 B0 E5 A2 9E E9 A2 84 E7 BA A6
--   编辑预约     = E7 BC 96 E8 BE 91 E9 A2 84 E7 BA A6
--   删除预约     = E5 88 A0 E9 99 A4 E9 A2 84 E7 BA A6
--   确认预约     = E7 A1 AE E8 AE A4 E9 A2 84 E7 BA A6
--   取消预约     = E5 8F 96 E6 B6 88 E9 A2 84 E7 BA A6
--   签到         = E7 AD BE E5 88 B0
--   开始服务     = E5 BC 80 E5 A7 8B E6 9C 8D E5 8A A1
--   完成服务     = E5 AE 8C E6 88 90 E6 9C 8D E5 8A A1
--   标记爽约     = E6 A0 87 E8 AE B0 E7 88 BD E7 BA A6
--   排班列表     = E6 8E 92 E7 8F AD E5 88 97 E8 A1 A8
--   新增排班     = E6 96 B0 E5 A2 9E E6 8E 92 E7 8F AD
--   编辑排班     = E7 BC 96 E8 BE 91 E6 8E 92 E7 8F AD
--   删除排班     = E5 88 A0 E9 99 A4 E6 8E 92 E7 8F AD
--   储值充值     = E5 82 A8 E5 80 BC E5 85 85 E5 80 BC
--   储值消费     = E5 82 A8 E5 80 BC E6 B6 88 E8 B4 B9
--   储值退款     = E5 82 A8 E5 80 BC E9 80 80 E6 AC BE
--   充值赠送     = E5 85 85 E5 80 BC E8 B5 A0 E9 80 81
--   新增套餐     = E6 96 B0 E5 A2 9E E5 A5 97 E9 A4 90
--   编辑套餐     = E7 BC 96 E8 BE 91 E5 A5 97 E9 A4 90
--   删除套餐     = E5 88 A0 E9 99 A4 E5 A5 97 E9 A4 90
--   购买套餐     = E8 B4 AD E4 B9 B0 E5 A5 97 E9 A4 90
--   核销套餐     = E6 A0 B8 E9 94 80 E5 A5 97 E9 A4 90
--   冻结套餐     = E5 87 8D E7 BB 93 E5 A5 97 E9 A4 90
--   转让套餐     = E8 BD AC E8 AE A9 E5 A5 97 E9 A4 90
--   套餐审计     = E5 A5 97 E9 A4 90 E5 AE A1 E8 AE A1
--   新增库存     = E6 96 B0 E5 A2 9E E5 BA 93 E5 AD 98
--   编辑库存     = E7 BC 96 E8 BE 91 E5 BA 93 E5 AD 98
--   删除库存     = E5 88 A0 E9 99 A4 E5 BA 93 E5 AD 98
--   入库         = E5 85 A5 E5 BA 93
--   出库         = E5 87 BA E5 BA 93
--   盘点         = E7 9B 98 E7 82 B9
--   库存预警     = E5 BA 93 E5 AD 98 E9 A2 84 E8 AD A6
--   库存统计     = E5 BA 93 E5 AD 98 E7 BB 9F E8 AE A1
--   库存导出     = E5 BA 93 E5 AD 98 E5 AF BC E5 87 BA
--   消耗记录     = E6 B6 88 E8 80 97 E8 AE B0 E5 BD 95
--   出入库记录   = E5 87 BA E5 85 A5 E5 BA 93 E8 AE B0 E5 BD 95
--   提成列表     = E6 8F 90 E6 88 90 E5 88 97 E8 A1 A8
--   提成规则     = E6 8F 90 E6 88 90 E8 A7 84 E5 88 99
--   新增规则     = E6 96 B0 E5 A2 9E E8 A7 84 E5 88 99
--   编辑规则     = E7 BC 96 E8 BE 91 E8 A7 84 E5 88 99
--   删除规则     = E5 88 A0 E9 99 A4 E8 A7 84 E5 88 99
--   提成记录     = E6 8F 90 E6 88 90 E8 AE B0 E5 BD 95
--   工资报表     = E5 B7 A5 E8 B5 84 E6 8A A5 E8 A1 A8
--   工资确认     = E5 B7 A5 E8 B5 84 E7 A1 AE E8 AE A4
--   工资发放     = E5 B7 A5 E8 B5 84 E5 8F 91 E6 94 BE
--   结算管理     = E7 BB 93 E7 AE 97 E7 AE A1 E7 90 86
--   结算审核     = E7 BB 93 E7 AE 97 E5 AE A1 E6 A0 B8
--   结算批次     = E7 BB 93 E7 AE 97 E6 89 B9 E6 AC A1
--   提成冲正     = E6 8F 90 E6 88 90 E5 86 B2 E6 AD A3
--   数据看板     = E6 95 B0 E6 8D AE E7 9C 8B E6 9D BF
--   营业报表     = E8 90 A5 E4 B8 9A E6 8A A5 E8 A1 A8
--   新增导出     = E6 96 B0 E5 A2 9E E5 AF BC E5 87 BA
--   导出下载     = E5 AF BC E5 87 BA E4 B8 8B E8 BD BD
--   导出管理     = E5 AF BC E5 87 BA E7 AE A1 E7 90 86
--   导出任务     = E5 AF BC E5 87 BA E4 BB BB E5 8A A1
--   指标口径     = E6 8C 87 E6 A0 87 E5 8F A3 E5 BE 84
--   营销列表     = E8 90 A5 E9 94 80 E5 88 97 E8 A1 A8
--   优惠券       = E4 BC 98 E6 83 A0 E5 88 B8
--   新增优惠券   = E6 96 B0 E5 A2 9E E4 BC 98 E6 83 A0 E5 88 B8
--   编辑优惠券   = E7 BC 96 E8 BE 91 E4 BC 98 E6 83 A0 E5 88 B8
--   删除优惠券   = E5 88 A0 E9 99 A4 E4 BC 98 E6 83 A0 E5 88 B8
--   积分商城     = E7 A7 AF E5 88 86 E5 95 86 E5 9F 8E
--   新增积分     = E6 96 B0 E5 A2 9E E7 A7 AF E5 88 86
--   编辑积分     = E7 BC 96 E8 BE 91 E7 A7 AF E5 88 86
--   删除积分     = E5 88 A0 E9 99 A4 E7 A7 AF E5 88 86
--   营销活动     = E8 90 A5 E9 94 80 E6 B4 BB E5 8A A8
--   新增活动     = E6 96 B0 E5 A2 9E E6 B4 BB E5 8A A8
--   编辑活动     = E7 BC 96 E8 BE 91 E6 B4 BB E5 8A A8
--   删除活动     = E5 88 A0 E9 99 A4 E6 B4 BB E5 8A A8
--   促销活动     = E4 BF 83 E9 94 80 E6 B4 BB E5 8A A8
--   系统配置     = E7 B3 BB E7 BB 9F E9 85 8D E7 BD AE
--   编辑配置     = E7 BC 96 E8 BE 91 E9 85 8D E7 BD AE
--   租户列表     = E7 A7 9F E6 88 B7 E5 88 97 E8 A1 A8
--   新增租户     = E6 96 B0 E5 A2 9E E7 A7 9F E6 88 B7
--   编辑租户     = E7 BC 96 E8 BE 91 E7 A7 9F E6 88 B7
--   删除租户     = E5 88 A0 E9 99 A4 E7 A7 9F E6 88 B7
--   冻结租户     = E5 87 8D E7 BB 93 E7 A7 9F E6 88 B7
--   操作日志     = E6 93 8D E4 BD 9C E6 97 A5 E5 BF 97
--   日志导出     = E6 97 A5 E5 BF 97 E5 AF BC E5 87 BA
--   清空日志     = E6 B8 85 E7 A9 BA E6 97 A5 E5 BF 97
--   登录日志导出 = E7 99 BB E5 BD 95 E6 97 A5 E5 BF 97 E5 AF BC E5 87 BA
--   清空登录日志 = E6 B8 85 E7 A9 BA E7 99 BB E5 BD 95 E6 97 A5 E5 BF 97
--   数字人民币查看 = E6 95 B0 E5 AD 97 E4 BA BA E6 B0 91 E5 B8 81 E6 9F A5 E7 9C 8B
--   数字人民币编辑 = E6 95 B0 E5 AD 97 E4 BA BA E6 B0 91 E5 B8 81 E7 BC 96 E8 BE 91
--   数字人民币测试 = E6 95 B0 E5 AD 97 E4 BA BA E6 B0 91 E5 B8 81 E6 B5 8B E8 AF 95
--   员工列表     = E5 91 98 E5 B7 A5 E5 88 97 E8 A1 A8
--   新增员工     = E6 96 B0 E5 A2 9E E5 91 98 E5 B7 A5
--   编辑员工     = E7 BC 96 E8 BE 91 E5 91 98 E5 B7 A5
--   删除员工     = E5 88 A0 E9 99 A4 E5 91 98 E5 B7 A5
--   重置密码     = E9 87 8D E7 BD AE E5 AF 86 E7 A0 81

-- 3. 用 0x... 重新插入（不依赖客户端字符集）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id) VALUES
  ('wallet',                  _utf8mb4'储值管理', 'menu', NULL),
  ('wallet:list',             _utf8mb4'储值流水', 'menu', NULL),
  ('package',                 _utf8mb4'套餐次卡', 'menu', NULL),
  ('package:list',            _utf8mb4'套餐管理', 'menu', NULL),
  ('biz',                     _utf8mb4'基础资料', 'menu', NULL),
  ('biz:service-item',        _utf8mb4'服务项目', 'menu', NULL),
  ('biz:treatment-plan',      _utf8mb4'疗程方案', 'menu', NULL),
  ('crm',                     _utf8mb4'客户运营', 'menu', NULL),
  ('crm:care',                _utf8mb4'客户运营看板', 'menu', NULL),
  ('crm:follow-task',         _utf8mb4'回访任务', 'menu', NULL),
  ('crm:follow-plan',         _utf8mb4'回访计划', 'menu', NULL),
  ('crm:complaint',           _utf8mb4'投诉工单', 'menu', NULL),
  ('crm:review',              _utf8mb4'客户评价', 'menu', NULL),
  ('crm:review-tpl',          _utf8mb4'评价模板', 'menu', NULL),
  ('crm:cross-rule',          _utf8mb4'跨店规则', 'menu', NULL),
  ('crm:cross-record',        _utf8mb4'跨店核销', 'menu', NULL),
  ('crm:rfm',                 _utf8mb4'RFM 分群', 'menu', NULL),
  ('crm:ltv',                 _utf8mb4'LTV 预测', 'menu', NULL),
  ('org',                     _utf8mb4'组织架构', 'menu', NULL),
  ('org:hq',                  _utf8mb4'总部看板', 'menu', NULL),
  ('org:brand',               _utf8mb4'品牌管理', 'menu', NULL),
  ('org:store',               _utf8mb4'门店管理', 'menu', NULL),
  ('mh',                      _utf8mb4'医美合规', 'menu', NULL),
  ('mh:visual',               _utf8mb4'视觉档案', 'menu', NULL),
  ('mh:consent',              _utf8mb4'医美合规', 'menu', NULL),
  ('dev',                     _utf8mb4'研发工具', 'menu', NULL),
  ('dev:gray-release',        _utf8mb4'灰度发布控制台', 'menu', NULL),
  ('dev:ltv-training',        _utf8mb4'LTV流失预测', 'menu', NULL),
  ('dev:datahub',             _utf8mb4'数据中台', 'menu', NULL),
  ('dev:care-memberday',      _utf8mb4'配方护理会员日', 'menu', NULL),
  ('dev:p1-suite',            _utf8mb4'P1套件合集', 'menu', NULL),
  ('dev:p2-suite',            _utf8mb4'P2套件合集', 'menu', NULL),
  ('dev:p3-suite',            _utf8mb4'P3套件合集', 'menu', NULL),
  ('dev:gap-suite',           _utf8mb4'补齐套件', 'menu', NULL),
  ('mkt:effect',              _utf8mb4'营销效果', 'menu', NULL),
  ('mkt:channel',             _utf8mb4'渠道配置', 'menu', NULL),
  ('mkt:template',            _utf8mb4'消息模板', 'menu', NULL),
  ('mkt:touch-task',          _utf8mb4'触达任务', 'menu', NULL),
  ('mkt:journey',             _utf8mb4'营销旅程', 'menu', NULL),
  ('mkt:conversion',          _utf8mb4'触达效果', 'menu', NULL),
  ('mkt:pricing',             _utf8mb4'动态定价', 'menu', NULL),
  ('pay:dcep',                _utf8mb4'数字人民币', 'menu', NULL),
  ('sys:qualification',       _utf8mb4'商户资质材料', 'menu', NULL),
  ('sys:login-log',           _utf8mb4'登录日志', 'menu', NULL),
  ('dev:forecast',            _utf8mb4'客流预测与智能排班', 'menu', NULL);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id) VALUES
  ('pos:order:add',           _utf8mb4'新增订单', 'button', NULL),
  ('pos:order:pay',           _utf8mb4'订单收款', 'button', NULL),
  ('pos:order:refund',        _utf8mb4'订单退款', 'button', NULL),
  ('pos:order:void',          _utf8mb4'订单作废', 'button', NULL),
  ('pos:order:list',          _utf8mb4'订单查询', 'button', NULL),
  ('pos:order:stat',          _utf8mb4'收银统计', 'button', NULL),
  ('pos:refund',              _utf8mb4'退款管理', 'button', NULL),
  ('pos:reconciliation:list', _utf8mb4'支付对账', 'button', NULL),
  ('booking:add',             _utf8mb4'新增预约', 'button', NULL),
  ('booking:edit',            _utf8mb4'编辑预约', 'button', NULL),
  ('booking:delete',          _utf8mb4'删除预约', 'button', NULL),
  ('booking:confirm',         _utf8mb4'确认预约', 'button', NULL),
  ('booking:cancel',          _utf8mb4'取消预约', 'button', NULL),
  ('booking:checkin',         _utf8mb4'签到', 'button', NULL),
  ('booking:start',           _utf8mb4'开始服务', 'button', NULL),
  ('booking:complete',        _utf8mb4'完成服务', 'button', NULL),
  ('booking:noshow',          _utf8mb4'标记爽约', 'button', NULL),
  ('booking:schedule:list',   _utf8mb4'排班列表', 'button', NULL),
  ('booking:schedule:add',    _utf8mb4'新增排班', 'button', NULL),
  ('booking:schedule:edit',   _utf8mb4'编辑排班', 'button', NULL),
  ('booking:schedule:delete', _utf8mb4'删除排班', 'button', NULL),
  ('wallet:recharge',         _utf8mb4'储值充值', 'button', NULL),
  ('wallet:consume',          _utf8mb4'储值消费', 'button', NULL),
  ('wallet:refund',           _utf8mb4'储值退款', 'button', NULL),
  ('wallet:promotion',        _utf8mb4'充值赠送', 'button', NULL),
  ('package:add',             _utf8mb4'新增套餐', 'button', NULL),
  ('package:edit',            _utf8mb4'编辑套餐', 'button', NULL),
  ('package:delete',          _utf8mb4'删除套餐', 'button', NULL),
  ('package:purchase',        _utf8mb4'购买套餐', 'button', NULL),
  ('package:redeem',          _utf8mb4'核销套餐', 'button', NULL),
  ('package:freeze',          _utf8mb4'冻结套餐', 'button', NULL),
  ('package:transfer',        _utf8mb4'转让套餐', 'button', NULL),
  ('package:audit',           _utf8mb4'套餐审计', 'button', NULL),
  ('stock:add',               _utf8mb4'新增库存', 'button', NULL),
  ('stock:edit',              _utf8mb4'编辑库存', 'button', NULL),
  ('stock:delete',            _utf8mb4'删除库存', 'button', NULL),
  ('stock:inbound',           _utf8mb4'入库', 'button', NULL),
  ('stock:outbound',          _utf8mb4'出库', 'button', NULL),
  ('stock:check',             _utf8mb4'盘点', 'button', NULL),
  ('stock:alert',             _utf8mb4'库存预警', 'button', NULL),
  ('stock:stat',              _utf8mb4'库存统计', 'button', NULL),
  ('stock:export',            _utf8mb4'库存导出', 'button', NULL),
  ('stock:consumption',       _utf8mb4'消耗记录', 'button', NULL),
  ('stock:record',            _utf8mb4'出入库记录', 'button', NULL),
  ('commission:list',         _utf8mb4'提成列表', 'button', NULL),
  ('commission:rule:list',    _utf8mb4'提成规则', 'button', NULL),
  ('commission:rule:add',     _utf8mb4'新增规则', 'button', NULL),
  ('commission:rule:edit',    _utf8mb4'编辑规则', 'button', NULL),
  ('commission:rule:delete',  _utf8mb4'删除规则', 'button', NULL),
  ('commission:record:list',  _utf8mb4'提成记录', 'button', NULL),
  ('commission:salary:list',  _utf8mb4'工资报表', 'button', NULL),
  ('commission:salary:confirm',_utf8mb4'工资确认', 'button', NULL),
  ('commission:salary:pay',   _utf8mb4'工资发放', 'button', NULL),
  ('commission:settlement:list',_utf8mb4'结算管理', 'button', NULL),
  ('commission:settlement:audit',_utf8mb4'结算审核', 'button', NULL),
  ('commission:batch',        _utf8mb4'结算批次', 'button', NULL),
  ('commission:reversal',     _utf8mb4'提成冲正', 'button', NULL),
  ('report:dashboard',        _utf8mb4'数据看板', 'button', NULL),
  ('report:biz:list',         _utf8mb4'营业报表', 'button', NULL),
  ('report:export:add',       _utf8mb4'新增导出', 'button', NULL),
  ('report:export:download',  _utf8mb4'导出下载', 'button', NULL),
  ('report:export:list',      _utf8mb4'导出管理', 'button', NULL),
  ('report:task',             _utf8mb4'导出任务', 'button', NULL),
  ('report:caliber',          _utf8mb4'指标口径', 'button', NULL),
  ('market:list',             _utf8mb4'营销列表', 'button', NULL),
  ('market:coupon:list',      _utf8mb4'优惠券', 'button', NULL),
  ('market:coupon:add',       _utf8mb4'新增优惠券', 'button', NULL),
  ('market:coupon:edit',      _utf8mb4'编辑优惠券', 'button', NULL),
  ('market:coupon:delete',    _utf8mb4'删除优惠券', 'button', NULL),
  ('market:point:list',       _utf8mb4'积分商城', 'button', NULL),
  ('market:point:add',        _utf8mb4'新增积分', 'button', NULL),
  ('market:point:edit',       _utf8mb4'编辑积分', 'button', NULL),
  ('market:point:delete',     _utf8mb4'删除积分', 'button', NULL),
  ('market:activity:list',    _utf8mb4'营销活动', 'button', NULL),
  ('market:activity:add',     _utf8mb4'新增活动', 'button', NULL),
  ('market:activity:edit',    _utf8mb4'编辑活动', 'button', NULL),
  ('market:activity:delete',  _utf8mb4'删除活动', 'button', NULL),
  ('mkt:promotion',           _utf8mb4'促销活动', 'button', NULL),
  ('mkt:coupon',              _utf8mb4'优惠券', 'button', NULL),
  ('mkt:points',              _utf8mb4'积分', 'button', NULL),
  ('sys:config:list',         _utf8mb4'系统配置', 'button', NULL),
  ('sys:config:edit',         _utf8mb4'编辑配置', 'button', NULL),
  ('sys:tenant:list',         _utf8mb4'租户列表', 'button', NULL),
  ('sys:tenant:add',          _utf8mb4'新增租户', 'button', NULL),
  ('sys:tenant:edit',         _utf8mb4'编辑租户', 'button', NULL),
  ('sys:tenant:delete',       _utf8mb4'删除租户', 'button', NULL),
  ('sys:tenant:freeze',       _utf8mb4'冻结租户', 'button', NULL),
  ('sys:log:list',            _utf8mb4'操作日志', 'button', NULL),
  ('sys:log:export',          _utf8mb4'日志导出', 'button', NULL),
  ('sys:log:clear',           _utf8mb4'清空日志', 'button', NULL),
  ('sys:login-log:list',      _utf8mb4'登录日志', 'button', NULL),
  ('sys:login-log:export',    _utf8mb4'登录日志导出', 'button', NULL),
  ('sys:login-log:clear',     _utf8mb4'清空登录日志', 'button', NULL),
  ('sys:dcep:view',           _utf8mb4'数字人民币查看', 'button', NULL),
  ('sys:dcep:edit',           _utf8mb4'数字人民币编辑', 'button', NULL),
  ('sys:dcep:test',           _utf8mb4'数字人民币测试', 'button', NULL),
  ('employee:list',           _utf8mb4'员工列表', 'button', NULL),
  ('employee:add',            _utf8mb4'新增员工', 'button', NULL),
  ('employee:edit',           _utf8mb4'编辑员工', 'button', NULL),
  ('employee:delete',         _utf8mb4'删除员工', 'button', NULL),
  ('employee:reset-password', _utf8mb4'重置密码', 'button', NULL);

-- 4. 重新绑定 sys_menu_permission 路由（路由本身无中文，无需 _utf8mb4）
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

-- 5. ADMIN 角色全量授权（自动获得新加的 menu 类型权限）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.permission_type = 'menu'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );

-- 6. 验证
SELECT '=== sys_permission 总数 ===' AS '';
SELECT permission_type, COUNT(*) AS cnt FROM sys_permission GROUP BY permission_type ORDER BY permission_type;
SELECT '=== ADMIN 实际看到的菜单 ===' AS '';
SELECT p.permission_name, smp.route_path, smp.sort_order
FROM sys_role_permission rp
INNER JOIN sys_role r ON rp.role_id = r.id
INNER JOIN sys_permission p ON rp.permission_id = p.id
INNER JOIN sys_menu_permission smp ON smp.permission_id = p.id
WHERE r.role_code = 'ADMIN' AND p.permission_type = 'menu'
ORDER BY smp.sort_order, p.permission_name;
