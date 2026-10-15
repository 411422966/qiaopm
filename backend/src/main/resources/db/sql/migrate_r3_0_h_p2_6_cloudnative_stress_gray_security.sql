-- ============================================
-- R3.0-H P2.6 云原生 + 压测 + 灰度 + 安全合规
--  1. cloud_service_registry（服务注册发现）
--  2. cloud_circuit_breaker（熔断记录）
--  3. cloud_slow_query（慢查询）
--  4. cloud_health_check（健康检查）
--  5. feature_flag（灰度发布 - 特性开关）
--  6. ab_experiment（AB 实验）
--  7. ab_assignment（用户分桶）
--  8. data_export_audit（数据导出审计）
--  9. field_desensitization（敏感字段脱敏配置）
-- 10. data_encryption_key（数据加密密钥）
-- 11. compliance_log（合规日志）
-- 12. compliance_report（合规报表）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS cloud_service_registry;
CREATE TABLE cloud_service_registry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name    VARCHAR(128) NOT NULL,
    service_code    VARCHAR(64)  NOT NULL,
    instance_id     VARCHAR(128) NOT NULL COMMENT '实例 ID（IP:PORT / UUID）',
    host            VARCHAR(64)  DEFAULT NULL,
    port            INT          DEFAULT NULL,
    version         VARCHAR(32)  DEFAULT NULL,
    region          VARCHAR(32)  DEFAULT 'default',
    zone            VARCHAR(32)  DEFAULT NULL,
    metadata        TEXT         DEFAULT NULL COMMENT 'JSON 元数据',
    status          VARCHAR(16)  DEFAULT 'UP' COMMENT 'UP / DOWN / STARTING / OUT_OF_SERVICE',
    weight          INT          DEFAULT 100,
    last_heartbeat  DATETIME     DEFAULT NULL,
    register_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_instance (instance_id),
    INDEX idx_service_status (service_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务注册发现';

DROP TABLE IF EXISTS cloud_circuit_breaker;
CREATE TABLE cloud_circuit_breaker (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    breaker_name    VARCHAR(128) NOT NULL,
    service_code    VARCHAR(64)  DEFAULT NULL,
    state           VARCHAR(16)  DEFAULT 'CLOSED' COMMENT 'CLOSED / OPEN / HALF_OPEN',
    failure_count   INT          DEFAULT 0,
    success_count   INT          DEFAULT 0,
    failure_threshold INT        DEFAULT 5,
    recovery_window_sec INT      DEFAULT 30,
    opened_at       DATETIME     DEFAULT NULL,
    last_failure_at DATETIME     DEFAULT NULL,
    last_state_change DATETIME   DEFAULT CURRENT_TIMESTAMP,
    error_message   VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_breaker (tenant_id, breaker_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='熔断记录';

DROP TABLE IF EXISTS cloud_slow_query;
CREATE TABLE cloud_slow_query (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    sql_text        TEXT         NOT NULL,
    sql_hash        VARCHAR(64)  NOT NULL,
    execution_ms    BIGINT       DEFAULT 0,
    rows_examined   BIGINT       DEFAULT 0,
    rows_sent       BIGINT       DEFAULT 0,
    database_name   VARCHAR(64)  DEFAULT NULL,
    table_name      VARCHAR(64)  DEFAULT NULL,
    user_name       VARCHAR(64)  DEFAULT NULL,
    trace_id        VARCHAR(64)  DEFAULT NULL,
    endpoint        VARCHAR(255) DEFAULT NULL,
    occurred_at     DATETIME     NOT NULL,
    suggestion      TEXT         DEFAULT NULL COMMENT '优化建议',
    indexed         TINYINT      DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sql_hash (sql_hash),
    INDEX idx_tenant_time (tenant_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢查询';

DROP TABLE IF EXISTS cloud_health_check;
CREATE TABLE cloud_health_check (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id     VARCHAR(128) NOT NULL,
    check_type      VARCHAR(32)  DEFAULT 'HTTP' COMMENT 'HTTP / TCP / DB / REDIS',
    target          VARCHAR(255) NOT NULL,
    status          VARCHAR(16)  DEFAULT 'UP',
    response_ms     INT          DEFAULT 0,
    http_code       INT          DEFAULT NULL,
    detail          VARCHAR(500) DEFAULT NULL,
    checked_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance_time (instance_id, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康检查';

DROP TABLE IF EXISTS feature_flag;
CREATE TABLE feature_flag (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    flag_key        VARCHAR(64)  NOT NULL,
    flag_name       VARCHAR(128) NOT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    enabled         TINYINT      DEFAULT 0 COMMENT '总开关',
    rollout_percent INT          DEFAULT 0 COMMENT '灰度百分比 0-100',
    whitelist       TEXT         DEFAULT NULL COMMENT 'JSON 白名单 userId',
    blacklist       TEXT         DEFAULT NULL COMMENT 'JSON 黑名单 userId',
    user_rule       VARCHAR(64)  DEFAULT NULL COMMENT 'USER_ID / STORE_ID / REGION / HASH',
    start_at        DATETIME     DEFAULT NULL,
    end_at          DATETIME     DEFAULT NULL,
    owner           VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_flag (tenant_id, flag_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feature Flag';

DROP TABLE IF EXISTS ab_experiment;
CREATE TABLE ab_experiment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    exp_key         VARCHAR(64)  NOT NULL,
    exp_name        VARCHAR(128) NOT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'DRAFT' COMMENT 'DRAFT / RUNNING / PAUSED / ENDED',
    variants        TEXT         NOT NULL COMMENT 'JSON [{key, name, weight}]',
    target_rule     VARCHAR(64)  DEFAULT 'USER_ID_HASH',
    start_at        DATETIME     DEFAULT NULL,
    end_at          DATETIME     DEFAULT NULL,
    primary_metric  VARCHAR(64)  DEFAULT NULL COMMENT 'CONVERSION / GMV / RETENTION',
    owner           VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_exp (tenant_id, exp_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AB 实验';

DROP TABLE IF EXISTS ab_assignment;
CREATE TABLE ab_assignment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    exp_key         VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    variant_key     VARCHAR(32)  NOT NULL,
    assigned_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exp_user (tenant_id, exp_key, user_id),
    INDEX idx_exp (exp_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户分桶';

DROP TABLE IF EXISTS data_export_audit;
CREATE TABLE data_export_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    export_type     VARCHAR(32)  NOT NULL COMMENT 'MEMBER / ORDER / REPORT / FINANCIAL',
    biz_filter      TEXT         DEFAULT NULL COMMENT '导出过滤条件',
    row_count       INT          DEFAULT 0,
    file_url        VARCHAR(500) DEFAULT NULL,
    file_hash       VARCHAR(128) DEFAULT NULL,
    encrypt_key_id  VARCHAR(64)  DEFAULT NULL,
    ip_address      VARCHAR(64)  DEFAULT NULL,
    purpose         VARCHAR(255) DEFAULT NULL COMMENT '导出目的',
    approved        TINYINT      DEFAULT 0,
    approved_by     VARCHAR(64)  DEFAULT NULL,
    approved_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据导出审计';

DROP TABLE IF EXISTS field_desensitization;
CREATE TABLE field_desensitization (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    field_name      VARCHAR(64)  NOT NULL COMMENT 'phone / idCard / email / bankCard / name',
    regex           VARCHAR(255) DEFAULT NULL,
    keep_prefix     INT          DEFAULT 0,
    keep_suffix     INT          DEFAULT 0,
    mask_char       VARCHAR(8)   DEFAULT '*',
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_field (tenant_id, field_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感字段脱敏配置';

DROP TABLE IF EXISTS data_encryption_key;
CREATE TABLE data_encryption_key (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    key_alias       VARCHAR(64)  NOT NULL,
    algorithm       VARCHAR(32)  DEFAULT 'AES-256-CBC',
    key_length      INT          DEFAULT 256,
    key_preview     VARCHAR(64)  DEFAULT NULL COMMENT '前 4 + 后 4 中间省略',
    iv_preview      VARCHAR(64)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ROTATED / REVOKED',
    rotated_at      DATETIME     DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_alias (tenant_id, key_alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加密密钥';

DROP TABLE IF EXISTS compliance_log;
CREATE TABLE compliance_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    log_type        VARCHAR(32)  NOT NULL COMMENT 'LOGIN / DATA_ACCESS / DATA_EXPORT / CONFIG_CHANGE / PERMISSION_GRANT',
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    target_type     VARCHAR(32)  DEFAULT NULL,
    target_id       VARCHAR(128) DEFAULT NULL,
    action          VARCHAR(64)  NOT NULL,
    detail          TEXT         DEFAULT NULL,
    ip_address      VARCHAR(64)  DEFAULT NULL,
    device_id       VARCHAR(128) DEFAULT NULL,
    geo_location    VARCHAR(64)  DEFAULT NULL,
    severity        VARCHAR(16)  DEFAULT 'INFO' COMMENT 'INFO / WARN / CRITICAL',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_type_time (tenant_id, log_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规日志';

DROP TABLE IF EXISTS compliance_report;
CREATE TABLE compliance_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    report_key      VARCHAR(64)  NOT NULL,
    report_type     VARCHAR(32)  NOT NULL COMMENT 'GDPR / PIPL / SOX / HIPAA / CUSTOM',
    period_start    DATETIME     NOT NULL,
    period_end      DATETIME     NOT NULL,
    total_logs      INT          DEFAULT 0,
    critical_count  INT          DEFAULT 0,
    warn_count      INT          DEFAULT 0,
    info_count      INT          DEFAULT 0,
    summary         TEXT         DEFAULT NULL,
    metrics         TEXT         DEFAULT NULL COMMENT 'JSON 关键指标',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / SUBMITTED',
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_report (tenant_id, report_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规报表';

-- ============================================
-- 预置脱敏规则
-- ============================================
INSERT IGNORE INTO field_desensitization (tenant_id, field_name, regex, keep_prefix, keep_suffix, mask_char, enabled) VALUES
    (1, 'phone',     '(\\d{3})\\d{4}(\\d{4})', 3, 4, '*', 1),
    (1, 'idCard',    '(\\d{4})\\d+(\\d{4})',   4, 4, '*', 1),
    (1, 'email',     '(\\w{2})\\w+(@\\w+)',    2, 0, '*', 1),
    (1, 'bankCard',  '(\\d{4})\\d+(\\d{4})',   4, 4, '*', 1),
    (1, 'name',      NULL,                      1, 1, '*', 1);

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM cloud_service_registry) AS registry_count,
    (SELECT COUNT(*) FROM cloud_circuit_breaker) AS breaker_count,
    (SELECT COUNT(*) FROM cloud_slow_query) AS slowq_count,
    (SELECT COUNT(*) FROM feature_flag) AS flag_count,
    (SELECT COUNT(*) FROM ab_experiment) AS exp_count,
    (SELECT COUNT(*) FROM data_export_audit) AS audit_count,
    (SELECT COUNT(*) FROM field_desensitization) AS desens_count,
    (SELECT COUNT(*) FROM compliance_log) AS log_count;