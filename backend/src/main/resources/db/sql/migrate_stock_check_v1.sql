-- ============================================
-- 盘点管理优化 - 增量迁移
-- 2026-08-02
-- 目标: 新增 确认人/确认时间 字段
-- 兼容原表结构，幂等可重跑
-- ============================================

ALTER TABLE stock_check
  ADD COLUMN IF NOT EXISTS `confirmed_by` bigint DEFAULT NULL COMMENT '确认人ID' AFTER `status`,
  ADD COLUMN IF NOT EXISTS `confirmed_by_name` varchar(64) DEFAULT NULL COMMENT '确认人' AFTER `confirmed_by`,
  ADD COLUMN IF NOT EXISTS `confirmed_time` datetime DEFAULT NULL COMMENT '确认时间' AFTER `confirmed_by_name`;