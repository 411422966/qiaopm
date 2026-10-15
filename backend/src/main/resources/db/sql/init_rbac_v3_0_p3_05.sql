-- ============================================
-- P3 · T-P05：数字人民币 2.0（真实通道联调）
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 数字人民币通道配置
-- ============================================
DROP TABLE IF EXISTS pay_dcep_channel;
CREATE TABLE pay_dcep_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    -- 通道
    channel_code VARCHAR(64) NOT NULL COMMENT 'DCEP-DCBC建行/DCEP-ICBC工行/DCEP-ABC农行',
    channel_name VARCHAR(128) NOT NULL,
    -- 凭证（敏感字段，生产应加密）
    merchant_id VARCHAR(128) NOT NULL,
    app_id VARCHAR(128),
    app_secret VARCHAR(256),
    private_key_path VARCHAR(500),
    public_key_path VARCHAR(500),
    -- API 端点
    api_base_url VARCHAR(500) NOT NULL,
    notify_url VARCHAR(500),
    -- 环境
    env_mode VARCHAR(16) DEFAULT 'SANDBOX' COMMENT 'SANDBOX/MOCK/PROD',
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    -- 健康
    last_health_at DATETIME,
    last_health_msg VARCHAR(255),
    -- 限流
    rate_limit_per_min INT DEFAULT 60,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, channel_code),
    INDEX idx_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人民币通道';

-- ============================================
-- 2. 数字人民币订单
-- ============================================
DROP TABLE IF EXISTS pay_dcep_order;
CREATE TABLE pay_dcep_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT,
    -- 业务关联
    biz_order_id BIGINT NOT NULL,
    biz_order_no VARCHAR(64) NOT NULL,
    -- 数字人民币
    dcep_order_no VARCHAR(64) NOT NULL COMMENT 'DCEP 订单号',
    prepay_id VARCHAR(128) COMMENT 'DCEP 预支付 ID',
    transaction_id VARCHAR(128) COMMENT 'DCEP 交易流水',
    -- 金额（分）
    amount_cents BIGINT NOT NULL,
    -- 通道
    channel_id BIGINT,
    channel_code VARCHAR(64),
    -- 钱包
    wallet_type VARCHAR(32) DEFAULT 'PERSONAL' COMMENT 'PERSONAL个人/MERCHANT对公',
    payer_phone VARCHAR(20),
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1待支付 2已支付 3已关闭 4已退款 5失败',
    -- 时间
    prepay_at DATETIME,
    paid_at DATETIME,
    close_at DATETIME,
    refund_at DATETIME,
    expire_at DATETIME,
    -- 错误
    error_code VARCHAR(64),
    error_msg VARCHAR(500),
    -- 回调原文
    notify_raw TEXT,
    notify_count INT DEFAULT 0,
    -- 操作
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_dcep_no (tenant_id, dcep_order_no),
    UNIQUE KEY uk_biz_order (biz_order_id),
    INDEX idx_status (status, create_time),
    INDEX idx_channel (channel_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人民币订单';

-- ============================================
-- 3. DCEP 回调日志
-- ============================================
DROP TABLE IF EXISTS pay_dcep_notify;
CREATE TABLE pay_dcep_notify (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    dcep_order_id BIGINT NOT NULL,
    -- 回调
    notify_id VARCHAR(64),
    event_type VARCHAR(32) COMMENT 'PAY_SUCCESS/REFUND_SUCCESS/...',
    -- 内容
    payload TEXT,
    -- 签名校验
    sign_verified TINYINT DEFAULT 0,
    sign_algo VARCHAR(32),
    -- 处理
    processed TINYINT DEFAULT 0,
    process_msg VARCHAR(500),
    -- 时间
    notify_at DATETIME,
    process_at DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (dcep_order_id, processed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DCEP 回调日志';

-- ============================================
-- 4. 演示通道（MOCK）
-- ============================================
INSERT IGNORE INTO pay_dcep_channel
(tenant_id, brand_id, channel_code, channel_name, merchant_id, app_id, app_secret,
 api_base_url, notify_url, env_mode, status, rate_limit_per_min, operator_name)
VALUES
(1, 1, 'DCEP-DCBC-MOCK', '建行 DCEP (Mock)', 'MCH-2026-DEMO-001',
 'app-demo', 'secret-demo',
 'https://api-mock.example.com/dcep',
 'https://your.domain/api/dcep/notify',
 'MOCK', 1, 60, 'system');

-- ============================================
-- 5. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pay:dcep', '数字人民币', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pay:channel') t LIMIT 1), 10;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN','FINANCE')
  AND p.permission_code = 'pay:dcep';

-- ============================================
-- 6. 验证
-- ============================================
SELECT 'pay_dcep_channel' tbl, COUNT(*) cnt FROM pay_dcep_channel WHERE deleted=0
UNION ALL SELECT 'pay_dcep_order', COUNT(*) FROM pay_dcep_order WHERE deleted=0
UNION ALL SELECT 'pay_dcep_notify', COUNT(*) FROM pay_dcep_notify;