-- 补齐 4 项：生日提醒 / NoShow 预测 / 大众点评 / 配方

-- 生日提醒
CREATE TABLE IF NOT EXISTS `birthday_member` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `birthday` DATE NOT NULL,
  `birthday_type` VARCHAR(16) DEFAULT 'SOLAR' COMMENT 'SOLAR/LUNAR',
  `lunar_month` INT DEFAULT NULL,
  `lunar_day` INT DEFAULT NULL,
  `tags` VARCHAR(255) DEFAULT NULL,
  `auto_send` TINYINT DEFAULT 1,
  `auto_coupon` VARCHAR(64) DEFAULT NULL,
  `auto_coupon_amount` DECIMAL(10,2) DEFAULT NULL,
  `vip_level` INT DEFAULT NULL,
  `last_sent_year` INT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_member` (`tenant_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生日会员';

CREATE TABLE IF NOT EXISTS `birthday_send_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `send_year` INT NOT NULL,
  `send_type` VARCHAR(16) DEFAULT 'WECHAT' COMMENT 'WECHAT/SMS/EMAIL/ALL',
  `gift_type` VARCHAR(32) DEFAULT NULL COMMENT 'COUPON/POINTS/GIFT/POINTS_COUPON',
  `gift_value` VARCHAR(255) DEFAULT NULL,
  `coupon_code` VARCHAR(64) DEFAULT NULL,
  `points_awarded` INT DEFAULT 0,
  `template_id` VARCHAR(64) DEFAULT NULL,
  `message` VARCHAR(1000) DEFAULT NULL,
  `channel_message_id` VARCHAR(128) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
  `error_message` VARCHAR(500) DEFAULT NULL,
  `sent_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_year_member` (`send_year`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生日发送日志';

-- NoShow 预测
CREATE TABLE IF NOT EXISTS `no_show_model` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `model_code` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(128) DEFAULT NULL,
  `algorithm` VARCHAR(32) DEFAULT 'XGBOOST' COMMENT 'XGBOOST/LIGHTGBM/RANDOM_FOREST/LOGISTIC',
  `version` VARCHAR(32) DEFAULT '1.0',
  `accuracy` DECIMAL(5,2) DEFAULT NULL,
  `precision_score` DECIMAL(5,2) DEFAULT NULL,
  `recall_score` DECIMAL(5,2) DEFAULT NULL,
  `f1_score` DECIMAL(5,2) DEFAULT NULL,
  `auc` DECIMAL(5,2) DEFAULT NULL,
  `feature_json` TEXT,
  `trained_at` DATETIME DEFAULT NULL,
  `sample_count` INT DEFAULT 0,
  `is_active` TINYINT DEFAULT 0,
  `description` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`tenant_id`, `model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NoShow 模型';

CREATE TABLE IF NOT EXISTS `no_show_prediction` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `booking_id` BIGINT NOT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `model_code` VARCHAR(64) DEFAULT NULL,
  `risk_score` DECIMAL(5,2) DEFAULT NULL,
  `risk_level` VARCHAR(16) DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH',
  `features_json` TEXT,
  `reason` VARCHAR(500) DEFAULT NULL,
  `actual_status` VARCHAR(16) DEFAULT NULL COMMENT 'COMPLETED/NO_SHOW/CANCELLED',
  `feedback_at` DATETIME DEFAULT NULL,
  `predicted_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_booking` (`booking_id`),
  INDEX `idx_risk` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NoShow 预测';

-- 大众点评
CREATE TABLE IF NOT EXISTS `dianping_shop` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `store_id` BIGINT DEFAULT NULL,
  `shop_id` VARCHAR(64) NOT NULL,
  `shop_name` VARCHAR(128) DEFAULT NULL,
  `app_key` VARCHAR(255) DEFAULT NULL,
  `app_secret` VARCHAR(255) DEFAULT NULL,
  `access_token` VARCHAR(500) DEFAULT NULL,
  `token_expire_at` DATETIME DEFAULT NULL,
  `category` VARCHAR(64) DEFAULT NULL,
  `city` VARCHAR(32) DEFAULT NULL,
  `business_key` VARCHAR(64) DEFAULT NULL,
  `webhook_url` VARCHAR(500) DEFAULT NULL,
  `last_sync_at` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/ERROR',
  `error_message` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_shop` (`tenant_id`, `shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大众点评店铺';

CREATE TABLE IF NOT EXISTS `dianping_deal` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `shop_id` VARCHAR(64) DEFAULT NULL,
  `deal_id` VARCHAR(64) NOT NULL,
  `deal_name` VARCHAR(255) DEFAULT NULL,
  `category` VARCHAR(64) DEFAULT NULL,
  `price` DECIMAL(10,2) DEFAULT 0,
  `original_price` DECIMAL(10,2) DEFAULT 0,
  `stock` INT DEFAULT 0,
  `sold_count` INT DEFAULT 0,
  `start_time` DATETIME DEFAULT NULL,
  `end_time` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ON_SALE',
  `local_service_id` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_shop_deal` (`shop_id`, `deal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大众点评团购';

CREATE TABLE IF NOT EXISTS `dianping_order` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `shop_id` VARCHAR(64) DEFAULT NULL,
  `deal_id` VARCHAR(64) DEFAULT NULL,
  `dp_order_id` VARCHAR(64) NOT NULL,
  `buyer_phone` VARCHAR(20) DEFAULT NULL,
  `buyer_name` VARCHAR(64) DEFAULT NULL,
  `amount` DECIMAL(10,2) DEFAULT 0,
  `quantity` INT DEFAULT 1,
  `serial_no` VARCHAR(64) DEFAULT NULL COMMENT '券码',
  `verify_code` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'UNCONSUMED' COMMENT 'UNCONSUMED/CONSUMED/REFUND/EXPIRED',
  `consume_time` DATETIME DEFAULT NULL,
  `local_booking_id` BIGINT DEFAULT NULL,
  `local_member_id` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_order` (`tenant_id`, `dp_order_id`),
  INDEX `idx_serial` (`serial_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大众点评订单';

CREATE TABLE IF NOT EXISTS `dianping_sync_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `shop_id` VARCHAR(64) DEFAULT NULL,
  `sync_type` VARCHAR(32) DEFAULT NULL COMMENT 'SHOP/DEAL/ORDER/REVIEW',
  `success_count` INT DEFAULT 0,
  `fail_count` INT DEFAULT 0,
  `duration_ms` INT DEFAULT 0,
  `status` VARCHAR(16) DEFAULT 'SUCCESS',
  `error_message` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点评同步日志';

-- 配方
CREATE TABLE IF NOT EXISTS `care_formula` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `formula_code` VARCHAR(64) NOT NULL,
  `formula_name` VARCHAR(128) NOT NULL,
  `formula_type` VARCHAR(32) DEFAULT 'HAIR_DYE' COMMENT 'HAIR_DYE/SKINCARE/NAIL/BODY/MEDICAL/CUSTOM',
  `applicable_service` VARCHAR(255) DEFAULT NULL,
  `skin_type` VARCHAR(64) DEFAULT NULL COMMENT 'DRY/OILY/MIXED/SENSITIVE/NORMAL',
  `hair_type` VARCHAR(64) DEFAULT NULL COMMENT 'DAMAGED/DRIED/OILY/CURLY/STRAIGHT/COLORED',
  `ingredients_json` TEXT COMMENT '原料列表 JSON [{name,ratio,unit,supplier}]',
  `steps_json` TEXT COMMENT '步骤 JSON [{order,action,duration,note}]',
  `duration_min` INT DEFAULT NULL COMMENT '护理时长',
  `caution` VARCHAR(500) DEFAULT NULL,
  `tags` VARCHAR(255) DEFAULT NULL,
  `is_premium` TINYINT DEFAULT 0,
  `version` VARCHAR(16) DEFAULT '1.0',
  `use_count` INT DEFAULT 0,
  `description` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`tenant_id`, `formula_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护理配方';

CREATE TABLE IF NOT EXISTS `care_formula_apply` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `formula_id` BIGINT NOT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `booking_id` BIGINT DEFAULT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `technician_id` BIGINT DEFAULT NULL,
  `applied_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `feedback` VARCHAR(500) DEFAULT NULL,
  `rating` INT DEFAULT NULL,
  `effect_json` VARCHAR(2000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_formula_member` (`formula_id`, `member_id`),
  INDEX `idx_booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配方应用记录';