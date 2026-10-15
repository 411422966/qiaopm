-- ============================================
-- M09 commission module - DB init
-- 2026-08-03
-- ============================================

USE meiyetec;

-- 1) commission_rule: 提成规则表
CREATE TABLE IF NOT EXISTS `commission_rule` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `business_type` varchar(20) NOT NULL COMMENT 'SERVICE|PRODUCT|CARD|RECHARGE',
  `target_id` bigint DEFAULT NULL COMMENT '指定服务/商品ID，NULL表示通用规则',
  `calculation_method` varchar(16) NOT NULL COMMENT 'RATE按比例|FIXED固定金额',
  `rate` decimal(7,4) DEFAULT NULL COMMENT '比例，如0.1000表示10%',
  `fixed_amount` decimal(12,2) DEFAULT NULL COMMENT '固定提成金额',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期，NULL长期有效',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  INDEX `idx_comm_rule_match` (`tenant_id`, `store_id`, `business_type`, `target_id`, `status`),
  INDEX `idx_comm_rule_effective` (`effective_date`, `expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工提成规则';

-- 2) commission_record: 提成记录表（核心快照表）
CREATE TABLE IF NOT EXISTS `commission_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL COMMENT '员工姓名快照',
  `rule_id` bigint NOT NULL COMMENT '匹配规则ID',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称快照',
  `business_type` varchar(20) NOT NULL,
  `source_type` varchar(20) NOT NULL COMMENT 'ORDER|PACKAGE_REDEMPTION',
  `source_id` bigint NOT NULL COMMENT '来源主记录ID',
  `source_item_id` bigint NOT NULL COMMENT '来源明细ID，用于幂等',
  `order_no` varchar(64) COMMENT '订单号',
  `service_id` bigint COMMENT '服务项目ID',
  `service_name` varchar(128) COMMENT '服务名称快照',
  `base_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '提成基数（折后价）',
  `calculation_method` varchar(16) NOT NULL COMMENT 'RATE|FIXED',
  `rate` decimal(7,4) COMMENT '比例快照',
  `fixed_amount` decimal(12,2) COMMENT '固定金额快照',
  `commission_amount` decimal(12,2) NOT NULL COMMENT '提成金额',
  `business_time` datetime NOT NULL COMMENT '业务发生时间',
  `salary_month` char(7) NOT NULL COMMENT '工资月份YYYY-MM',
  `settlement_id` bigint DEFAULT NULL COMMENT '所属结算单ID',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1待结算 2已结算 3已作废',
  `invalid_reason` varchar(255) COMMENT '作废原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_comm_source_item` (`source_type`, `source_item_id`),
  INDEX `idx_comm_record_employee_month` (`employee_id`, `salary_month`, `status`),
  INDEX `idx_comm_record_order` (`source_id`, `order_no`),
  INDEX `idx_comm_record_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工提成记录';

-- 3) commission_settlement: 提成结算单
CREATE TABLE IF NOT EXISTS `commission_settlement` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `settlement_no` varchar(64) NOT NULL COMMENT '结算单号',
  `salary_month` char(7) NOT NULL COMMENT '结算月份YYYY-MM',
  `employee_count` int NOT NULL DEFAULT 0 COMMENT '涉及员工数',
  `total_commission` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '提成总额',
  `total_adjustment` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '调整总额',
  `total_payable` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '应付总额',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1草稿 2已确认 3已发放 4已取消',
  `confirm_time` datetime COMMENT '确认时间',
  `confirm_by` bigint COMMENT '确认人',
  `pay_time` datetime COMMENT '发放时间',
  `pay_by` bigint COMMENT '发放人',
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  INDEX `idx_settlement_month` (`salary_month`, `store_id`),
  INDEX `idx_settlement_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成结算单';

-- 4) commission_settlement_record: 结算单与提成的关联表
CREATE TABLE IF NOT EXISTS `commission_settlement_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL COMMENT '结算单ID',
  `commission_record_id` bigint NOT NULL COMMENT '提成记录ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `employee_name` varchar(64) NOT NULL COMMENT '员工姓名快照',
  `base_salary` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '基本工资',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '提成金额',
  `adjustment_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '调整金额',
  `payable_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '应付金额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_settlement_commission` (`settlement_id`, `commission_record_id`),
  INDEX `idx_settlement_employee` (`settlement_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单与提成记录关联';

-- 默认规则
INSERT INTO `commission_rule`
(`tenant_id`, `store_id`, `rule_name`, `business_type`, `target_id`, `calculation_method`, `rate`, `priority`, `effective_date`, `status`, `remark`)
SELECT 1, 1, 'service default 10%', 'SERVICE', NULL, 'RATE', 0.1000, 10, '2000-01-01', 1, 'm09 default'
WHERE NOT EXISTS (
  SELECT 1 FROM `commission_rule`
  WHERE `tenant_id`=1 AND `store_id`=1 AND `business_type`='SERVICE' AND `target_id` IS NULL AND `deleted`=0
);

SELECT 'commission_rule' AS tbl, COUNT(*) AS cnt FROM commission_rule WHERE deleted=0
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record WHERE deleted=0
UNION ALL SELECT 'commission_settlement', COUNT(*) FROM commission_settlement WHERE deleted=0
UNION ALL SELECT 'commission_settlement_record', COUNT(*) FROM commission_settlement_record WHERE deleted=0;
