-- ============================================
-- Stripe/Square/PayPal海外支付接入
-- P1-004：海外支付
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 海外支付网关
-- ============================================
DROP TABLE IF EXISTS biz_overseas_payment_gateway;
CREATE TABLE biz_overseas_payment_gateway (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    gateway_code VARCHAR(32) NOT NULL COMMENT '网关编码',
    gateway_name VARCHAR(128) COMMENT '网关名称',
    api_base_url VARCHAR(256) COMMENT 'API Base URL',
    publishable_key VARCHAR(512) COMMENT 'Publishable Key',
    secret_key VARCHAR(512) COMMENT 'Secret Key',
    webhook_secret VARCHAR(512) COMMENT 'Webhook Secret',
    merchant_id VARCHAR(128) COMMENT '商户ID',
    account_id VARCHAR(128) COMMENT '账户ID',
    supported_currencies VARCHAR(500) COMMENT '支持货币JSON',
    payment_methods VARCHAR(500) COMMENT '支付方式JSON',
    fee_rate DECIMAL(5,4) DEFAULT 0 COMMENT '手续费率',
    fixed_fee DECIMAL(15,2) DEFAULT 0 COMMENT '固定费用',
    max_amount_per_transaction DECIMAL(15,2) DEFAULT 0 COMMENT '单笔限额',
    max_amount_per_day DECIMAL(15,2) DEFAULT 0 COMMENT '单日限额',
    environment VARCHAR(16) DEFAULT 'sandbox' COMMENT '环境',
    api_version VARCHAR(32) COMMENT 'API版本',
    country_code VARCHAR(8) COMMENT '国家代码',
    status TINYINT DEFAULT 1 COMMENT '状态',
    health_status VARCHAR(16) DEFAULT 'unknown' COMMENT '健康状态',
    last_health_check DATETIME COMMENT '最新检查时间',
    metadata TEXT COMMENT '元数据JSON',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_gateway_code (gateway_code),
    INDEX idx_status (status),
    INDEX idx_environment (environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='海外支付网关';

-- ============================================
-- 2. 海外支付交易
-- ============================================
DROP TABLE IF EXISTS biz_overseas_payment;
CREATE TABLE biz_overseas_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    order_id BIGINT COMMENT '业务订单ID',
    order_type VARCHAR(32) DEFAULT 'service_order' COMMENT '订单类型',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    member_id BIGINT COMMENT '客户ID',
    member_name VARCHAR(128) COMMENT '客户姓名',
    member_email VARCHAR(128) COMMENT '客户邮箱',
    gateway_code VARCHAR(32) NOT NULL COMMENT '网关编码',
    gateway_intent_id VARCHAR(128) COMMENT '网关支付意图ID',
    gateway_payment_id VARCHAR(128) COMMENT '网关支付ID',
    gateway_client_secret VARCHAR(256) COMMENT '网关客户端密钥',
    amount DECIMAL(15,2) NOT NULL COMMENT '交易金额',
    currency VARCHAR(8) DEFAULT 'USD' COMMENT '货币',
    fee DECIMAL(15,2) DEFAULT 0 COMMENT '手续费',
    net_amount DECIMAL(15,2) DEFAULT 0 COMMENT '实际到账',
    payment_method VARCHAR(32) COMMENT '支付方式',
    payment_method_detail VARCHAR(256) COMMENT '支付方式详情',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态',
    failure_message VARCHAR(500) COMMENT '失败原因',
    refund_amount DECIMAL(15,2) DEFAULT 0 COMMENT '退款金额',
    refund_reason VARCHAR(500) COMMENT '退款原因',
    refund_time DATETIME COMMENT '退款时间',
    paid_at DATETIME COMMENT '支付确认时间',
    source VARCHAR(32) DEFAULT 'web' COMMENT '支付来源',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    device_fingerprint VARCHAR(128) COMMENT '设备指纹',
    three_ds_result VARCHAR(64) COMMENT '3DS验证',
    fraud_score DECIMAL(3,2) COMMENT '防欺诈评分',
    country_code VARCHAR(8) COMMENT '国家',
    metadata TEXT COMMENT '扩展数据JSON',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_member_id (member_id),
    INDEX idx_gateway_code (gateway_code),
    INDEX idx_status (status),
    INDEX idx_intent_id (gateway_intent_id),
    INDEX idx_payment_id (gateway_payment_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='海外支付交易';

-- ============================================
-- 3. Webhook 日志
-- ============================================
DROP TABLE IF EXISTS biz_payment_webhook_log;
CREATE TABLE biz_payment_webhook_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    gateway_code VARCHAR(32) NOT NULL COMMENT '网关编码',
    event_type VARCHAR(64) COMMENT '事件类型',
    gateway_event_id VARCHAR(128) COMMENT '网关事件ID',
    gateway_payment_id VARCHAR(128) COMMENT '网关支付ID',
    payload TEXT COMMENT '事件载荷',
    signature VARCHAR(512) COMMENT '签名',
    verify_status TINYINT DEFAULT 0 COMMENT '验证状态',
    process_status VARCHAR(16) DEFAULT 'pending' COMMENT '处理状态',
    process_time DATETIME COMMENT '处理时间',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_message VARCHAR(1000) COMMENT '错误信息',
    receive_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    source_ip VARCHAR(64) COMMENT '源IP',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_id (gateway_event_id),
    INDEX idx_gateway_code (gateway_code),
    INDEX idx_process_status (process_status),
    INDEX idx_receive_time (receive_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付Webhook日志';

-- ============================================
-- 4. 支付路由规则
-- ============================================
DROP TABLE IF EXISTS biz_payment_route_rule;
CREATE TABLE biz_payment_route_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    priority INT DEFAULT 5 COMMENT '优先级',
    currency VARCHAR(8) COMMENT '货币',
    country_code VARCHAR(8) COMMENT '国家',
    min_amount DECIMAL(15,2) DEFAULT 0 COMMENT '最小金额',
    max_amount DECIMAL(15,2) DEFAULT 0 COMMENT '最大金额',
    payment_method VARCHAR(32) COMMENT '支付方式',
    gateway_code VARCHAR(32) NOT NULL COMMENT '目标网关',
    fee_rate DECIMAL(5,4) DEFAULT 0 COMMENT '费率',
    priority_strategy VARCHAR(16) DEFAULT 'cost' COMMENT '策略',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_currency (currency),
    INDEX idx_country (country_code),
    INDEX idx_status (status),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付路由规则';

-- ============================================
-- 5. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('payment:overseas:gateway', '海外支付网关', 'payment', 'menu', 1, 0, NOW()),
    ('payment:overseas:transaction', '海外支付交易', 'payment', 'menu', 1, 0, NOW()),
    ('payment:overseas:route', '支付路由规则', 'payment', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 6. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/payment' AND deleted = 0 LIMIT 1),
    'PaymentOverseas', '/payment/overseas', '海外支付', 'CreditCard', 5, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/payment/overseas' AND deleted = 0
);

-- ============================================
-- 7. 示例数据
-- ============================================
INSERT INTO biz_overseas_payment_gateway (gateway_code, gateway_name, api_base_url, merchant_id, fee_rate, fixed_fee, supported_currencies, payment_methods, environment, status, deleted, create_time)
VALUES
    ('stripe', 'Stripe (US)', 'https://api.stripe.com', 'acct_demo', 0.029, 0.30, '["USD","EUR","GBP","CAD","AUD"]', '["card","apple_pay","google_pay"]', 'sandbox', 1, 0, NOW()),
    ('square', 'Square (US)', 'https://connect.squareup.com', 'sq_demo', 0.026, 0.10, '["USD","CAD","GBP","JPY","AUD"]', '["card","apple_pay","google_pay"]', 'sandbox', 1, 0, NOW()),
    ('paypal', 'PayPal (Global)', 'https://api-m.paypal.com', 'paypal_demo', 0.0349, 0.49, '["USD","EUR","GBP","JPY","CAD","AUD"]', '["paypal","card"]', 'sandbox', 1, 0, NOW());

INSERT INTO biz_payment_route_rule (rule_name, priority, currency, country_code, payment_method, gateway_code, fee_rate, priority_strategy, status, deleted, create_time)
VALUES
    ('北美信用卡优先', 9, 'USD', 'US', 'card', 'stripe', 0.029, 'cost', 1, 0, NOW()),
    ('欧洲iDEAL优先', 8, 'EUR', 'NL', 'ideal', 'stripe', 0.029, 'coverage', 1, 0, NOW()),
    ('PayPal全球通用', 7, 'USD', NULL, 'paypal', 'paypal', 0.0349, 'coverage', 1, 0, NOW()),
    ('英国费率优先', 6, 'GBP', 'GB', 'card', 'square', 0.026, 'cost', 1, 0, NOW());