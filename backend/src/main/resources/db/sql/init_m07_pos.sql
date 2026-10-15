-- ============================================
-- M07 收银结算模块 - 数据库初始化
-- ============================================

-- 服务/产品表（订单明细需要引用）
CREATE TABLE IF NOT EXISTS `biz_service` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint,
  `service_code` varchar(64),
  `service_name` varchar(128) NOT NULL,
  `category_id` bigint COMMENT '分类',
  `unit_price` decimal(10,2) NOT NULL,
  `member_price` decimal(10,2) COMMENT '会员价',
  `unit` varchar(16) DEFAULT '次',
  `duration` int DEFAULT 0 COMMENT '时长(分钟)',
  `description` varchar(500),
  `image_url` varchar(500),
  `is_package` tinyint DEFAULT 0,
  `status` tinyint DEFAULT 1,
  `sort_order` int DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_biz_service_store` (`store_id`),
  INDEX `idx_biz_service_cat` (`category_id`)
) COMMENT='服务/产品表';

-- 收银订单表
CREATE TABLE IF NOT EXISTS `pos_order` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `order_no` varchar(64) UNIQUE NOT NULL,
  `member_id` bigint COMMENT '会员ID',
  `member_name` varchar(64),
  `member_phone` varchar(20),
  `booking_id` bigint COMMENT '关联预约',
  `type` tinyint NOT NULL COMMENT '类型:1消费 2充值 3混合',
  `total_amount` decimal(12,2) NOT NULL COMMENT '原价总额',
  `discount_amount` decimal(12,2) DEFAULT 0 COMMENT '折扣金额',
  `final_amount` decimal(12,2) NOT NULL COMMENT '实付金额',
  `paid_amount` decimal(12,2) DEFAULT 0 COMMENT '已付金额',
  `cashier_id` bigint NOT NULL COMMENT '收银员',
  `cashier_name` varchar(64),
  `remark` varchar(255),
  `status` tinyint DEFAULT 1 COMMENT '状态:1进行中 2已支付 3已退款 4已撤销',
  `pay_time` datetime COMMENT '支付时间',
  `refund_time` datetime COMMENT '退款时间',
  `refund_amount` decimal(12,2) DEFAULT 0,
  `refund_reason` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_order_store` (`store_id`),
  INDEX `idx_pos_order_member` (`member_id`),
  INDEX `idx_pos_order_date` (`store_id`, `create_time`)
) COMMENT='收银订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS `pos_order_item` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `order_id` bigint NOT NULL,
  `service_id` bigint,
  `service_name` varchar(128) NOT NULL,
  `quantity` int DEFAULT 1,
  `unit_price` decimal(10,2) NOT NULL,
  `total_price` decimal(12,2) NOT NULL,
  `discount_amount` decimal(10,2) DEFAULT 0,
  `final_price` decimal(12,2) NOT NULL,
  `package_order_id` bigint COMMENT '套餐核销ID',
  `package_times` int DEFAULT 0 COMMENT '核销次数',
  `employee_id` bigint COMMENT '服务技师',
  `employee_name` varchar(64),
  `remark` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_item_order` (`order_id`)
) COMMENT='订单明细表';

-- 支付记录表
CREATE TABLE IF NOT EXISTS `pos_payment` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `order_id` bigint NOT NULL,
  `payment_no` varchar(64) UNIQUE NOT NULL,
  `method` varchar(32) NOT NULL COMMENT '支付方式',
  `amount` decimal(12,2) NOT NULL,
  `status` tinyint DEFAULT 0 COMMENT '状态:0待支付 1成功 2失败 3已退款',
  `transaction_id` varchar(128) COMMENT '第三方流水号',
  `pay_time` datetime,
  `refund_time` datetime,
  `error_msg` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_payment_order` (`order_id`)
) COMMENT='支付记录表';

-- 初始化测试服务
INSERT INTO `biz_service` (`service_code`, `service_name`, `unit_price`, `member_price`, `unit`, `duration`, `status`, `sort_order`) VALUES
('SV0001', '基础面部护理', 280.00, 198.00, '次', 60, 1, 1),
('SV0002', '深层补水护理', 480.00, 358.00, '次', 90, 1, 2),
('SV0003', '肩颈舒缓按摩', 180.00, 128.00, '次', 45, 1, 3),
('SV0004', '全身精油SPA', 880.00, 698.00, '次', 120, 1, 4),
('SV0005', '美睫嫁接', 380.00, 288.00, '次', 90, 1, 5);

-- 初始化订单编号序列表（如果不存在）
CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `seq_name` varchar(64) UNIQUE,
  `current_val` bigint DEFAULT 0,
  `prefix` varchar(16),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='业务序列号表';