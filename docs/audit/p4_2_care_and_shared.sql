-- P4.2.1 护肤配方/护理建议
CREATE TABLE IF NOT EXISTS `skin_profile` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `assess_date` DATE NOT NULL COMMENT '评估日期',

  -- 肤质特征
  `skin_type` VARCHAR(32) DEFAULT NULL COMMENT 'DRY/OILY/COMBINATION/SENSITIVE/NORMAL',
  `oil_level` TINYINT DEFAULT 0 COMMENT '油分 0-10',
  `moisture_level` TINYINT DEFAULT 0 COMMENT '水分 0-10',
  `elasticity_level` TINYINT DEFAULT 0 COMMENT '弹性 0-10',
  `pigmentation_level` TINYINT DEFAULT 0 COMMENT '色沉 0-10',
  `sensitivity_level` TINYINT DEFAULT 0 COMMENT '敏感 0-10',
  `pore_level` TINYINT DEFAULT 0 COMMENT '毛孔 0-10',
  `wrinkle_level` TINYINT DEFAULT 0 COMMENT '皱纹 0-10',

  -- 关注问题
  `concerns` VARCHAR(255) DEFAULT NULL COMMENT 'JSON数组 ["acne","wrinkle","pigmentation"]',
  `allergies` VARCHAR(255) DEFAULT NULL COMMENT '过敏成分 JSON',
  `lifestyle_tags` VARCHAR(255) DEFAULT NULL COMMENT 'LIFESTYLE_TAGS',

  -- 推断标签
  `age_group` VARCHAR(16) DEFAULT NULL COMMENT 'YOUNG/MIDDLE/MATURE',
  `life_stage` VARCHAR(32) DEFAULT NULL COMMENT 'STUDENT/NEW_MOM/CAREER/MATURE_WOMAN',

  -- 设备检测结果（可选）
  `device_report_url` VARCHAR(255) DEFAULT NULL,
  `assessor_id` BIGINT DEFAULT NULL COMMENT '评估师ID',

  `remark` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_member_date` (`tenant_id`, `member_id`, `assess_date`),
  INDEX `idx_tenant_member` (`tenant_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员肤质档案';

-- 护肤方案
CREATE TABLE IF NOT EXISTS `care_plan` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `plan_code` VARCHAR(64) NOT NULL,
  `plan_name` VARCHAR(128) NOT NULL,
  `goal` VARCHAR(255) DEFAULT NULL COMMENT '目标描述',
  `start_date` DATE DEFAULT NULL,
  `end_date` DATE DEFAULT NULL,
  `cycle_days` INT DEFAULT 28 COMMENT '周期天数',
  `sessions_total` INT DEFAULT 0 COMMENT '总次数',
  `sessions_per_week` DECIMAL(3,1) DEFAULT 1.0 COMMENT '周频次',
  `estimated_cost` DECIMAL(10,2) DEFAULT 0 COMMENT '预估费用',

  -- 包含的步骤（JSON）
  `steps_json` TEXT COMMENT '步骤 JSON 数组',

  -- 推荐理由
  `reason_json` TEXT COMMENT '推荐理由',

  -- 关联（可选）
  `treatment_plan_id` BIGINT DEFAULT NULL COMMENT '推荐疗程方案ID',
  `service_ids` VARCHAR(255) DEFAULT NULL COMMENT '推荐服务IDs（逗号分隔）',
  `product_ids` VARCHAR(255) DEFAULT NULL COMMENT '推荐产品IDs',

  `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/COMPLETED/PAUSED/EXPIRED',
  `adherence_score` DECIMAL(5,2) DEFAULT 0 COMMENT '依从度 0-100',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_tenant_member` (`tenant_id`, `member_id`),
  INDEX `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员护肤方案';

-- P4.2.3 多人共享套餐
CREATE TABLE IF NOT EXISTS `shared_package` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `package_code` VARCHAR(64) NOT NULL COMMENT '共享套餐编码',
  `package_name` VARCHAR(128) NOT NULL,
  `treatment_plan_id` BIGINT DEFAULT NULL COMMENT '关联疗程方案',
  `total_sessions` INT NOT NULL DEFAULT 0 COMMENT '总次数',
  `used_sessions` INT NOT NULL DEFAULT 0 COMMENT '已使用',
  `share_member_limit` INT NOT NULL DEFAULT 2 COMMENT '允许多人共享数',

  -- 价格
  `original_price` DECIMAL(10,2) DEFAULT 0 COMMENT '原总价',
  `shared_price` DECIMAL(10,2) DEFAULT 0 COMMENT '共享价',
  `per_member_price` DECIMAL(10,2) DEFAULT 0 COMMENT '人均价',

  -- 主持有人
  `owner_member_id` BIGINT NOT NULL COMMENT '主持有人',

  -- 有效期
  `valid_start_date` DATE DEFAULT NULL,
  `valid_end_date` DATE DEFAULT NULL,
  `expire_days` INT DEFAULT 365 COMMENT '从购买算起有效期',

  -- 状态
  `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/CANCELLED/DEPLETED',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `package_code`),
  INDEX `idx_tenant_owner` (`tenant_id`, `owner_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享套餐主表';

-- 共享成员
CREATE TABLE IF NOT EXISTS `shared_package_member` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `shared_package_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `join_date` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `used_sessions` INT NOT NULL DEFAULT 0 COMMENT '本人已使用',
  `relationship` VARCHAR(32) DEFAULT NULL COMMENT 'OWNER/SELF/SPOUSE/FAMILY/FRIEND',
  `join_method` VARCHAR(32) DEFAULT 'INVITE' COMMENT 'INVITE/SCAN/PURCHASE',
  `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LEFT/REVOKED',

  UNIQUE KEY `uk_pkg_member` (`shared_package_id`, `member_id`),
  INDEX `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享套餐成员表';

-- 共享套餐使用记录
CREATE TABLE IF NOT EXISTS `shared_package_usage` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `shared_package_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `booking_id` BIGINT DEFAULT NULL COMMENT '预约记录ID',
  `usage_date` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `sessions_used` INT NOT NULL DEFAULT 1,
  `employee_id` BIGINT DEFAULT NULL,
  `store_id` BIGINT DEFAULT NULL,
  `remark` TEXT,

  INDEX `idx_pkg` (`shared_package_id`),
  INDEX `idx_member` (`member_id`),
  INDEX `idx_tenant_date` (`tenant_id`, `usage_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享套餐使用记录';

-- 共享邀请码
CREATE TABLE IF NOT EXISTS `shared_package_invite` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `shared_package_id` BIGINT NOT NULL,
  `invite_code` VARCHAR(32) NOT NULL,
  `inviter_id` BIGINT NOT NULL,
  `invitee_phone` VARCHAR(20) DEFAULT NULL,
  `invitee_name` VARCHAR(64) DEFAULT NULL,
  `expire_at` DATETIME DEFAULT NULL,
  `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/EXPIRED/CANCELLED',
  `accepted_at` DATETIME DEFAULT NULL,
  `accepted_member_id` BIGINT DEFAULT NULL,

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`invite_code`),
  INDEX `idx_pkg` (`shared_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享邀请码';