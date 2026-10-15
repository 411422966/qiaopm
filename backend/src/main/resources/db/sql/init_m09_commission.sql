-- ============================================
-- M09 员工提成模块 - 数据库初始化
-- 2026-08-03
-- ============================================

CREATE TABLE IF NOT EXISTS `commission_rule` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `business_type` varchar(20) NOT NULL COMMENT 'SERVICE服务/PRODUCT产品/CARD开卡/RECHARGE充值',
  `target_id` bigint DEFAULT NULL COMMENT '指定服务/商品ID，NULL表示业务类型通用规则',
  `calculation_method` varchar(16) NOT NULL COMMENT 'RATE按比例/FIXED固定金额',
  `rate` decimal(7,4) DEFAULT NULL COMMENT '比例，如0.1000表示10%',
  `fixed_amount` decimal(12,2) DEFAULT NULL COMMENT '固定提成金额',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期，NULL长期有效',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态:1启用 0停用',
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  INDEX `idx_comm_rule_match` (`tenant_id`, `store_id`, `business_type`, `target_id`, `status`),
  INDEX `idx_comm_rule_effective` (`effective_date`, `expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工提成规则';

CREATE TABLE IF NOT EXISTS `commission_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `rule_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `source_type` varchar(20) NOT NULL COMMENT 'ORDER订单/PACKAGE_REDEMPTION核销',
  `source_id` bigint NOT NULL COMMENT '来源主记录ID',
  `source_item_id` bigint NOT NULL COMMENT '来源明细ID，用于幂等',
  `order_no` varchar(64),
  `service_id` bigint,
  `service_name` varchar(128),
  `base_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '提成基数',
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4),
  `fixed_amount` decimal(12,2),
  `commission_amount` decimal(12,2) NOT NULL COMMENT '提成金额',
  `business_time` datetime NOT NULL,
  `salary_month` char(7) NOT NULL COMMENT '工资月份YYYY-MM',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1待结算 2已结算 3已作废',
  `invalid_reason` varchar(255),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_comm_source_item` (`source_type`, `source_item_id`),
  INDEX `idx_comm_record_employee_month` (`employee_id`, `salary_month`, `status`),
  INDEX `idx_comm_record_order` (`source_id`, `order_no`),
  INDEX `idx_comm_record_business_time` (`business_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工提成记录';

CREATE TABLE IF NOT EXISTS `commission_salary` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `salary_month` char(7) NOT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT 0,
  `performance_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '业绩金额',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `adjustment_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '奖励/扣款调整',
  `payable_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '应发金额',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1草稿 2已确认 3已发放',
  `confirm_time` datetime,
  `pay_time` datetime,
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_comm_salary_employee_month` (`tenant_id`, `store_id`, `employee_id`, `salary_month`),
  INDEX `idx_comm_salary_month_status` (`salary_month`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工工资表';

-- 默认规则：指定项目规则优先于门店通用规则
INSERT INTO `commission_rule`
(`tenant_id`, `store_id`, `rule_name`, `business_type`, `target_id`, `calculation_method`, `rate`, `priority`, `effective_date`, `status`, `remark`)
SELECT 1, 1, '服务项目通用提成10%', 'SERVICE', NULL, 'RATE', 0.1000, 10, '2000-01-01', 1, 'M09 默认规则'
WHERE NOT EXISTS (
  SELECT 1 FROM `commission_rule`
  WHERE `tenant_id`=1 AND `store_id`=1 AND `business_type`='SERVICE' AND `target_id` IS NULL AND `deleted`=0
);

UPDATE `commission_rule`
SET `effective_date` = '2000-01-01'
WHERE `tenant_id`=1 AND `store_id`=1 AND `business_type`='SERVICE'
  AND `target_id` IS NULL AND `remark`='M09 默认规则';

SELECT 'commission_rule' AS tbl, COUNT(*) AS cnt FROM commission_rule
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record
UNION ALL SELECT 'commission_salary', COUNT(*) FROM commission_salary;
