-- ============================================
-- R3.0-H P2.3 跨仓预警
--  1. wms_warehouse_threshold（仓库级安全阈值，可覆盖默认）
--  2. wms_cross_warehouse_alert（跨仓预警工单）
--  3. wms_transfer_suggestion（智能调拨建议）
--  4. wms_expiry_alert（跨仓过期预警）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS wms_warehouse_threshold;
CREATE TABLE wms_warehouse_threshold (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    warehouse_id    BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    min_stock       INT          DEFAULT 0 COMMENT '安全库存下限',
    max_stock       INT          DEFAULT 999999 COMMENT '安全库存上限',
    safety_days     INT          DEFAULT 7 COMMENT '安全库存天数',
    enabled         TINYINT      DEFAULT 1,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wh_product (tenant_id, warehouse_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库级阈值';

DROP TABLE IF EXISTS wms_cross_warehouse_alert;
CREATE TABLE wms_cross_warehouse_alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    product_id      BIGINT       NOT NULL,
    product_name    VARCHAR(255) DEFAULT NULL,
    from_warehouse_id BIGINT     DEFAULT NULL,
    from_warehouse_name VARCHAR(128) DEFAULT NULL,
    from_quantity   INT          DEFAULT 0,
    to_warehouse_id BIGINT       DEFAULT NULL,
    to_warehouse_name VARCHAR(128) DEFAULT NULL,
    to_quantity     INT          DEFAULT 0,
    alert_type      VARCHAR(32)  NOT NULL COMMENT 'IMBALANCE / OVERLOAD / UNDERLOAD / EXPIRY / TRANSFER_GAP',
    severity        VARCHAR(16)  DEFAULT 'MEDIUM' COMMENT 'HIGH / MEDIUM / LOW',
    recommended_qty INT          DEFAULT 0 COMMENT '建议调拨数量',
    alert_reason    VARCHAR(500) DEFAULT NULL,
    suggested_action VARCHAR(500) DEFAULT NULL,
    status          INT          DEFAULT 1 COMMENT '1=待处理 / 2=已生成调拨单 / 3=已忽略 / 4=已解决',
    related_transfer_id BIGINT   DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    handle_remark   VARCHAR(500) DEFAULT NULL,
    handled_at      DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_tenant_type (tenant_id, alert_type, status),
    INDEX idx_tenant_severity (tenant_id, severity, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨仓预警';

DROP TABLE IF EXISTS wms_transfer_suggestion;
CREATE TABLE wms_transfer_suggestion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    product_id      BIGINT       NOT NULL,
    product_name    VARCHAR(255) DEFAULT NULL,
    from_warehouse_id BIGINT     NOT NULL,
    from_warehouse_name VARCHAR(128) DEFAULT NULL,
    to_warehouse_id BIGINT       NOT NULL,
    to_warehouse_name VARCHAR(128) DEFAULT NULL,
    suggested_qty   INT          NOT NULL,
    priority        VARCHAR(16)  DEFAULT 'MEDIUM' COMMENT 'HIGH / MEDIUM / LOW',
    reason          VARCHAR(500) DEFAULT NULL,
    algorithm_version VARCHAR(32) DEFAULT 'BALANCE-V1',
    accepted        TINYINT      DEFAULT 0 COMMENT '是否被运营采纳',
    accepted_at     DATETIME     DEFAULT NULL,
    accepted_by     VARCHAR(64)  DEFAULT NULL,
    result_transfer_id BIGINT    DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_tenant_product (tenant_id, product_id),
    INDEX idx_accepted (accepted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能调拨建议';

DROP TABLE IF EXISTS wms_expiry_alert;
CREATE TABLE wms_expiry_alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    warehouse_id    BIGINT       NOT NULL,
    warehouse_name  VARCHAR(128) DEFAULT NULL,
    product_id      BIGINT       NOT NULL,
    product_name    VARCHAR(255) DEFAULT NULL,
    batch_no        VARCHAR(64)  DEFAULT NULL,
    quantity        INT          DEFAULT 0,
    expiry_date     DATE         DEFAULT NULL,
    days_remaining  INT          DEFAULT NULL,
    alert_level     VARCHAR(16)  NOT NULL COMMENT 'EXPIRED / EXPIRING_30 / EXPIRING_60 / EXPIRING_90',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_level (tenant_id, alert_level),
    INDEX idx_expiry (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨仓过期预警';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM wms_warehouse_threshold) AS threshold_count,
    (SELECT COUNT(*) FROM wms_cross_warehouse_alert) AS alert_count,
    (SELECT COUNT(*) FROM wms_transfer_suggestion) AS sugg_count,
    (SELECT COUNT(*) FROM wms_expiry_alert) AS expiry_count;