-- ============================================
-- EXT-19 后续优化：
--   1) overseas_booking 新增 mini_program_open_id（小程序渠道 openId）
--   2) overseas_booking 表创建（兼容 dev 环境：实体已存在但缺 SQL）
-- 2026-09-05
-- ============================================================

-- 1. 表创建（IF NOT EXISTS：兼容已有库）
CREATE TABLE IF NOT EXISTS overseas_booking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    booking_no VARCHAR(64),
    source VARCHAR(32),
    external_id VARCHAR(128),
    customer_name VARCHAR(128),
    customer_phone VARCHAR(64),
    customer_email VARCHAR(128),
    customer_locale VARCHAR(16),
    mini_program_open_id VARCHAR(128) COMMENT '小程序渠道 openId（绑定后可走 MiniProgramSender）',
    service_id BIGINT,
    service_name VARCHAR(255),
    staff_id BIGINT,
    store_id BIGINT,
    booking_date DATE,
    booking_time VARCHAR(16),
    notes TEXT,
    status VARCHAR(32),
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    raw_payload TEXT,
    converted_member_id BIGINT,
    converted_order_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_store (store_id),
    INDEX idx_source (source),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='海外预约';

-- 2. 已存在库：补字段
ALTER TABLE overseas_booking
    ADD COLUMN IF NOT EXISTS mini_program_open_id VARCHAR(128) COMMENT '小程序渠道 openId';
