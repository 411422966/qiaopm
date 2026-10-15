-- P0 缺失：配方/护理建议 + 会员日自动化

-- 配方/护理方案
CREATE TABLE IF NOT EXISTS `care_recipe` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `recipe_code` VARCHAR(64) NOT NULL,
  `recipe_name` VARCHAR(128) NOT NULL,
  `skin_type` VARCHAR(32) DEFAULT NULL COMMENT '适用肤质：DRY/OILY/MIXED/SENSITIVE/NORMAL/COMBINATION',
  `concerns` VARCHAR(255) DEFAULT NULL COMMENT '针对问题：WHITENING/ANTI_AGE/ACNE/...',
  `description` TEXT,
  `steps_json` TEXT COMMENT '步骤JSON',
  `session_count` INT DEFAULT 4 COMMENT '建议次数',
  `interval_days` INT DEFAULT 14 COMMENT '间隔天数',
  `estimated_cost` DECIMAL(10,2) DEFAULT 0,
  `expected_improvement` VARCHAR(500) DEFAULT NULL,
  `cover_image` VARCHAR(255) DEFAULT NULL,
  `tag` VARCHAR(32) DEFAULT NULL COMMENT 'HOT/NEW/RECOMMEND/SEASONAL',
  `applicable_season` VARCHAR(32) DEFAULT 'ALL',
  `gender` VARCHAR(16) DEFAULT 'ALL',
  `age_min` INT DEFAULT NULL,
  `age_max` INT DEFAULT NULL,
  `score` INT DEFAULT 0 COMMENT '评分',
  `use_count` INT DEFAULT 0,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `recipe_code`),
  INDEX `idx_tenant_skin` (`tenant_id`, `skin_type`),
  INDEX `idx_tenant_concern` (`tenant_id`, `concerns`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配方/护理方案';

-- 配方-服务关联（包含什么）
CREATE TABLE IF NOT EXISTS `care_recipe_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `recipe_id` BIGINT NOT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `item_type` VARCHAR(32) DEFAULT 'SERVICE' COMMENT 'SERVICE/PRODUCT/STEP',
  `item_name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `usage_qty` VARCHAR(32) DEFAULT NULL,
  `sequence` INT DEFAULT 0,
  `estimated_minutes` INT DEFAULT NULL,

  INDEX `idx_recipe` (`recipe_id`),
  INDEX `idx_service` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配方项目';

-- 配方推荐记录（皮肤检测结果）
CREATE TABLE IF NOT EXISTS `care_skin_assessment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `assessment_no` VARCHAR(64) NOT NULL,

  -- 检测输入
  `skin_type` VARCHAR(32) DEFAULT NULL,
  `oil_level` INT DEFAULT NULL COMMENT '油分 1-10',
  `moisture_level` INT DEFAULT NULL COMMENT '水分 1-10',
  `elasticity_level` INT DEFAULT NULL COMMENT '弹性 1-10',
  `pigment_level` INT DEFAULT NULL COMMENT '色素沉着 1-10',
  `pore_level` INT DEFAULT NULL COMMENT '毛孔 1-10',
  `wrinkle_level` INT DEFAULT NULL COMMENT '皱纹 1-10',
  `concerns_json` VARCHAR(500) DEFAULT NULL COMMENT '皮肤问题 JSON',

  -- 评估输出
  `skin_score` INT DEFAULT NULL COMMENT '综合 0-100',
  `recommended_recipe_id` BIGINT DEFAULT NULL,
  `recommended_recipe_ids` VARCHAR(255) DEFAULT NULL COMMENT 'Top 3 IDs',
  `analysis_json` TEXT COMMENT '详细分析',

  `source` VARCHAR(32) DEFAULT 'MANUAL' COMMENT 'MANUAL/AI/CV',
  `operator_id` BIGINT DEFAULT NULL,
  `assessment_date` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `next_assessment_date` DATETIME DEFAULT NULL,

  UNIQUE KEY `uk_no` (`tenant_id`, `assessment_no`),
  INDEX `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='皮肤检测评估';

-- 会员日配置
CREATE TABLE IF NOT EXISTS `member_day_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `config_name` VARCHAR(64) NOT NULL,
  `day_type` VARCHAR(32) DEFAULT 'MEMBER_DAY' COMMENT 'MEMBER_DAY/BIRTHDAY/ANNIVERSARY/SEASONAL',
  `trigger_rule` VARCHAR(255) DEFAULT NULL COMMENT '触发规则',

  -- 优惠
  `discount_rate` DECIMAL(5,2) DEFAULT NULL COMMENT '统一折扣',
  `gift_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '赠送金额',
  `gift_points` INT DEFAULT NULL COMMENT '赠送积分',
  `gift_coupon_id` BIGINT DEFAULT NULL,
  `free_service_ids` VARCHAR(255) DEFAULT NULL,
  `coupon_amount` DECIMAL(10,2) DEFAULT NULL,

  -- 范围
  `target_level` VARCHAR(64) DEFAULT 'ALL' COMMENT 'ALL/NORMAL/SILVER/GOLD/DIAMOND',
  `target_store_ids` VARCHAR(255) DEFAULT NULL,

  -- 触发条件
  `birthday_offset_days` INT DEFAULT NULL COMMENT '生日当天前后天数',
  `fixed_month` INT DEFAULT NULL,
  `fixed_day` INT DEFAULT NULL,
  `repeat_annually` TINYINT DEFAULT 1,

  -- 营销素材
  `banner_image` VARCHAR(255) DEFAULT NULL,
  `sms_template` VARCHAR(500) DEFAULT NULL,
  `push_title` VARCHAR(128) DEFAULT NULL,
  `push_content` VARCHAR(500) DEFAULT NULL,

  `priority` INT DEFAULT 0,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `effective_date` DATE DEFAULT NULL,
  `expire_date` DATE DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员日配置';

-- 会员日执行记录
CREATE TABLE IF NOT EXISTS `member_day_execution` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `config_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `member_name` VARCHAR(64) DEFAULT NULL,
  `member_phone` VARCHAR(20) DEFAULT NULL,
  `trigger_reason` VARCHAR(64) DEFAULT NULL COMMENT 'BIRTHDAY/MEMBER_DAY/ANNIVERSARY',
  `trigger_date` DATE NOT NULL,
  `channel` VARCHAR(32) DEFAULT 'SMS' COMMENT 'SMS/WECHAT/PUSH/EMAIL',
  `discount_amount` DECIMAL(10,2) DEFAULT 0,
  `gift_amount` DECIMAL(10,2) DEFAULT 0,
  `gift_points` INT DEFAULT 0,
  `coupon_code` VARCHAR(64) DEFAULT NULL,
  `send_status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/USED',
  `sent_at` DATETIME DEFAULT NULL,
  `used_at` DATETIME DEFAULT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `error_message` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_member` (`member_id`),
  INDEX `idx_date` (`trigger_date`),
  INDEX `idx_status` (`send_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员日执行记录';