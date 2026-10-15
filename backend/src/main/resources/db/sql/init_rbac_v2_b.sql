-- =============================================
-- R2.0-B：金额、库存、次数并发与幂等底座
-- =============================================
USE meiyetec;

-- =============================================
-- 1. 幂等记录表（sys_idempotency_record）
-- =============================================
DROP TABLE IF EXISTS sys_idempotency_record;
CREATE TABLE sys_idempotency_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键（前端或第三方传入）',
    biz_type VARCHAR(64) NOT NULL COMMENT '业务类型：wallet.recharge / wallet.consume / wallet.refund / package.purchase / stock.outbound / pos.pay / pos.refund',
    biz_id BIGINT DEFAULT NULL COMMENT '业务主键（首次成功落库后回填）',
    request_hash VARCHAR(64) DEFAULT NULL COMMENT '请求体摘要（SHA-256），用于检测同一 key 不同 body 的冲突',
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING / SUCCESS / FAILED',
    response_code INT DEFAULT NULL COMMENT '落库时的 BizCode.code',
    response_message VARCHAR(512) DEFAULT NULL COMMENT '落库时的消息',
    response_data MEDIUMTEXT DEFAULT NULL COMMENT '落库时的 response.data 序列化',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    expired_at DATETIME DEFAULT NULL COMMENT '过期时间，过期后可被复用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tenant_key_biz (tenant_id, idempotency_key, biz_type),
    INDEX idx_biz_id (biz_id),
    INDEX idx_expired_at (expired_at),
    INDEX idx_biz_type (biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录表';

-- =============================================
-- 2. 钱包表加 version（乐观锁）
-- =============================================
ALTER TABLE crm_wallet
    ADD COLUMN version INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号' AFTER frozen_amount;

-- =============================================
-- 3. 套餐订单表加 version（乐观锁，避免超扣）
-- =============================================
ALTER TABLE crm_package_order
    ADD COLUMN version INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号' AFTER status;

-- =============================================
-- 4. 库存表加 version（乐观锁，与 quantity>=? 双保险）
-- =============================================
ALTER TABLE stock_inventory
    ADD COLUMN version INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号' AFTER quantity;

-- =============================================
-- 5. 幂等记录保留默认 24h（可由 sys_config 覆盖）
-- =============================================
INSERT IGNORE INTO sys_config (config_key, config_value, config_group, description)
VALUES ('idempotency.retention-hours', '24', 'system', '幂等记录保留小时数');

-- =============================================
-- 6. 验证
-- =============================================
SELECT '=== sys_idempotency_record ===' AS '';
SHOW CREATE TABLE sys_idempotency_record;

SELECT '=== crm_wallet version 字段 ===' AS '';
SHOW COLUMNS FROM crm_wallet LIKE 'version';
SELECT '=== crm_package_order version 字段 ===' AS '';
SHOW COLUMNS FROM crm_package_order LIKE 'version';
SELECT '=== stock_inventory version 字段 ===' AS '';
SHOW COLUMNS FROM stock_inventory LIKE 'version';
