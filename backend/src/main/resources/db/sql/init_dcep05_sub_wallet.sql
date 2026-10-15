-- ================================================================
-- DCEP-05 数字人民币子钱包独立账本模型
-- 建表时间：2026-09-04
-- ================================================================

-- 1. 子钱包主表
DROP TABLE IF EXISTS biz_dcep_sub_wallet;
CREATE TABLE biz_dcep_sub_wallet (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT COMMENT '租户ID',
    store_id        BIGINT COMMENT '所属门店（null=总部统一账户）',
    parent_wallet_id BIGINT NOT NULL COMMENT '所属主钱包ID',
    parent_wallet_type VARCHAR(20) COMMENT '主钱包类型: hardware/soft',
    sub_wallet_code VARCHAR(50) NOT NULL COMMENT '子钱包编号（全局唯一）',
    sub_wallet_name VARCHAR(100) NOT NULL COMMENT '子钱包名称',
    wallet_type     VARCHAR(30) NOT NULL COMMENT '子钱包类型: store_fund/product_fund/project_fund/brand_share/settlement',
    currency        VARCHAR(10) NOT NULL DEFAULT 'CNY',
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '独立账户余额',
    total_in        DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '累计转入',
    total_out       DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '累计转出',
    frozen_balance  DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额',
    available_balance DECIMAL(15,2) GENERATED ALWAYS AS (balance - frozen_balance) STORED COMMENT '可用余额（计算列）',
    max_balance     DECIMAL(15,2) DEFAULT NULL COMMENT '单钱包余额上限（null=无上限）',
    min_balance     DECIMAL(15,2) DEFAULT 0.00 COMMENT '保底余额',
    transfer_in_ratio DECIMAL(5,4) DEFAULT NULL COMMENT '转入手续费率（null=免手续费）',
    transfer_out_ratio DECIMAL(5,4) DEFAULT NULL COMMENT '转出手续费率',
    auto_sweep_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '自动归集: 0-否 1-是',
    sweep_threshold  DECIMAL(15,2) DEFAULT NULL COMMENT '自动归集触发阈值',
    sweep_target_id BIGINT DEFAULT NULL COMMENT '自动归集目标子钱包ID',
    settlement_mode VARCHAR(20) DEFAULT NULL COMMENT '结算模式: daily/weekly/monthly',
    status          VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态: active/frozen/closed',
    priority        INT DEFAULT 0 COMMENT '优先级（用于自动归集顺序）',
    tags            VARCHAR(500) DEFAULT NULL COMMENT '标签JSON: [\"美甲\",\"储值卡\"]',
    remark          VARCHAR(500) DEFAULT NULL,
    activated_time  DATETIME DEFAULT NULL,
    closed_time     DATETIME DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sub_wallet_code (sub_wallet_code),
    INDEX idx_parent_wallet (parent_wallet_id),
    INDEX idx_tenant_store (tenant_id, store_id),
    INDEX idx_wallet_type (wallet_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数字人民币子钱包（独立账本）';

-- 2. 子钱包账本流水表（不可篡改的复式记账）
DROP TABLE IF EXISTS biz_dcep_sub_wallet_ledger;
CREATE TABLE biz_dcep_sub_wallet_ledger (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT COMMENT '租户ID',
    sub_wallet_id       BIGINT NOT NULL COMMENT '子钱包ID',
    ledger_no           VARCHAR(50) NOT NULL COMMENT '账本编号（流水号）',
    tx_type             VARCHAR(20) NOT NULL COMMENT '交易类型: transfer_in/transfer_out/consume/refund/freeze/unfreeze/settle/adjust',
    amount              DECIMAL(15,2) NOT NULL COMMENT '交易金额（正数）',
    balance_before      DECIMAL(15,2) NOT NULL COMMENT '变动前余额',
    balance_after       DECIMAL(15,2) NOT NULL COMMENT '变动后余额',
    fee                 DECIMAL(15,2) DEFAULT 0.00 COMMENT '手续费',
    opposite_wallet_id  BIGINT DEFAULT NULL COMMENT '对手子钱包ID（转账时）',
    opposite_wallet_name VARCHAR(100) DEFAULT NULL COMMENT '对手钱包名称',
    order_no            VARCHAR(50) DEFAULT NULL COMMENT '关联订单号',
    merchant_id         VARCHAR(50) DEFAULT NULL COMMENT '商户号',
    merchant_name       VARCHAR(100) DEFAULT NULL COMMENT '商户名称',
    operator_id         BIGINT DEFAULT NULL COMMENT '操作人',
    operator_name       VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
    remark              VARCHAR(500) DEFAULT NULL,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sub_wallet (sub_wallet_id),
    INDEX idx_ledger_no (ledger_no),
    INDEX idx_tx_type (tx_type),
    INDEX idx_order_no (order_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='子钱包独立账本流水（复式记账）';

-- 3. 示例数据
INSERT INTO biz_dcep_sub_wallet (tenant_id, store_id, parent_wallet_id, parent_wallet_type, sub_wallet_code, sub_wallet_name, wallet_type, balance, total_in, status, tags, activated_time, deleted, create_time)
VALUES
    (1, NULL, 1, 'hardware', 'SW-HQ-001', '总部资金归集账户', 'store_fund', 5000.00, 5000.00, 'active', '["总部","归集"]', NOW(), 0, NOW()),
    (1, 1, 1, 'hardware', 'SW-STORE1-001', '门店一独立账户', 'store_fund', 2000.00, 2000.00, 'active', '["美甲","储值"]', NOW(), 0, NOW()),
    (1, 2, 1, 'hardware', 'SW-STORE2-001', '门店二独立账户', 'store_fund', 1500.00, 1500.00, 'active', '["美容","产品"]', NOW(), 0, NOW()),
    (1, NULL, 1, 'hardware', 'SW-BRAND-001', '品牌分账账户', 'brand_share', 3000.00, 3000.00, 'active', '["分账","结算"]', NOW(), 0, NOW());
