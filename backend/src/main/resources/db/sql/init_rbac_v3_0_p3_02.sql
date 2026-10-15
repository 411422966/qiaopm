-- ============================================
-- P3 · T-P02：LTV 与复购概率
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 会员 LTV 预测快照
-- ============================================
DROP TABLE IF EXISTS crm_ltv_snapshot;
CREATE TABLE crm_ltv_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    member_id BIGINT NOT NULL,
    -- 历史 LTV（已实现）
    historical_ltv DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '历史已实现 LTV（累计消费）',
    -- 预测 LTV
    predicted_ltv_30d DECIMAL(12,2) COMMENT '30 天预测 LTV',
    predicted_ltv_90d DECIMAL(12,2) COMMENT '90 天预测 LTV',
    predicted_ltv_365d DECIMAL(12,2) COMMENT '365 天预测 LTV',
    predicted_ltv_lifetime DECIMAL(12,2) COMMENT '终身 LTV',
    -- 复购概率
    repurchase_prob_30d DECIMAL(5,4) COMMENT '30 天复购概率 0-1',
    repurchase_prob_90d DECIMAL(5,4),
    repurchase_prob_180d DECIMAL(5,4),
    -- 模型
    model_type VARCHAR(32) NOT NULL COMMENT 'BG/NBD/HISTORICAL/ML/RULE',
    model_version VARCHAR(32),
    -- 输入特征
    avg_order_value DECIMAL(12,2) COMMENT '客单价',
    purchase_frequency DECIMAL(8,2) COMMENT '月均消费频次',
    last_purchase_days INT COMMENT '距上次消费天数',
    total_orders INT,
    -- 验证指标
    auc DECIMAL(5,4),
    recall DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    -- 时间
    snapshot_date DATE NOT NULL,
    calc_duration_ms INT,
    operator_name VARCHAR(64) DEFAULT 'system',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id),
    INDEX idx_brand_date (brand_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LTV 预测快照';

-- ============================================
-- 2. LTV 模型版本
-- ============================================
DROP TABLE IF EXISTS ai_ltv_model;
CREATE TABLE ai_ltv_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    model_code VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(32) NOT NULL COMMENT 'BG/NBD/HISTORICAL/ML',
    -- 训练集
    train_start DATE,
    train_end DATE,
    -- 验证指标
    auc DECIMAL(5,4),
    recall DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    -- 模型参数
    params_json TEXT,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1训练中 2已部署 3停用',
    is_active TINYINT DEFAULT 0 COMMENT '是否当前激活',
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, model_code),
    INDEX idx_status (status, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LTV 模型';

-- ============================================
-- 3. 演示模型
-- ============================================
INSERT IGNORE INTO ai_ltv_model (tenant_id, brand_id, model_code, model_name, model_type,
    train_start, train_end, auc, recall, precision_score, status, is_active, enabled, operator_name)
VALUES
(1, 1, 'LTV-BG-NBD-V1', 'LTV BG/NBD v1', 'BG',
    '2025-08-01', '2026-08-01', 0.7845, 0.6230, 0.7180, 2, 1, 1, 'system');

-- ============================================
-- 4. 验证
-- ============================================
SELECT 'crm_ltv_snapshot' tbl, COUNT(*) cnt FROM crm_ltv_snapshot
UNION ALL SELECT 'ai_ltv_model', COUNT(*) FROM ai_ltv_model WHERE deleted=0;