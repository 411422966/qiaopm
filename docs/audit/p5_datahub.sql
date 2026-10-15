-- P5 数据中台

-- 维度表（主数据）
CREATE TABLE IF NOT EXISTS `dim_table` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `dim_code` VARCHAR(64) NOT NULL COMMENT '维度编码',
  `dim_name` VARCHAR(128) NOT NULL,
  `dim_type` VARCHAR(32) DEFAULT 'BUSINESS' COMMENT 'BUSINESS/CUSTOM/TIME/REGION',
  `description` TEXT,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `dim_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主数据维度表';

-- 维度值
CREATE TABLE IF NOT EXISTS `dim_value` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `dim_id` BIGINT NOT NULL,
  `value_code` VARCHAR(64) NOT NULL,
  `value_name` VARCHAR(128) NOT NULL,
  `parent_id` BIGINT DEFAULT NULL COMMENT '层级',
  `sort_order` INT DEFAULT 0,
  `extra_json` VARCHAR(500) DEFAULT NULL COMMENT '扩展属性',
  `status` VARCHAR(16) DEFAULT 'ACTIVE',

  INDEX `idx_dim` (`dim_id`),
  INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维度值';

-- 元数据（数据源描述）
CREATE TABLE IF NOT EXISTS `meta_table` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `table_code` VARCHAR(128) NOT NULL,
  `table_name` VARCHAR(255) NOT NULL,
  `db_name` VARCHAR(64) DEFAULT NULL,
  `schema_name` VARCHAR(64) DEFAULT NULL,
  `category` VARCHAR(32) DEFAULT 'BUSINESS' COMMENT 'BUSINESS/LOG/AI/OPS',
  `description` TEXT,
  `row_count` BIGINT DEFAULT 0,
  `size_mb` DECIMAL(10,2) DEFAULT 0,
  `owner` VARCHAR(64) DEFAULT NULL,
  `tag` VARCHAR(255) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `last_sync_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `table_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据表';

-- 字段元数据
CREATE TABLE IF NOT EXISTS `meta_column` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `meta_table_id` BIGINT NOT NULL,
  `column_name` VARCHAR(64) NOT NULL,
  `column_type` VARCHAR(32) DEFAULT NULL,
  `data_type` VARCHAR(32) DEFAULT NULL,
  `is_primary_key` TINYINT DEFAULT 0,
  `is_nullable` TINYINT DEFAULT 1,
  `description` VARCHAR(255) DEFAULT NULL,
  `is_sensitive` TINYINT DEFAULT 0 COMMENT '1=敏感',
  `enum_values` VARCHAR(255) DEFAULT NULL COMMENT '枚举值',
  `sort_order` INT DEFAULT 0,

  INDEX `idx_meta_table` (`meta_table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段元数据';

-- 数据血缘（表级）
CREATE TABLE IF NOT EXISTS `meta_lineage` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `source_table_code` VARCHAR(128) NOT NULL,
  `target_table_code` VARCHAR(128) NOT NULL,
  `transformation` VARCHAR(255) DEFAULT NULL COMMENT '转换说明',
  `lineage_type` VARCHAR(32) DEFAULT 'ETL' COMMENT 'ETL/JOIN/COPY/AGG',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_source` (`source_table_code`),
  INDEX `idx_target` (`target_table_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据血缘';

-- 指标定义
CREATE TABLE IF NOT EXISTS `metric_definition` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `metric_code` VARCHAR(64) NOT NULL,
  `metric_name` VARCHAR(128) NOT NULL,
  `metric_category` VARCHAR(32) DEFAULT 'BUSINESS' COMMENT 'BUSINESS/OPERATIONS/MARKETING/FINANCE/AI',
  `description` VARCHAR(255) DEFAULT NULL,
  `unit` VARCHAR(16) DEFAULT NULL COMMENT '%/元/次/人',

  -- SQL/公式
  `sql_template` TEXT COMMENT '计算 SQL 或公式',
  `source_table` VARCHAR(128) DEFAULT NULL,

  -- 维度关联
  `default_dimensions` VARCHAR(255) DEFAULT NULL COMMENT '默认维度IDs',
  `aggregation` VARCHAR(32) DEFAULT 'SUM' COMMENT 'SUM/AVG/COUNT/MAX/MIN',

  -- 目标/阈值
  `target_value` DECIMAL(15,2) DEFAULT NULL COMMENT '目标值',
  `warning_value` DECIMAL(15,2) DEFAULT NULL COMMENT '预警值',
  `higher_is_better` TINYINT DEFAULT 1 COMMENT '1=越高越好',

  `owner` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `metric_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义';

-- 指标快照（每天计算一次）
CREATE TABLE IF NOT EXISTS `metric_snapshot` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `metric_id` BIGINT NOT NULL,
  `metric_code` VARCHAR(64) NOT NULL,
  `snapshot_date` DATE NOT NULL,
  `value` DECIMAL(20,4) DEFAULT 0,
  `dim_id` BIGINT DEFAULT NULL COMMENT '维度ID',
  `store_id` BIGINT DEFAULT NULL,
  `period` VARCHAR(16) DEFAULT 'DAILY' COMMENT 'DAILY/WEEKLY/MONTHLY/YTD',
  `calc_duration_ms` INT DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_metric_date_dim` (`tenant_id`, `metric_id`, `snapshot_date`, `period`, `dim_id`, `store_id`),
  INDEX `idx_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标快照';

-- 看板配置
CREATE TABLE IF NOT EXISTS `dashboard` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `dashboard_code` VARCHAR(64) NOT NULL,
  `dashboard_name` VARCHAR(128) NOT NULL,
  `dashboard_type` VARCHAR(32) DEFAULT 'OPS' COMMENT 'OPS/SALES/MEMBER/MARKETING/FINANCE',
  `layout_json` TEXT COMMENT '布局配置',
  `metric_codes` VARCHAR(500) DEFAULT NULL COMMENT '包含的指标编码',
  `description` TEXT,
  `is_default` TINYINT DEFAULT 0,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `dashboard_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板';

-- 看板订阅
CREATE TABLE IF NOT EXISTS `dashboard_subscription` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `dashboard_id` BIGINT NOT NULL,
  `subscriber_id` BIGINT NOT NULL,
  `frequency` VARCHAR(16) DEFAULT 'DAILY' COMMENT 'DAILY/WEEKLY',
  `channel` VARCHAR(32) DEFAULT 'IN_APP' COMMENT 'IN_APP/EMAIL/WECHAT',
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_subscriber` (`subscriber_id`),
  INDEX `idx_dashboard` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板订阅';