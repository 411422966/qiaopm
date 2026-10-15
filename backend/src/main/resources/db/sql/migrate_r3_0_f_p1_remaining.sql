-- ============================================
-- R3.0-F P1 剩余改进：
--  1. 总部统一下发（mkt_dispatch*）
--  2. 智能排班（schedule_suggestion）
--  3. 需求预测（biz_forecast_*）
--  4. 推荐算法（crm_recommend_log）
-- 2026-08-14
-- ============================================

-- ============================================
-- 1. 总部统一下发
-- ============================================
DROP TABLE IF EXISTS mkt_dispatch;
CREATE TABLE mkt_dispatch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       NOT NULL COMMENT '下发品牌',
    dispatch_no     VARCHAR(64)  NOT NULL COMMENT '下发单号',
    dispatch_type   VARCHAR(32)  NOT NULL COMMENT 'COUPON优惠券/PACKAGE套餐/DISCOUNT折扣/ACTIVITY活动',
    title           VARCHAR(255) NOT NULL,
    payload         TEXT         DEFAULT NULL COMMENT 'JSON：原对象 ID/参数/规则',
    target_type     VARCHAR(32)  DEFAULT 'SPECIFIC' COMMENT 'ALL全部/BRAND品牌级/SPECIFIC指定门店',
    target_store_ids VARCHAR(2000) DEFAULT NULL COMMENT 'JSON 数组',
    status          VARCHAR(16)  DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/EXECUTING/DONE/CANCELLED',
    plan_start_time DATETIME     DEFAULT NULL,
    plan_end_time   DATETIME     DEFAULT NULL,
    published_at    DATETIME     DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_no (tenant_id, dispatch_no),
    INDEX idx_brand (brand_id, status, deleted),
    INDEX idx_type (dispatch_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='总部统一下发策略';

DROP TABLE IF EXISTS mkt_dispatch_target;
CREATE TABLE mkt_dispatch_target (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    dispatch_id     BIGINT       NOT NULL,
    store_id        BIGINT       NOT NULL,
    store_name      VARCHAR(64)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED/EXECUTED',
    accepted_at     DATETIME     DEFAULT NULL,
    rejected_reason VARCHAR(500) DEFAULT NULL,
    executed_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_dispatch_store (dispatch_id, store_id, deleted),
    INDEX idx_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下发目标门店';

-- ============================================
-- 2. 智能排班：建议结果
-- ============================================
DROP TABLE IF EXISTS schedule_suggestion;
CREATE TABLE schedule_suggestion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       NOT NULL,
    work_date       DATE         NOT NULL,
    employee_id     BIGINT       NOT NULL,
    employee_name   VARCHAR(64)  DEFAULT NULL,
    shift_code      VARCHAR(16)  DEFAULT 'DAY' COMMENT 'DAY/MIDDLE/NIGHT',
    start_time      TIME         DEFAULT NULL,
    end_time        TIME         DEFAULT NULL,
    expected_load   INT          DEFAULT 0 COMMENT '预测当班客流 0-100',
    confidence      DECIMAL(5,2) DEFAULT 0.00 COMMENT '0-1',
    reason          VARCHAR(500) DEFAULT NULL COMMENT '推荐理由（JSON）',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/EXPIRED',
    accepted_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_store_date (store_id, work_date, status),
    INDEX idx_employee (employee_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 排班建议';

-- ============================================
-- 3. 需求预测
-- ============================================
DROP TABLE IF EXISTS biz_forecast_demand;
CREATE TABLE biz_forecast_demand (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       NOT NULL,
    forecast_date   DATE         NOT NULL,
    forecast_type   VARCHAR(32)  DEFAULT 'BOOKING' COMMENT 'BOOKING/ORDER/STOCK',
    target_id       BIGINT       DEFAULT NULL COMMENT '服务/商品/资源 ID',
    target_name     VARCHAR(128) DEFAULT NULL,
    predicted_value DECIMAL(18,2) DEFAULT 0,
    actual_value    DECIMAL(18,2) DEFAULT 0,
    error_rate      DECIMAL(5,2) DEFAULT 0,
    model_version   VARCHAR(32)  DEFAULT 'BASE-1',
    confidence      DECIMAL(5,2) DEFAULT 0.00,
    operator_name   VARCHAR(64)  DEFAULT 'system',
    forecast_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store_date (store_id, forecast_date, forecast_type),
    INDEX idx_target (target_id, forecast_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求预测';

-- ============================================
-- 4. 推荐算法日志
-- ============================================
DROP TABLE IF EXISTS crm_recommend_log;
CREATE TABLE crm_recommend_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    member_id       BIGINT       NOT NULL,
    recommend_type  VARCHAR(32)  NOT NULL COMMENT 'COUPON/PACKAGE/SERVICE/PUNCH/IMAGE',
    target_id       BIGINT       DEFAULT NULL,
    target_name     VARCHAR(255) DEFAULT NULL,
    score           DECIMAL(5,4) DEFAULT 0,
    reason          VARCHAR(500) DEFAULT NULL,
    channel         VARCHAR(32)  DEFAULT 'INTERNAL',
    shown           TINYINT      DEFAULT 0,
    clicked         TINYINT      DEFAULT 0,
    converted       TINYINT      DEFAULT 0,
    exposed_at      DATETIME     DEFAULT NULL,
    clicked_at      DATETIME     DEFAULT NULL,
    converted_at    DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id, recommend_type, create_time),
    INDEX idx_type (recommend_type, converted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐结果日志';

-- ============================================
-- 权限
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:dispatch', '总部下发', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:channel') t LIMIT 1), 90;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'intel:forecast', '需求预测', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='intel:ltv') t LIMIT 1), 50;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'intel:recommend', '智能推荐', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='intel:ltv') t LIMIT 1), 51;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('mkt:dispatch','intel:forecast','intel:recommend');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM mkt_dispatch WHERE deleted=0) AS dispatch_count,
    (SELECT COUNT(*) FROM mkt_dispatch_target WHERE deleted=0) AS dispatch_target,
    (SELECT COUNT(*) FROM schedule_suggestion WHERE deleted=0) AS suggestions,
    (SELECT COUNT(*) FROM biz_forecast_demand) AS forecasts,
    (SELECT COUNT(*) FROM crm_recommend_log) AS recommend_logs;