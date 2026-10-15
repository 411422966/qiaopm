-- ============================================
-- AI-007：A/B 测试深化
--  1. ab_assignment：增加转化追踪字段（供 computeSignificance 卡方检验用）
--  2. ab_experiment：增加 M14 联动字段（pricing_exp_id）+ 显著性实时结果
-- 2026-09-04
-- ============================================

-- 1. ab_assignment：转化追踪
ALTER TABLE ab_assignment
    ADD COLUMN conversion_status VARCHAR(16)   DEFAULT 'RECORDED' COMMENT 'RECORDED / CONVERTED / EXPIRED',
    ADD COLUMN converted_at      DATETIME      DEFAULT NULL,
    ADD COLUMN conversion_amount DECIMAL(12,2) DEFAULT NULL COMMENT '转化金额',
    ADD COLUMN metric_value      DECIMAL(12,4) DEFAULT NULL COMMENT '通用指标值（AOV / GMV）',
    ADD COLUMN metric_source     VARCHAR(64)   DEFAULT NULL COMMENT 'CN_ORDER / GMV / RETENTION',
    ADD INDEX idx_exp_status (exp_key, conversion_status),
    ADD INDEX idx_exp_metric (exp_key, metric_source);

-- 2. ab_experiment：M14 联动 + 实时统计
ALTER TABLE ab_experiment
    ADD COLUMN pricing_exp_id  BIGINT       DEFAULT NULL COMMENT 'M14 定价实验 ID，可空',
    ADD COLUMN chi_square      DOUBLE       DEFAULT NULL COMMENT '卡方统计量',
    ADD COLUMN chi_p_value     DOUBLE       DEFAULT NULL COMMENT 'P-value（0..1）',
    ADD COLUMN chi_df          INT          DEFAULT NULL COMMENT '自由度',
    ADD COLUMN chi_updated_at  DATETIME     DEFAULT NULL,
    ADD COLUMN conclusion      VARCHAR(32)  DEFAULT NULL COMMENT 'INSUFFICIENT_DATA / WIN_VARIANT_X / NO_SIGNIFICANCE / EVEN_DISTRIBUTION',
    ADD COLUMN best_variant    VARCHAR(32)  DEFAULT NULL,
    ADD INDEX idx_pricing_exp (pricing_exp_id);

-- 3. 验证
SELECT
    (SELECT COUNT(*) FROM ab_assignment) AS assign_count,
    (SELECT COUNT(*) FROM ab_experiment) AS exp_count,
    (SELECT COUNT(*) FROM pricing_experiment_result) AS pricing_result_count;
