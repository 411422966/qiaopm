-- ================================================================
-- M12-05 业务策略下发：价格 / 营销活动统一下发
-- 建表时间：2026-09-06
-- ================================================================
--
-- 设计要点：
--  - mkt_dispatch 已存在（R3.0-F P1-3），本迁移：
--    a) 增加 dispatch_subtype 字段（PRICING_RULE / ACTIVITY / COUPON_BATCH / EXPERIMENT）
--    b) 增加 source_id（源策略 ID）+ source_version（版本号）
--  - mkt_dispatch_log：每条 dispatch 的下发执行日志（按门店）
--  - mkt_dispatch_target.status 已有 PENDING/ACCEPTED/REJECTED/EXECUTED
--  - 所有表 tenant_id 必填，强租户隔离
--  - 幂等键：uk_dispatch_target (dispatch_id, store_id) — 同一 dispatch 同店只一条
-- ================================================================

-- 1. mkt_dispatch 增加字段
ALTER TABLE mkt_dispatch
    ADD COLUMN IF NOT EXISTS dispatch_subtype VARCHAR(30) DEFAULT NULL COMMENT 'PRICING_RULE/ACTIVITY/COUPON_BATCH/EXPERIMENT',
    ADD COLUMN IF NOT EXISTS source_id BIGINT DEFAULT NULL COMMENT '源策略ID（价格规则/活动/批次/实验）',
    ADD COLUMN IF NOT EXISTS source_version VARCHAR(20) DEFAULT NULL COMMENT '源策略版本号',
    ADD COLUMN IF NOT EXISTS published_version INT NOT NULL DEFAULT 1 COMMENT '下发版本号（每次重新发布 +1）',
    ADD COLUMN IF NOT EXISTS success_count INT NOT NULL DEFAULT 0 COMMENT '成功下发门店数',
    ADD COLUMN IF NOT EXISTS fail_count INT NOT NULL DEFAULT 0 COMMENT '失败门店数',
    ADD COLUMN IF NOT EXISTS total_count INT NOT NULL DEFAULT 0 COMMENT '目标门店总数';

-- 2. 下发执行日志（每条 dispatch × 每个目标门店）
DROP TABLE IF EXISTS m12_dispatch_log;
CREATE TABLE m12_dispatch_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    dispatch_id     BIGINT NOT NULL COMMENT '下发ID（mkt_dispatch.id）',
    store_id        BIGINT NOT NULL COMMENT '目标门店ID',
    store_name      VARCHAR(100) DEFAULT NULL COMMENT '门店名称（冗余）',
    -- 下发源信息
    dispatch_type   VARCHAR(30) DEFAULT NULL COMMENT 'COUPON/PACKAGE/DISCOUNT/ACTIVITY/PRICING_RULE',
    dispatch_subtype VARCHAR(30) DEFAULT NULL COMMENT 'PRICING_RULE/ACTIVITY/COUPON_BATCH/EXPERIMENT',
    source_id       BIGINT DEFAULT NULL COMMENT '源策略ID',
    source_version  VARCHAR(20) DEFAULT NULL COMMENT '源策略版本',
    -- 状态机
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/SKIPPED',
    -- 命中结果
    applied_count   INT DEFAULT 0 COMMENT '实际应用门店数（用于 multi-store 批次）',
    error_msg       VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    error_code      VARCHAR(50) DEFAULT NULL,
    -- 审计
    operator_id     BIGINT DEFAULT NULL,
    operator_name   VARCHAR(50) DEFAULT NULL,
    -- 时间戳
    dispatch_started_at  DATETIME DEFAULT NULL COMMENT '下发开始时间',
    dispatch_finished_at DATETIME DEFAULT NULL COMMENT '下发完成时间',
    duration_ms     BIGINT DEFAULT 0 COMMENT '耗时毫秒',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dispatch_store (dispatch_id, store_id),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_dispatch (dispatch_id),
    INDEX idx_store (tenant_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M12-05 下发执行日志（按门店维度）';

-- 3. 索引补全（如已存在则忽略）
CREATE INDEX IF NOT EXISTS idx_mkt_dispatch_subtype ON mkt_dispatch(dispatch_subtype);
CREATE INDEX IF NOT EXISTS idx_mkt_dispatch_status_pub ON mkt_dispatch(status, published_at);
