-- ============================================
-- IOT-006：设备联动真实协议 — 2 张表
--  1. iot_device_link      联动关系（主设备 → 从设备）
--  2. iot_device_link_log  每次联动执行日志
-- 2026-09-04
-- ============================================

-- ============================================
-- 1. IoT 设备联动关系
-- ============================================
DROP TABLE IF EXISTS iot_device_link;
CREATE TABLE iot_device_link (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID',
    store_id            BIGINT       NOT NULL DEFAULT 1 COMMENT '门店 ID',
    master_device_id    BIGINT       NOT NULL COMMENT '主设备 ID（触发源）',
    slave_device_id     BIGINT       NOT NULL COMMENT '从设备 ID（被控制方）',
    trigger_type        VARCHAR(32)  DEFAULT 'TELEMETRY' COMMENT '触发类型 TELEMETRY / COMMAND / ALERT / MANUAL / SCHEDULED',
    trigger_condition   VARCHAR(500) DEFAULT NULL COMMENT '触发条件 DSL（如 metric.temperature > 35）',
    action_json         VARCHAR(2000) DEFAULT NULL COMMENT '联动动作 JSON（commandType / params / cmdUrl / timeoutSeconds）',
    enabled             TINYINT      DEFAULT 1 COMMENT '1启用 0停用',
    description         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted             TINYINT      DEFAULT 0,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_master_enabled (master_device_id, enabled, deleted),
    INDEX idx_slave (slave_device_id),
    INDEX idx_tenant (tenant_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 设备联动关系';

-- ============================================
-- 2. IoT 联动执行日志
-- ============================================
DROP TABLE IF EXISTS iot_device_link_log;
CREATE TABLE iot_device_link_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       DEFAULT NULL COMMENT '租户 ID',
    store_id            BIGINT       DEFAULT NULL COMMENT '门店 ID',
    link_id             BIGINT       NOT NULL COMMENT '联动 ID',
    master_device_id    BIGINT       DEFAULT NULL COMMENT '主设备 ID',
    slave_device_id     BIGINT       DEFAULT NULL COMMENT '从设备 ID',
    trigger_type        VARCHAR(32)  DEFAULT NULL COMMENT '触发类型',
    trigger_matched     TINYINT      DEFAULT 0 COMMENT '触发条件是否满足 1/0',
    trigger_snapshot    TEXT         DEFAULT NULL COMMENT '触发数据快照（JSON）',
    command_payload     TEXT         DEFAULT NULL COMMENT '下发的指令 JSON',
    response_summary    VARCHAR(500) DEFAULT NULL COMMENT '设备响应摘要',
    status              VARCHAR(16)  DEFAULT 'pending' COMMENT 'success / failed / skipped / timeout',
    error_code          VARCHAR(64)  DEFAULT NULL COMMENT '错误码',
    error_message       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    duration_ms         BIGINT       DEFAULT 0 COMMENT '执行耗时（毫秒）',
    protocol            VARCHAR(16)  DEFAULT NULL COMMENT 'mqtt / http / modbus / ble',
    device_topic        VARCHAR(256) DEFAULT NULL COMMENT '下行的设备 Topic 或 HTTP URL',
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_link (link_id, create_time),
    INDEX idx_status (status, create_time),
    INDEX idx_master_time (master_device_id, create_time),
    INDEX idx_slave_time (slave_device_id, create_time),
    INDEX idx_tenant_time (tenant_id, store_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 联动执行日志';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM iot_device_link)      AS link_count,
    (SELECT COUNT(*) FROM iot_device_link_log)  AS log_count;
