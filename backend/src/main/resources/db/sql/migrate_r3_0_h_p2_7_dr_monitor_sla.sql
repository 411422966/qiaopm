-- ============================================
-- R3.0-H P2.7 应急容灾 + 灾备演练 + 监控告警 + SLA 报告
--  1. dr_site（灾备站点）
--  2. dr_runbook（应急预案）
--  3. dr_incident（故障事件）
--  4. dr_switch_log（切换日志）
--  5. dr_backup_record（备份记录）
--  6. dr_drill_plan（演练计划）
--  7. dr_drill_execution（演练执行）
--  8. monitor_metric（监控指标）
--  9. monitor_alert_rule（告警规则）
-- 10. monitor_alert_event（告警事件）
-- 11. monitor_notification_log（通知日志）
-- 12. sla_target（SLA 目标）
-- 13. sla_uptime_record（可用性记录）
-- 14. sla_report（SLA 月度报告）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS dr_site;
CREATE TABLE dr_site (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_code       VARCHAR(64)  NOT NULL,
    site_name       VARCHAR(128) NOT NULL,
    site_type       VARCHAR(16)  NOT NULL COMMENT 'PRIMARY / STANDBY / DISASTER',
    region          VARCHAR(32)  DEFAULT NULL,
    zone            VARCHAR(32)  DEFAULT NULL,
    endpoint        VARCHAR(255) DEFAULT NULL,
    role            VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / STANDBY / OFFLINE',
    priority        INT          DEFAULT 100,
    rpo_seconds     INT          DEFAULT 60 COMMENT '数据丢失容忍',
    rto_seconds     INT          DEFAULT 300 COMMENT '恢复时间目标',
    last_heartbeat  DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_code (site_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灾备站点';

DROP TABLE IF EXISTS dr_runbook;
CREATE TABLE dr_runbook (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    runbook_code    VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    category        VARCHAR(32)  NOT NULL COMMENT 'DB_DOWN / NETWORK / SECURITY / DATA_CORRUPTION / PEAK_LOAD',
    severity        VARCHAR(16)  DEFAULT 'P2' COMMENT 'P1 / P2 / P3 / P4',
    description     TEXT         DEFAULT NULL,
    steps           TEXT         NOT NULL COMMENT 'JSON 步骤数组 [{order, action, owner, timeout_sec}]',
    escalation      TEXT         DEFAULT NULL COMMENT 'JSON 升级路径',
    estimated_minutes INT        DEFAULT 30,
    last_reviewed_at DATETIME    DEFAULT NULL,
    owner           VARCHAR(64)  DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_runbook_code (runbook_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应急预案';

DROP TABLE IF EXISTS dr_incident;
CREATE TABLE dr_incident (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    incident_no     VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    severity        VARCHAR(16)  DEFAULT 'P3' COMMENT 'P1 / P2 / P3 / P4',
    status          VARCHAR(16)  DEFAULT 'OPEN' COMMENT 'OPEN / INVESTIGATING / MITIGATING / RESOLVED / POSTMORTEM',
    affected_services TEXT       DEFAULT NULL COMMENT '逗号分隔',
    runbook_id      BIGINT       DEFAULT NULL,
    reported_by     VARCHAR(64)  DEFAULT NULL,
    reported_at     DATETIME     NOT NULL,
    resolved_at     DATETIME     DEFAULT NULL,
    root_cause      VARCHAR(500) DEFAULT NULL,
    resolution      TEXT         DEFAULT NULL,
    duration_seconds INT         DEFAULT NULL,
    customer_impact TEXT         DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_incident_no (incident_no),
    INDEX idx_status_time (status, reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故障事件';

DROP TABLE IF EXISTS dr_switch_log;
CREATE TABLE dr_switch_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_site_id    BIGINT       NOT NULL,
    to_site_id      BIGINT       NOT NULL,
    switch_type     VARCHAR(32)  DEFAULT 'PLANNED' COMMENT 'PLANNED / EMERGENCY / DRILL',
    triggered_by    VARCHAR(64)  DEFAULT NULL,
    reason          VARCHAR(500) DEFAULT NULL,
    started_at      DATETIME     NOT NULL,
    completed_at    DATETIME     DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS / SUCCESS / FAILED / ROLLED_BACK',
    data_loss_seconds INT        DEFAULT NULL,
    downtime_seconds  INT        DEFAULT NULL,
    detail          TEXT         DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='切换日志';

DROP TABLE IF EXISTS dr_backup_record;
CREATE TABLE dr_backup_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id         BIGINT       DEFAULT NULL,
    backup_type     VARCHAR(32)  NOT NULL COMMENT 'FULL / INCREMENTAL / DIFFERENTIAL / SNAPSHOT',
    backup_target   VARCHAR(64)  NOT NULL COMMENT 'DB / FILES / CONFIG',
    file_url        VARCHAR(500) DEFAULT NULL,
    file_size_bytes BIGINT       DEFAULT 0,
    file_hash       VARCHAR(128) DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED / VERIFYING',
    duration_seconds INT         DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    started_at      DATETIME     NOT NULL,
    completed_at    DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target_time (backup_target, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份记录';

DROP TABLE IF EXISTS dr_drill_plan;
CREATE TABLE dr_drill_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code       VARCHAR(64)  NOT NULL,
    plan_name       VARCHAR(128) NOT NULL,
    drill_type      VARCHAR(32)  NOT NULL COMMENT 'TABLETOP / SIMULATION / PARTIAL_FAILOVER / FULL_FAILOVER',
    scheduled_at    DATETIME     NOT NULL,
    estimated_hours DECIMAL(6,2) DEFAULT 4.0,
    scope           VARCHAR(500) DEFAULT NULL,
    participants    TEXT         DEFAULT NULL,
    scenario        TEXT         DEFAULT NULL,
    objectives      TEXT         DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED / RUNNING / COMPLETED / CANCELLED',
    owner           VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plan_code (plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演练计划';

DROP TABLE IF EXISTS dr_drill_execution;
CREATE TABLE dr_drill_execution (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         BIGINT       NOT NULL,
    plan_code       VARCHAR(64)  NOT NULL,
    started_at      DATETIME     NOT NULL,
    completed_at    DATETIME     DEFAULT NULL,
    success         TINYINT      DEFAULT NULL COMMENT '1 成功 / 0 失败 / NULL 进行中',
    rto_actual_sec  INT          DEFAULT NULL,
    rpo_actual_sec  INT          DEFAULT NULL,
    issues_found    TEXT         DEFAULT NULL,
    lessons_learned TEXT         DEFAULT NULL,
    score           INT          DEFAULT NULL COMMENT '演练评分 0-100',
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_time (plan_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演练执行';

DROP TABLE IF EXISTS monitor_metric;
CREATE TABLE monitor_metric (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    metric_name     VARCHAR(64)  NOT NULL,
    metric_type     VARCHAR(16)  DEFAULT 'GAUGE' COMMENT 'GAUGE / COUNTER / HISTOGRAM / SUMMARY',
    target_type     VARCHAR(32)  DEFAULT NULL COMMENT 'SERVICE / INSTANCE / DB / CACHE / API',
    target_code     VARCHAR(128) DEFAULT NULL,
    value           DOUBLE       DEFAULT 0,
    unit            VARCHAR(16)  DEFAULT NULL,
    tags            VARCHAR(500) DEFAULT NULL COMMENT 'JSON tags',
    sampled_at      DATETIME     NOT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_metric_time (metric_name, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控指标';

DROP TABLE IF EXISTS monitor_alert_rule;
CREATE TABLE monitor_alert_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    rule_name       VARCHAR(128) NOT NULL,
    metric_name     VARCHAR(64)  NOT NULL,
    comparator      VARCHAR(8)   DEFAULT '>' COMMENT '> / >= / < / <= / == / !=',
    threshold       DOUBLE       NOT NULL,
    severity        VARCHAR(16)  DEFAULT 'WARN' COMMENT 'INFO / WARN / ERROR / CRITICAL',
    duration_sec    INT          DEFAULT 60 COMMENT '持续时间触发',
    channels        VARCHAR(255) DEFAULT NULL COMMENT 'SMS / EMAIL / WECHAT / WEBHOOK 逗号分隔',
    recipients      TEXT         DEFAULT NULL,
    silence_minutes INT          DEFAULT 30,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_rule (tenant_id, rule_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则';

DROP TABLE IF EXISTS monitor_alert_event;
CREATE TABLE monitor_alert_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    rule_id         BIGINT       NOT NULL,
    rule_name       VARCHAR(128) DEFAULT NULL,
    metric_name     VARCHAR(64)  NOT NULL,
    metric_value    DOUBLE       DEFAULT NULL,
    threshold       DOUBLE       DEFAULT NULL,
    severity        VARCHAR(16)  DEFAULT 'WARN',
    status          VARCHAR(16)  DEFAULT 'FIRING' COMMENT 'FIRING / RESOLVED / SILENCED',
    started_at      DATETIME     NOT NULL,
    resolved_at     DATETIME     DEFAULT NULL,
    acknowledged_at DATETIME     DEFAULT NULL,
    acked_by        VARCHAR(64)  DEFAULT NULL,
    incident_id     BIGINT       DEFAULT NULL,
    message         VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status_time (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警事件';

DROP TABLE IF EXISTS monitor_notification_log;
CREATE TABLE monitor_notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_event_id  BIGINT       NOT NULL,
    channel         VARCHAR(32)  NOT NULL COMMENT 'SMS / EMAIL / WECHAT / WEBHOOK / DINGTALK',
    recipient       VARCHAR(255) DEFAULT NULL,
    subject         VARCHAR(255) DEFAULT NULL,
    content         TEXT         DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING / SUCCESS / FAILED',
    error_message   VARCHAR(500) DEFAULT NULL,
    sent_at         DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_time (alert_event_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知日志';

DROP TABLE IF EXISTS sla_target;
CREATE TABLE sla_target (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    service_code    VARCHAR(64)  NOT NULL,
    service_name    VARCHAR(128) DEFAULT NULL,
    availability_target DECIMAL(5,2) DEFAULT 99.90 COMMENT '可用性 % 目标',
    monthly_downtime_budget_sec INT DEFAULT 2592 COMMENT '月度停机预算 30天',
    response_p95_ms INT          DEFAULT 500,
    response_p99_ms INT          DEFAULT 1000,
    error_rate_threshold DECIMAL(5,4) DEFAULT 0.001 COMMENT '错误率 0.1%',
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_service (tenant_id, service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SLA 目标';

DROP TABLE IF EXISTS sla_uptime_record;
CREATE TABLE sla_uptime_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    service_code    VARCHAR(64)  NOT NULL,
    record_date     DATE         NOT NULL,
    total_minutes   INT          DEFAULT 1440,
    down_minutes    INT          DEFAULT 0,
    uptime_minutes  INT          DEFAULT 1440,
    availability    DECIMAL(7,4) DEFAULT 100.0000,
    incident_count  INT          DEFAULT 0,
    p95_response_ms INT          DEFAULT NULL,
    p99_response_ms INT          DEFAULT NULL,
    error_count     INT          DEFAULT 0,
    total_requests  BIGINT       DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_service_date (tenant_id, service_code, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可用性记录';

DROP TABLE IF EXISTS sla_report;
CREATE TABLE sla_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    report_key      VARCHAR(64)  NOT NULL,
    period_month    VARCHAR(7)   NOT NULL COMMENT 'YYYY-MM',
    service_code    VARCHAR(64)  DEFAULT NULL,
    availability_actual DECIMAL(7,4) DEFAULT NULL,
    availability_target DECIMAL(5,2) DEFAULT NULL,
    achieved        TINYINT      DEFAULT NULL,
    downtime_seconds INT         DEFAULT NULL,
    downtime_budget_sec INT      DEFAULT NULL,
    incident_count  INT          DEFAULT 0,
    p95_avg_ms      INT          DEFAULT NULL,
    p99_avg_ms      INT          DEFAULT NULL,
    error_rate      DECIMAL(7,6) DEFAULT NULL,
    summary         TEXT         DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'GENERATED' COMMENT 'GENERATED / SUBMITTED / ACKNOWLEDGED',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_report (tenant_id, report_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SLA 月报';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM dr_site) AS site_count,
    (SELECT COUNT(*) FROM dr_runbook) AS runbook_count,
    (SELECT COUNT(*) FROM dr_incident) AS incident_count,
    (SELECT COUNT(*) FROM dr_backup_record) AS backup_count,
    (SELECT COUNT(*) FROM dr_drill_plan) AS plan_count,
    (SELECT COUNT(*) FROM monitor_metric) AS metric_count,
    (SELECT COUNT(*) FROM monitor_alert_rule) AS rule_count,
    (SELECT COUNT(*) FROM sla_target) AS sla_target_count,
    (SELECT COUNT(*) FROM sla_uptime_record) AS uptime_count,
    (SELECT COUNT(*) FROM sla_report) AS report_count;