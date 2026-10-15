-- P4.3 电子价目表 + 卡项套餐可视化

-- 电子价目表主表
CREATE TABLE IF NOT EXISTS `price_list` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `list_code` VARCHAR(64) NOT NULL COMMENT '价目表编码',
  `list_name` VARCHAR(128) NOT NULL,
  `list_type` VARCHAR(32) DEFAULT 'STANDARD' COMMENT 'STANDARD/PROMOTION/CHANNEL/MEMBER',
  `store_id` BIGINT DEFAULT NULL COMMENT '适用门店（NULL=全部）',
  `member_level` VARCHAR(32) DEFAULT NULL COMMENT '适用会员等级',
  `effective_date` DATE DEFAULT NULL COMMENT '生效日期',
  `expire_date` DATE DEFAULT NULL COMMENT '失效日期',

  -- 状态
  `status` VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/EXPIRED/ARCHIVED',
  `is_default` TINYINT DEFAULT 0 COMMENT '1=默认价目表',
  `version` INT DEFAULT 1 COMMENT '版本号',

  `description` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `list_code`),
  INDEX `idx_tenant_status` (`tenant_id`, `status`),
  INDEX `idx_tenant_store` (`tenant_id`, `store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子价目表';

-- 价目表项
CREATE TABLE IF NOT EXISTS `price_list_item` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `price_list_id` BIGINT NOT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `category_id` BIGINT DEFAULT NULL,

  -- 价格
  `list_price` DECIMAL(10,2) DEFAULT 0 COMMENT '标价',
  `member_price` DECIMAL(10,2) DEFAULT 0 COMMENT '会员价',
  `vip_price` DECIMAL(10,2) DEFAULT 0 COMMENT 'VIP价',
  `discount_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '折扣率',

  -- 可视化字段
  `image_url` VARCHAR(255) DEFAULT NULL,
  `tag` VARCHAR(32) DEFAULT NULL COMMENT 'HOT/NEW/RECOMMEND/LIMITED',
  `sort_order` INT DEFAULT 0,
  `is_featured` TINYINT DEFAULT 0 COMMENT '1=推荐',
  `sold_count` INT DEFAULT 0 COMMENT '已售数量（用于排序）',

  `remark` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_price_list` (`price_list_id`),
  INDEX `idx_service` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价目表项目';

-- 卡项套餐（可视化增强）
CREATE TABLE IF NOT EXISTS `card_package_template` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `template_code` VARCHAR(64) NOT NULL,
  `template_name` VARCHAR(128) NOT NULL,

  -- 模板类型
  `package_type` VARCHAR(32) DEFAULT 'TIMES' COMMENT 'TIMES/AMOUNT/MIXED',
  `card_level` VARCHAR(32) DEFAULT 'NORMAL' COMMENT 'NORMAL/SILVER/GOLD/DIAMOND',

  -- 总览
  `total_sessions` INT DEFAULT 0 COMMENT '总次数',
  `total_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '总金额（储值卡）',
  `original_price` DECIMAL(10,2) DEFAULT 0 COMMENT '原价',
  `package_price` DECIMAL(10,2) DEFAULT 0 COMMENT '套餐价',
  `discount_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '折扣率',

  -- 可视化
  `cover_image` VARCHAR(255) DEFAULT NULL,
  `theme_color` VARCHAR(16) DEFAULT '#FF6B35' COMMENT '主色调',
  `accent_color` VARCHAR(16) DEFAULT '#FFE5D9' COMMENT '辅助色',
  `description` TEXT,
  `highlights` VARCHAR(500) DEFAULT NULL COMMENT '亮点 JSON',
  `tag` VARCHAR(32) DEFAULT NULL COMMENT 'HOT/NEW/RECOMMEND',

  -- 适用门店
  `store_ids` VARCHAR(255) DEFAULT NULL COMMENT '适用门店 IDs（NULL=全部）',

  -- 包含服务（JSON）
  `included_services` TEXT COMMENT '包含服务 JSON [{"name": "..."}]',
  `included_products` TEXT COMMENT '包含产品 JSON',

  -- 状态
  `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/ARCHIVED',
  `sold_count` INT DEFAULT 0,
  `view_count` INT DEFAULT 0,

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_code` (`tenant_id`, `template_code`),
  INDEX `idx_tenant_status` (`tenant_id`, `status`),
  INDEX `idx_tenant_type` (`tenant_id`, `package_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡项套餐模板';

-- 价目表查看记录（统计热度）
CREATE TABLE IF NOT EXISTS `price_list_view_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `price_list_id` BIGINT DEFAULT NULL,
  `service_id` BIGINT DEFAULT NULL,
  `template_id` BIGINT DEFAULT NULL,
  `member_id` BIGINT DEFAULT NULL,
  `channel` VARCHAR(32) DEFAULT 'STAFF' COMMENT 'STAFF/MEMBER/WECHAT',
  `view_date` DATE NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_tenant_date` (`tenant_id`, `view_date`),
  INDEX `idx_template_date` (`template_id`, `view_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价目表浏览日志';