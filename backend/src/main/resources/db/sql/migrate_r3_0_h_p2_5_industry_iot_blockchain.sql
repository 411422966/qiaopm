-- ============================================
-- R3.0-H P2.5 行业SaaS + 智能硬件 + 区块链溯源
--  1. industry_template（行业模板）
--  2. industry_service_relation（行业-服务/商品关联）
--  3. iot_command_dest（IoT 命令下发目标）
--  4. iot_event_dest（IoT 事件上报）
--  5. blockchain_record（区块链存证）
--  6. blockchain_chain_block（区块链区块）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS industry_template;
CREATE TABLE industry_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    industry_code   VARCHAR(32)  NOT NULL COMMENT 'BEAUTY / MEDSPA / DENTAL / HAIR / MATERNITY',
    industry_name   VARCHAR(64)  NOT NULL COMMENT '美业 / 医美 / 口腔 / 美发 / 月子',
    description     VARCHAR(500) DEFAULT NULL,
    default_services TEXT       DEFAULT NULL COMMENT 'JSON 默认服务列表',
    default_products TEXT       DEFAULT NULL COMMENT 'JSON 默认商品列表',
    default_forms   TEXT        DEFAULT NULL COMMENT 'JSON 默认表单（如医美同意书）',
    enabled         TINYINT      DEFAULT 1,
    sort_order      INT          DEFAULT 0,
    icon            VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_code (industry_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行业模板';

DROP TABLE IF EXISTS industry_service_relation;
CREATE TABLE industry_service_relation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    industry_code   VARCHAR(32)  NOT NULL,
    target_type     VARCHAR(32)  NOT NULL COMMENT 'SERVICE / PRODUCT / FORM / ROLE',
    target_code     VARCHAR(128) NOT NULL,
    target_name     VARCHAR(255) DEFAULT NULL,
    required        TINYINT      DEFAULT 1 COMMENT '是否该行业必备',
    sort_order      INT          DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_target (industry_code, target_type, target_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行业-服务/商品关联';

DROP TABLE IF EXISTS iot_command_dest;
CREATE TABLE iot_command_dest (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    device_id       BIGINT       NOT NULL COMMENT '目标设备',
    device_code     VARCHAR(64)  DEFAULT NULL,
    command_type    VARCHAR(64)  NOT NULL COMMENT 'START / STOP / RESET / CALIBRATE / UPGRADE / CUSTOM',
    command_payload TEXT         DEFAULT NULL COMMENT 'JSON 命令负载',
    priority        INT          DEFAULT 5 COMMENT '1-10, 1 最高',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING / SENT / ACK / SUCCESS / FAIL / TIMEOUT',
    sent_at         DATETIME     DEFAULT NULL,
    ack_at          DATETIME     DEFAULT NULL,
    completed_at    DATETIME     DEFAULT NULL,
    response_payload TEXT        DEFAULT NULL COMMENT '设备响应',
    error_message   VARCHAR(500) DEFAULT NULL,
    retry_count     INT          DEFAULT 0,
    max_retry       INT          DEFAULT 3,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_status (device_id, status),
    INDEX idx_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 命令下发';

DROP TABLE IF EXISTS iot_event_dest;
CREATE TABLE iot_event_dest (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    device_id       BIGINT       NOT NULL,
    device_code     VARCHAR(64)  DEFAULT NULL,
    event_type      VARCHAR(64)  NOT NULL COMMENT 'TELEMETRY / ALERT / STATUS / ERROR',
    event_payload   TEXT         DEFAULT NULL COMMENT 'JSON 事件数据',
    severity        VARCHAR(16)  DEFAULT 'INFO' COMMENT 'INFO / WARN / ERROR / CRITICAL',
    occurred_at     DATETIME     DEFAULT NULL,
    received_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    processed       TINYINT      DEFAULT 0,
    processed_at    DATETIME     DEFAULT NULL,
    INDEX idx_device_event (device_id, event_type, occurred_at),
    INDEX idx_processed (processed, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 事件上报';

DROP TABLE IF EXISTS blockchain_record;
CREATE TABLE blockchain_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    record_type     VARCHAR(32)  NOT NULL COMMENT 'PRODUCT / DRUG / DEVICE / ORDER / CONSENT',
    biz_id          BIGINT       NOT NULL COMMENT '业务主键',
    biz_no          VARCHAR(64)  DEFAULT NULL,
    block_hash      VARCHAR(64)  NOT NULL COMMENT '本块哈希',
    prev_hash       VARCHAR(64)  DEFAULT NULL COMMENT '上一块哈希',
    block_index     BIGINT       NOT NULL COMMENT '块序号',
    merkle_root     VARCHAR(64)  DEFAULT NULL,
    payload         TEXT         DEFAULT NULL COMMENT 'JSON 存证内容',
    payload_hash    VARCHAR(64)  NOT NULL COMMENT 'payload 哈希',
    nonce           BIGINT       DEFAULT 0,
    difficulty      INT          DEFAULT 2,
    miner           VARCHAR(64)  DEFAULT 'system',
    consensus       VARCHAR(16)  DEFAULT 'POW' COMMENT 'POW / POS / DPOS',
    signature       VARCHAR(128) DEFAULT NULL,
    verified        TINYINT      DEFAULT 1,
    verify_count    INT          DEFAULT 0,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_block_hash (block_hash),
    INDEX idx_type_biz (record_type, biz_id),
    INDEX idx_block_index (block_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区块链存证';

DROP TABLE IF EXISTS blockchain_chain_block;
CREATE TABLE blockchain_chain_block (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_id        VARCHAR(64)  NOT NULL COMMENT '链标识（meiyetec-main / meiyetec-drug...）',
    block_index     BIGINT       NOT NULL,
    block_hash      VARCHAR(64)  NOT NULL,
    prev_hash       VARCHAR(64)  DEFAULT NULL,
    merkle_root     VARCHAR(64)  DEFAULT NULL,
    tx_count        INT          DEFAULT 0,
    size_bytes      INT          DEFAULT 0,
    difficulty      INT          DEFAULT 2,
    nonce           BIGINT       DEFAULT 0,
    miner           VARCHAR(64)  DEFAULT 'system',
    sealed_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chain_index (chain_id, block_index),
    UNIQUE KEY uk_chain_hash (chain_id, block_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区块链 - 区块';

INSERT IGNORE INTO industry_template (industry_code, industry_name, description, default_services, default_products, enabled, sort_order, icon) VALUES
    ('BEAUTY', '美业', '面部护理、SPA、身体护理',
     '["基础护理","深层清洁","保湿护理","身体SPA"]',
     '["面膜","精华液","身体磨砂"]',
     1, 1, '🌸'),
    ('MEDSPA', '医美', '注射类、激光类、抗衰类',
     '["玻尿酸注射","肉毒素","激光嫩肤","热玛吉"]',
     '["玻尿酸","肉毒素","麻药"]',
     1, 2, '💉'),
    ('DENTAL', '口腔', '种植、正畸、修复',
     '["洁牙","补牙","种植牙","正畸复诊"]',
     '["种植体","牙套","麻药"]',
     1, 3, '🦷'),
    ('HAIR', '美发', '剪发、烫发、染发、护理',
     '["剪发","烫发","染发","头发护理"]',
     '["染发剂","烫发剂","洗发水"]',
     1, 4, '💇'),
    ('MATERNITY', '月子', '产后修复、新生儿护理',
     '["产后开奶","子宫复旧","婴儿SPA","妈妈营养餐"]',
     '["月子餐","催乳产品","婴儿用品"]',
     1, 5, '👶');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM industry_template) AS industry_count,
    (SELECT COUNT(*) FROM iot_command_dest) AS cmd_count,
    (SELECT COUNT(*) FROM iot_event_dest) AS evt_count,
    (SELECT COUNT(*) FROM blockchain_record) AS bc_count,
    (SELECT COUNT(*) FROM blockchain_chain_block) AS block_count;