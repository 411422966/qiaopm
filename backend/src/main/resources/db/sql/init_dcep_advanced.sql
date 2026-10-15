-- ============================================
-- 数字人民币2.0：硬钱包 + 离线支付 + 智能合约
-- P2-002：数字人民币高级功能
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 数字人民币硬钱包
-- ============================================
DROP TABLE IF EXISTS biz_dcep_hardware_wallet;
CREATE TABLE biz_dcep_hardware_wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    member_name VARCHAR(128) COMMENT '客户姓名',
    wallet_name VARCHAR(128) COMMENT '钱包名称',
    wallet_type VARCHAR(32) DEFAULT 'nfc_sim' COMMENT '钱包类型',
    wallet_code VARCHAR(64) NOT NULL COMMENT '钱包编号',
    se_chip_id VARCHAR(128) COMMENT 'SE芯片ID',
    nfc_sim_id VARCHAR(64) COMMENT 'NFC SIM卡号',
    device_model VARCHAR(128) COMMENT '设备型号',
    brand VARCHAR(64) COMMENT '品牌',
    public_key VARCHAR(512) COMMENT '钱包公钥',
    wallet_address VARCHAR(128) COMMENT '钱包地址',
    operator_org VARCHAR(64) DEFAULT '中国工商银行' COMMENT '运营机构',
    balance DECIMAL(15,2) DEFAULT 0 COMMENT '在线余额',
    total_recharge DECIMAL(15,2) DEFAULT 0 COMMENT '累计充值',
    total_consumption DECIMAL(15,2) DEFAULT 0 COMMENT '累计消费',
    max_amount_per_transaction DECIMAL(15,2) DEFAULT 5000 COMMENT '单笔限额',
    max_amount_per_day DECIMAL(15,2) DEFAULT 10000 COMMENT '单日限额',
    daily_remaining DECIMAL(15,2) DEFAULT 10000 COMMENT '单日剩余',
    offline_support TINYINT DEFAULT 0 COMMENT '支持离线',
    offline_balance_limit DECIMAL(15,2) DEFAULT 500 COMMENT '离线额度上限',
    offline_balance DECIMAL(15,2) DEFAULT 0 COMMENT '当前离线余额',
    biometric_enabled TINYINT DEFAULT 0 COMMENT '生物识别',
    pin_set TINYINT DEFAULT 0 COMMENT 'PIN设置',
    last_sync_time DATETIME COMMENT '最后同步时间',
    status VARCHAR(16) DEFAULT 'active' COMMENT '状态',
    activated_time DATETIME COMMENT '激活时间',
    expired_time DATETIME COMMENT '过期时间',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_code (wallet_code),
    INDEX idx_member_id (member_id),
    INDEX idx_wallet_type (wallet_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人民币硬钱包';

-- ============================================
-- 2. 双离线交易
-- ============================================
DROP TABLE IF EXISTS biz_dcep_offline_transaction;
CREATE TABLE biz_dcep_offline_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    wallet_id BIGINT NOT NULL COMMENT '钱包ID',
    wallet_code VARCHAR(64) COMMENT '钱包编号',
    offline_tx_no VARCHAR(64) NOT NULL COMMENT '离线交易号',
    offline_cert VARCHAR(256) COMMENT '离线凭证',
    offline_signature VARCHAR(512) COMMENT '离线签名',
    amount DECIMAL(15,2) NOT NULL COMMENT '交易金额',
    offline_balance DECIMAL(15,2) DEFAULT 0 COMMENT '离线余额',
    tx_type VARCHAR(16) DEFAULT 'consume' COMMENT '交易类型',
    merchant_id VARCHAR(64) COMMENT '商户号',
    merchant_name VARCHAR(128) COMMENT '商户名称',
    order_no VARCHAR(64) COMMENT '关联订单号',
    dual_offline TINYINT DEFAULT 1 COMMENT '双离线',
    sync_status VARCHAR(16) DEFAULT 'pending' COMMENT '同步状态',
    sync_time DATETIME COMMENT '同步时间',
    sync_attempts INT DEFAULT 0 COMMENT '同步尝试次数',
    conflict_detected TINYINT DEFAULT 0 COMMENT '冲突检测',
    conflict_type VARCHAR(32) COMMENT '冲突类型',
    conflict_resolution VARCHAR(500) COMMENT '冲突解决方案',
    anti_double_spend_proof VARCHAR(512) COMMENT '防双花证据',
    offline_tx_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '离线交易时间',
    geo_location VARCHAR(64) COMMENT 'GPS位置',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_offline_tx_no (offline_tx_no),
    INDEX idx_wallet_id (wallet_id),
    INDEX idx_sync_status (sync_status),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_offline_tx_time (offline_tx_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人民币离线交易';

-- ============================================
-- 3. 智能合约
-- ============================================
DROP TABLE IF EXISTS biz_dcep_smart_contract;
CREATE TABLE biz_dcep_smart_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    contract_name VARCHAR(128) NOT NULL COMMENT '合约名称',
    contract_type VARCHAR(32) DEFAULT 'prepaid_card_escrow' COMMENT '合约类型',
    contract_address VARCHAR(128) COMMENT '合约地址',
    contract_code_hash VARCHAR(128) COMMENT '合约代码hash',
    contract_code TEXT COMMENT '合约源代码',
    wallet_id BIGINT COMMENT '关联钱包',
    member_id BIGINT COMMENT '关联客户',
    escrow_amount DECIMAL(15,2) DEFAULT 0 COMMENT '监管金额',
    released_amount DECIMAL(15,2) DEFAULT 0 COMMENT '已释放',
    used_amount DECIMAL(15,2) DEFAULT 0 COMMENT '已使用',
    refunded_amount DECIMAL(15,2) DEFAULT 0 COMMENT '已退还',
    currency VARCHAR(8) DEFAULT 'CNY' COMMENT '货币',
    trigger_conditions TEXT COMMENT '触发条件JSON',
    status VARCHAR(16) DEFAULT 'active' COMMENT '状态',
    activated_time DATETIME COMMENT '激活时间',
    expired_time DATETIME COMMENT '过期时间',
    completed_time DATETIME COMMENT '完成时间',
    auto_execute_count INT DEFAULT 0 COMMENT '自动执行次数',
    manual_execute_count INT DEFAULT 0 COMMENT '手动执行次数',
    event_log TEXT COMMENT '事件日志',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_contract_address (contract_address),
    INDEX idx_contract_type (contract_type),
    INDEX idx_status (status),
    INDEX idx_wallet_id (wallet_id),
    INDEX idx_member_id (member_id),
    INDEX idx_expired_time (expired_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人民币智能合约';

-- ============================================
-- 4. 合约执行日志
-- ============================================
DROP TABLE IF EXISTS biz_dcep_contract_execution_log;
CREATE TABLE biz_dcep_contract_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合约ID',
    contract_name VARCHAR(128) COMMENT '合约名称',
    execute_type VARCHAR(16) DEFAULT 'auto' COMMENT '执行类型',
    action VARCHAR(32) COMMENT '动作',
    amount DECIMAL(15,2) DEFAULT 0 COMMENT '金额',
    status VARCHAR(16) DEFAULT 'success' COMMENT '状态',
    trigger_snapshot TEXT COMMENT '触发条件',
    result VARCHAR(1000) COMMENT '执行结果',
    error_message VARCHAR(1000) COMMENT '错误信息',
    transaction_no VARCHAR(64) COMMENT '关联交易ID',
    block_number BIGINT COMMENT '区块号',
    gas_used BIGINT DEFAULT 0 COMMENT 'Gas消耗',
    execute_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_contract_id (contract_id),
    INDEX idx_status (status),
    INDEX idx_execute_time (execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能合约执行日志';

-- ============================================
-- 5. 防双花证据
-- ============================================
DROP TABLE IF EXISTS biz_dcep_anti_double_spend_proof;
CREATE TABLE biz_dcep_anti_double_spend_proof (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    wallet_id BIGINT NOT NULL COMMENT '钱包ID',
    wallet_code VARCHAR(64) COMMENT '钱包编号',
    tx_no VARCHAR(64) NOT NULL COMMENT '交易号',
    nonce BIGINT COMMENT 'Nonce',
    merkle_root VARCHAR(256) COMMENT 'Merkle根',
    dual_signature VARCHAR(1024) COMMENT '双重签名',
    wallet_proof VARCHAR(512) COMMENT '钱包证明',
    merchant_proof VARCHAR(512) COMMENT '商户证明',
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态',
    verify_time DATETIME COMMENT '验证时间',
    verifier_id VARCHAR(64) COMMENT '验证人',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tx_no (tx_no),
    INDEX idx_wallet_id (wallet_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='防双花证据';

-- ============================================
-- 6. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('dcep:hardware-wallet', '硬钱包管理', 'payment', 'menu', 1, 0, NOW()),
    ('dcep:offline', '离线交易', 'payment', 'menu', 1, 0, NOW()),
    ('dcep:smart-contract', '智能合约', 'payment', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 7. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/payment' AND deleted = 0 LIMIT 1),
    'DcepAdvanced', '/payment/dcep-advanced', '数字人民币2.0', 'Cellphone', 6, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/payment/dcep-advanced' AND deleted = 0
);

-- ============================================
-- 8. 示例数据
-- ============================================
INSERT INTO biz_dcep_hardware_wallet (wallet_name, wallet_type, wallet_code, device_model, brand, operator_org, balance, total_recharge, offline_support, offline_balance, status, member_id, member_name, activated_time, deleted, create_time)
VALUES
    ('华为Mate60硬钱包', 'nfc_sim', 'HW-NFCSIM-A0000001', 'Mate60 Pro', '华为', '中国工商银行', 2000.00, 3000.00, 1, 500.00, 'active', 1001, '张女士', NOW(), 0, NOW()),
    ('Apple Watch SE钱包', 'se_chip', 'HW-SECHIP-B0000001', 'Apple Watch SE', 'Apple', '中国建设银行', 1500.00, 2000.00, 1, 300.00, 'active', 1002, '李女士', NOW(), 0, NOW()),
    ('小米手环7钱包', 'se_chip', 'HW-SECHIP-C0000001', '小米手环7', '小米', '中国银行', 800.00, 1000.00, 1, 200.00, 'active', 1003, '陈女士', NOW(), 0, NOW()),
    ('在线钱包', 'biometric', 'HW-BIO-D0000001', '指纹识别设备', '美业定制', '中国工商银行', 500.00, 500.00, 0, 0.00, 'active', 1004, '王女士', NOW(), 0, NOW());

INSERT INTO biz_dcep_smart_contract (contract_name, contract_type, wallet_id, member_id, escrow_amount, released_amount, expired_time, status, trigger_conditions, deleted, create_time)
VALUES
    ('VIP客户预付卡', 'prepaid_card_escrow', 1, 1001, 5000.00, 0.00, '2026-12-31 23:59:59', 'active', '{"type":"expire","deadline":"2026-12-31"}', 0, NOW()),
    ('储值卡-3000元', 'limited_storage', 2, 1002, 3000.00, 1500.00, '2026-06-30 23:59:59', 'active', '{"type":"expire"}', 0, NOW()),
    ('消费返券合约', 'conditional_coupon', 3, 1003, 0.00, 0.00, '2026-12-31 23:59:59', 'active', '{"type":"spend_threshold","amount":1000}', 0, NOW());