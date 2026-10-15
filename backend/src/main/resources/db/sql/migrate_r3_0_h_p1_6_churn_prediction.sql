-- ============================================
-- R3.0-H P1.6 流失预测补强
--  1. ai_churn_model（流失预测模型管理）
--  2. crm_churn_prediction（每个会员的流失预测快照）
--  3. crm_churn_alert（流失预警）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS ai_churn_model;
CREATE TABLE ai_churn_model (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    model_code      VARCHAR(64)  NOT NULL COMMENT '模型编码',
    model_name      VARCHAR(128) NOT NULL,
    version         VARCHAR(32)  DEFAULT '1.0.0',
    algorithm       VARCHAR(32)  DEFAULT 'WEIGHTED_RULE' COMMENT 'WEIGHTED_RULE / LOGISTIC / XGBOOST',
    threshold_high  DECIMAL(3,2) DEFAULT 0.70 COMMENT '高风险阈值（≥此值报警）',
    threshold_med   DECIMAL(3,2) DEFAULT 0.40 COMMENT '中风险阈值',
    is_active       TINYINT      DEFAULT 0,
    enabled         TINYINT      DEFAULT 1,
    status          INT          DEFAULT 1,
    description     VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流失预测模型';

DROP TABLE IF EXISTS crm_churn_prediction;
CREATE TABLE crm_churn_prediction (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    brand_id            BIGINT       DEFAULT NULL,
    member_id           BIGINT       NOT NULL,
    -- 输入特征
    total_orders        INT          DEFAULT 0,
    historical_ltv      DECIMAL(12,2) DEFAULT 0,
    avg_order_value     DECIMAL(10,2) DEFAULT 0,
    monthly_freq        DECIMAL(6,2) DEFAULT 0,
    last_purchase_days  INT          DEFAULT 9999,
    tenure_days         INT          DEFAULT 0,
    complaint_count     INT          DEFAULT 0,
    review_avg_rating   DECIMAL(3,2) DEFAULT NULL,
    -- 输出
    churn_score         DECIMAL(5,4) NOT NULL COMMENT '流失概率 [0,1]',
    risk_level          VARCHAR(16)  NOT NULL COMMENT 'HIGH / MEDIUM / LOW / SAFE',
    predicted_lost_date DATE         DEFAULT NULL,
    -- 模型
    model_code          VARCHAR(64)  DEFAULT NULL,
    model_version       VARCHAR(32)  DEFAULT NULL,
    -- 时间
    snapshot_date       DATE         NOT NULL,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_score (tenant_id, churn_score DESC),
    INDEX idx_tenant_risk (tenant_id, risk_level),
    INDEX idx_member (member_id, snapshot_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员流失预测';

DROP TABLE IF EXISTS crm_churn_alert;
CREATE TABLE crm_churn_alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       DEFAULT NULL,
    member_id       BIGINT       NOT NULL,
    member_name     VARCHAR(64)  DEFAULT NULL,
    member_phone    VARCHAR(32)  DEFAULT NULL,
    risk_level      VARCHAR(16)  NOT NULL,
    churn_score     DECIMAL(5,4) NOT NULL,
    predicted_lost_date DATE     DEFAULT NULL,
    -- 建议措施
    alert_reason    VARCHAR(500) DEFAULT NULL,
    suggested_action VARCHAR(500) DEFAULT NULL,
    -- 状态
    status          INT          DEFAULT 1 COMMENT '1=待处理 / 2=已联系 / 3=已挽回 / 4=已流失 / 5=忽略',
    handler_id      BIGINT       DEFAULT NULL,
    handler_name    VARCHAR(64)  DEFAULT NULL,
    handled_at      DATETIME     DEFAULT NULL,
    handle_remark   VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_member (member_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流失预警工单';

-- 默认模型
INSERT IGNORE INTO ai_churn_model
  (tenant_id, model_code, model_name, version, algorithm, threshold_high, threshold_med, is_active, enabled, status, description)
VALUES
  (1, 'CHURN-V1', '加权规则流失预测', '1.0.0', 'WEIGHTED_RULE', 0.70, 0.40, 1, 1, 2,
   '基于 RFM + 投诉 + 评分 的加权规则评分模型');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM ai_churn_model) AS model_count,
    (SELECT COUNT(*) FROM crm_churn_prediction) AS pred_count,
    (SELECT COUNT(*) FROM crm_churn_alert) AS alert_count,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_name='ai_churn_model') AS model_table_exists;