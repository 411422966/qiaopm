-- P1 批次：美甲款式图库 / LBS / 礼品卡 / 订阅 / 支付 / 国际预约 / 自助签到 / SOP / 风控 / Cohort

-- 0001 美甲款式
CREATE TABLE IF NOT EXISTS `style_gallery` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `style_code` VARCHAR(64) NOT NULL,
  `style_name` VARCHAR(128) NOT NULL,
  `category` VARCHAR(32) DEFAULT 'NORMAL' COMMENT 'NORMAL/ART/GLITTER/FRENCH/MATTE/CHROME/3D',
  `difficulty` INT DEFAULT 1 COMMENT '1-5',
  `estimated_minutes` INT DEFAULT 60,
  `base_price` DECIMAL(10,2) DEFAULT 0,
  `service_id` BIGINT DEFAULT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `cover_image` VARCHAR(255) DEFAULT NULL,
  `images_json` VARCHAR(2000) DEFAULT NULL COMMENT '图片数组',
  `tags` VARCHAR(255) DEFAULT NULL,
  `view_count` INT DEFAULT 0,
  `order_count` INT DEFAULT 0,
  `like_count` INT DEFAULT 0,
  `is_hot` TINYINT DEFAULT 0,
  `is_new` TINYINT DEFAULT 0,
  `is_recommend` TINYINT DEFAULT 0,
  `season` VARCHAR(16) DEFAULT 'ALL',
  `gender` VARCHAR(16) DEFAULT 'FEMALE',
  `status` VARCHAR(16) DEFAULT 'ON_SHELF',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `style_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美甲款式';

CREATE TABLE IF NOT EXISTS `style_view_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `style_id` BIGINT NOT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `open_id` VARCHAR(64) DEFAULT NULL,
  `source` VARCHAR(32) DEFAULT 'H5',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_style` (`style_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='款式浏览';

-- 0002 LBS 上门服务
CREATE TABLE IF NOT EXISTS `lbs_technician` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `technician_id` BIGINT NOT NULL,
  `name` VARCHAR(64) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `current_lat` DECIMAL(10,6) DEFAULT NULL,
  `current_lng` DECIMAL(10,6) DEFAULT NULL,
  `max_radius_km` DECIMAL(6,2) DEFAULT 10,
  `available` TINYINT DEFAULT 1,
  `service_ids` VARCHAR(500) DEFAULT NULL,
  `rating` DECIMAL(3,2) DEFAULT 5.0,
  `online_status` VARCHAR(16) DEFAULT 'OFFLINE' COMMENT 'ONLINE/BUSY/OFFLINE',
  `last_loc_time` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_tech` (`technician_id`),
  INDEX `idx_location` (`current_lat`, `current_lng`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上门技师';

CREATE TABLE IF NOT EXISTS `lbs_service_order` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `order_no` VARCHAR(64) NOT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `member_name` VARCHAR(64) DEFAULT NULL,
  `member_phone` VARCHAR(20) DEFAULT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `service_name` VARCHAR(128) DEFAULT NULL,
  `address` VARCHAR(500) DEFAULT NULL,
  `lat` DECIMAL(10,6) DEFAULT NULL,
  `lng` DECIMAL(10,6) DEFAULT NULL,
  `scheduled_time` DATETIME DEFAULT NULL,
  `technician_id` BIGINT DEFAULT NULL,
  `dispatch_status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/DISPATCHED/ACCEPTED/ARRIVED/IN_SERVICE/COMPLETED/CANCELLED',
  `dispatch_time` DATETIME DEFAULT NULL,
  `accept_time` DATETIME DEFAULT NULL,
  `amount` DECIMAL(10,2) DEFAULT 0,
  `travel_fee` DECIMAL(10,2) DEFAULT 0,
  `notes` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_order_no` (`tenant_id`, `order_no`),
  INDEX `idx_status` (`dispatch_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上门服务订单';

-- 0003 礼品卡
CREATE TABLE IF NOT EXISTS `gift_card` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `card_no` VARCHAR(32) NOT NULL,
  `card_type` VARCHAR(32) DEFAULT 'FIXED' COMMENT 'FIXED/CUSTOM/SERVICE',
  `face_value` DECIMAL(10,2) DEFAULT 0,
  `balance` DECIMAL(10,2) DEFAULT 0,
  `service_id` BIGINT DEFAULT NULL,
  `service_count` INT DEFAULT 1,
  `buyer_id` BIGINT DEFAULT NULL,
  `buyer_name` VARCHAR(64) DEFAULT NULL,
  `recipient_name` VARCHAR(64) DEFAULT NULL,
  `recipient_phone` VARCHAR(20) DEFAULT NULL,
  `gift_message` VARCHAR(500) DEFAULT NULL,
  `theme` VARCHAR(64) DEFAULT 'DEFAULT',
  `cover_image` VARCHAR(255) DEFAULT NULL,
  `purchase_order_id` BIGINT DEFAULT NULL,
  `purchase_time` DATETIME DEFAULT NULL,
  `expire_time` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED/REFUNDED',
  `use_time` DATETIME DEFAULT NULL,
  `use_order_id` BIGINT DEFAULT NULL,
  `use_store_id` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_card` (`tenant_id`, `card_no`),
  INDEX `idx_status` (`status`),
  INDEX `idx_recipient` (`recipient_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品卡';

CREATE TABLE IF NOT EXISTS `gift_card_transaction` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `card_id` BIGINT NOT NULL,
  `tx_type` VARCHAR(16) DEFAULT 'CONSUME' COMMENT 'CONSUME/REFUND/RECHARGE',
  `amount` DECIMAL(10,2) DEFAULT 0,
  `balance` DECIMAL(10,2) DEFAULT 0,
  `order_id` BIGINT DEFAULT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `notes` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_card` (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品卡流水';

-- 0004 订阅计划
CREATE TABLE IF NOT EXISTS `subscription_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `plan_code` VARCHAR(64) NOT NULL,
  `plan_name` VARCHAR(128) NOT NULL,
  `plan_type` VARCHAR(32) DEFAULT 'MONTHLY' COMMENT 'MONTHLY/YEARLY/WEEKLY',
  `price` DECIMAL(10,2) DEFAULT 0,
  `billing_cycle_days` INT DEFAULT 30,
  `trial_days` INT DEFAULT 0,
  `service_count` INT DEFAULT NULL COMMENT '每月可约次数',
  `included_services` VARCHAR(500) DEFAULT NULL COMMENT '包含服务',
  `discount_rate` DECIMAL(5,2) DEFAULT NULL COMMENT '服务折扣',
  `gift_points` INT DEFAULT 0,
  `extra_benefits` VARCHAR(500) DEFAULT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `cover_image` VARCHAR(255) DEFAULT NULL,
  `total_subscribers` INT DEFAULT 0,
  `active_subscribers` INT DEFAULT 0,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `plan_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅计划';

CREATE TABLE IF NOT EXISTS `subscription_member` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `sub_no` VARCHAR(64) NOT NULL,
  `plan_id` BIGINT NOT NULL,
  `plan_code` VARCHAR(64) DEFAULT NULL,
  `member_id` BIGINT NOT NULL,
  `start_date` DATE DEFAULT NULL,
  `next_bill_date` DATE DEFAULT NULL,
  `end_date` DATE DEFAULT NULL,
  `used_count` INT DEFAULT 0,
  `remaining_count` INT DEFAULT 0,
  `auto_renew` TINYINT DEFAULT 1,
  `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/CANCELLED/EXPIRED',
  `payment_method` VARCHAR(32) DEFAULT NULL,
  `total_paid` DECIMAL(10,2) DEFAULT 0,
  `renew_count` INT DEFAULT 0,
  `cancelled_at` DATETIME DEFAULT NULL,
  `cancel_reason` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_sub_no` (`tenant_id`, `sub_no`),
  INDEX `idx_member` (`member_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订阅';

CREATE TABLE IF NOT EXISTS `subscription_billing` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `sub_member_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `plan_id` BIGINT NOT NULL,
  `billing_date` DATE DEFAULT NULL,
  `amount` DECIMAL(10,2) DEFAULT 0,
  `payment_method` VARCHAR(32) DEFAULT NULL,
  `payment_status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/PAID/FAILED',
  `bill_no` VARCHAR(64) DEFAULT NULL,
  `transaction_id` VARCHAR(128) DEFAULT NULL,
  `error_message` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `paid_at` DATETIME DEFAULT NULL,
  INDEX `idx_sub_member` (`sub_member_id`),
  INDEX `idx_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅账单';

-- 0005 支付网关
CREATE TABLE IF NOT EXISTS `payment_gateway` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `gateway_code` VARCHAR(32) NOT NULL COMMENT 'STRIPE/SQUARE/PAYPAL/AFTERPAY/KLARNA/ALIPAY/WECHAT',
  `gateway_name` VARCHAR(64) DEFAULT NULL,
  `merchant_id` VARCHAR(128) DEFAULT NULL,
  `api_key` VARCHAR(255) DEFAULT NULL,
  `secret_key` VARCHAR(255) DEFAULT NULL,
  `webhook_secret` VARCHAR(255) DEFAULT NULL,
  `currency` VARCHAR(8) DEFAULT 'CNY',
  `sandbox_mode` TINYINT DEFAULT 1,
  `test_public_key` VARCHAR(255) DEFAULT NULL,
  `test_secret_key` VARCHAR(255) DEFAULT NULL,
  `supported_methods` VARCHAR(255) DEFAULT NULL COMMENT 'CARD/BNPL/WALLET',
  `region` VARCHAR(32) DEFAULT 'CN',
  `extra_config` VARCHAR(2000) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`tenant_id`, `gateway_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付网关';

CREATE TABLE IF NOT EXISTS `payment_intent` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `intent_no` VARCHAR(64) NOT NULL,
  `gateway_code` VARCHAR(32) DEFAULT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `order_no` VARCHAR(64) DEFAULT NULL,
  `amount` DECIMAL(10,2) DEFAULT 0,
  `currency` VARCHAR(8) DEFAULT 'CNY',
  `method` VARCHAR(32) DEFAULT NULL COMMENT 'CARD/BNPL/WALLET/...',
  `intent_status` VARCHAR(16) DEFAULT 'CREATED' COMMENT 'CREATED/PROCESSING/SUCCEEDED/FAILED/CANCELLED',
  `client_secret` VARCHAR(255) DEFAULT NULL,
  `transaction_id` VARCHAR(128) DEFAULT NULL,
  `installments` INT DEFAULT NULL COMMENT 'BNPL 期数',
  `error_code` VARCHAR(64) DEFAULT NULL,
  `error_message` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `paid_at` DATETIME DEFAULT NULL,
  UNIQUE KEY `uk_no` (`tenant_id`, `intent_no`),
  INDEX `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付意图';

-- 0006 国际预约
CREATE TABLE IF NOT EXISTS `overseas_booking` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `booking_no` VARCHAR(64) NOT NULL,
  `source` VARCHAR(32) DEFAULT 'GOOGLE' COMMENT 'GOOGLE/INSTAGRAM/FACEBOOK/WHATSAPP',
  `external_id` VARCHAR(128) DEFAULT NULL,
  `customer_name` VARCHAR(64) DEFAULT NULL,
  `customer_phone` VARCHAR(32) DEFAULT NULL,
  `customer_email` VARCHAR(128) DEFAULT NULL,
  `customer_locale` VARCHAR(16) DEFAULT 'en',
  `service_id` BIGINT DEFAULT NULL,
  `service_name` VARCHAR(128) DEFAULT NULL,
  `staff_id` BIGINT DEFAULT NULL,
  `store_id` BIGINT DEFAULT NULL,
  `booking_date` DATE DEFAULT NULL,
  `booking_time` VARCHAR(16) DEFAULT NULL,
  `notes` TEXT,
  `status` VARCHAR(16) DEFAULT 'NEW' COMMENT 'NEW/CONFIRMED/CANCELLED/COMPLETED',
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `raw_payload` TEXT,
  `converted_member_id` BIGINT DEFAULT NULL,
  `converted_order_id` BIGINT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_no` (`tenant_id`, `booking_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='海外预约';

-- 0007 自助签到
CREATE TABLE IF NOT EXISTS `kiosk_device` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `store_id` BIGINT NOT NULL,
  `device_code` VARCHAR(64) NOT NULL,
  `device_name` VARCHAR(128) DEFAULT NULL,
  `location` VARCHAR(128) DEFAULT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `last_heartbeat` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'ONLINE' COMMENT 'ONLINE/OFFLINE/MAINTENANCE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_dev` (`tenant_id`, `device_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助签到设备';

CREATE TABLE IF NOT EXISTS `kiosk_checkin` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `device_id` BIGINT DEFAULT NULL,
  `store_id` BIGINT DEFAULT NULL,
  `booking_id` BIGINT DEFAULT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `checkin_method` VARCHAR(16) DEFAULT 'QRCODE' COMMENT 'QRCODE/PHONE/FACEID/MANUAL',
  `checkin_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `arrived_at` DATETIME DEFAULT NULL,
  `service_started_at` DATETIME DEFAULT NULL,
  `service_finished_at` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'CHECKED_IN' COMMENT 'CHECKED_IN/IN_SERVICE/COMPLETED/CANCELLED',
  `staff_id` BIGINT DEFAULT NULL,
  `notes` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_member` (`member_id`),
  INDEX `idx_booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助签到';

-- 0008 医美并发症 SOP
CREATE TABLE IF NOT EXISTS `complication_sop` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `sop_code` VARCHAR(64) NOT NULL,
  `sop_name` VARCHAR(128) NOT NULL,
  `category` VARCHAR(32) DEFAULT 'INJECTION' COMMENT 'INJECTION/LASER/SURGERY/OTHER',
  `severity` VARCHAR(16) DEFAULT 'MEDIUM' COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
  `trigger_symptom` VARCHAR(500) DEFAULT NULL,
  `trigger_timing` VARCHAR(255) DEFAULT NULL COMMENT 'IMMEDIATE/1H/24H/7D',
  `steps_json` TEXT,
  `medications` VARCHAR(1000) DEFAULT NULL,
  `equipment` VARCHAR(500) DEFAULT NULL,
  `escalation_rule` VARCHAR(500) DEFAULT NULL,
  `response_deadline_min` INT DEFAULT 60,
  `responsible_role` VARCHAR(64) DEFAULT NULL,
  `version` VARCHAR(16) DEFAULT '1.0',
  `status` VARCHAR(16) DEFAULT 'ACTIVE',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`tenant_id`, `sop_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='并发症 SOP';

CREATE TABLE IF NOT EXISTS `complication_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `event_no` VARCHAR(64) NOT NULL,
  `sop_id` BIGINT DEFAULT NULL,
  `sop_code` VARCHAR(64) DEFAULT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `severity` VARCHAR(16) DEFAULT 'MEDIUM',
  `symptoms` TEXT,
  `trigger_time` DATETIME DEFAULT NULL,
  `response_time` DATETIME DEFAULT NULL,
  `resolved_time` DATETIME DEFAULT NULL,
  `handler_id` BIGINT DEFAULT NULL,
  `handler_name` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/RESPONDING/RESOLVED/ESCALATED',
  `execution_log` TEXT,
  `outcome` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_no` (`tenant_id`, `event_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='并发症事件';

-- 0009 风控
CREATE TABLE IF NOT EXISTS `risk_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `rule_code` VARCHAR(64) NOT NULL,
  `rule_name` VARCHAR(128) NOT NULL,
  `rule_type` VARCHAR(32) DEFAULT 'TRANSACTION' COMMENT 'TRANSACTION/REFUND/LOGIN/COUPON',
  `rule_level` VARCHAR(16) DEFAULT 'RISK' COMMENT 'INFO/WARN/RISK/HIGH',
  `condition_expr` VARCHAR(1000) DEFAULT NULL,
  `threshold` DECIMAL(15,2) DEFAULT NULL,
  `action` VARCHAR(32) DEFAULT 'FLAG' COMMENT 'FLAG/BLOCK/REVIEW/NOTIFY',
  `notify_channel` VARCHAR(255) DEFAULT NULL,
  `enabled` TINYINT DEFAULT 1,
  `hit_count` INT DEFAULT 0,
  `last_hit_at` DATETIME DEFAULT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`tenant_id`, `rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则';

CREATE TABLE IF NOT EXISTS `risk_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `event_no` VARCHAR(64) NOT NULL,
  `rule_id` BIGINT DEFAULT NULL,
  `rule_code` VARCHAR(64) DEFAULT NULL,
  `rule_level` VARCHAR(16) DEFAULT 'RISK',
  `biz_type` VARCHAR(32) DEFAULT NULL,
  `biz_id` BIGINT DEFAULT NULL,
  `biz_no` VARCHAR(64) DEFAULT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `amount` DECIMAL(15,2) DEFAULT 0,
  `risk_score` INT DEFAULT 50,
  `action` VARCHAR(32) DEFAULT 'FLAG',
  `description` VARCHAR(1000) DEFAULT NULL,
  `handled` TINYINT DEFAULT 0,
  `handle_status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/PASSED/REJECTED',
  `handler_id` BIGINT DEFAULT NULL,
  `handle_notes` VARCHAR(500) DEFAULT NULL,
  `handled_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_no` (`tenant_id`, `event_no`),
  INDEX `idx_status` (`handle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控事件';

-- 0010 Cohort
CREATE TABLE IF NOT EXISTS `cohort_definition` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `cohort_code` VARCHAR(64) NOT NULL,
  `cohort_name` VARCHAR(128) NOT NULL,
  `cohort_type` VARCHAR(32) DEFAULT 'MONTHLY' COMMENT 'MONTHLY/WEEKLY/QUARTERLY',
  `metric` VARCHAR(64) DEFAULT 'RETAIN' COMMENT 'RETAIN/REVENUE/CHURN',
  `target_dim` VARCHAR(32) DEFAULT 'MEMBER' COMMENT 'MEMBER/ORDER/SERVICE',
  `description` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_code` (`tenant_id`, `cohort_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cohort 队列';

CREATE TABLE IF NOT EXISTS `cohort_result` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `cohort_id` BIGINT DEFAULT NULL,
  `cohort_code` VARCHAR(64) DEFAULT NULL,
  `cohort_period` VARCHAR(16) NOT NULL COMMENT '如 2025-01',
  `size` INT DEFAULT 0,
  `period_index` INT DEFAULT 0 COMMENT '距 cohort 起点的月份',
  `value` DECIMAL(15,4) DEFAULT 0 COMMENT '留存率/Revenue/Churn',
  `value_type` VARCHAR(16) DEFAULT 'RATE' COMMENT 'RATE/COUNT/AMOUNT',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_cohort` (`cohort_code`, `period_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Cohort 结果';