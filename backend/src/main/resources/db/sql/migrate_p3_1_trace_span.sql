-- ============================================
-- P3.1 全链路追踪：Trace Span 日志表
-- 修复：TraceService 写入 trace_span 时报 "Table 'meiyetec.trace_span' doesn't exist"
-- 字段与 com.meiyetec.entity.TraceSpan 保持一致
-- 2026-08-16
-- ============================================

DROP TABLE IF EXISTS trace_span;
CREATE TABLE trace_span (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)  NOT NULL                COMMENT '链路 traceId',
    span_id         VARCHAR(64)  NOT NULL                COMMENT '当前 spanId',
    parent_span_id  VARCHAR(64)  DEFAULT NULL            COMMENT '调用方 spanId（根调用为空）',
    service_name    VARCHAR(64)  NOT NULL                COMMENT '服务名',
    operation_name  VARCHAR(255) DEFAULT NULL            COMMENT '方法名 / 操作名',
    http_method     VARCHAR(16)  DEFAULT NULL            COMMENT 'HTTP 方法',
    uri             VARCHAR(512) DEFAULT NULL            COMMENT '请求 URI',
    status          VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / ERROR / TIMEOUT',
    http_status     INT          DEFAULT NULL            COMMENT 'HTTP 状态码',
    error_code      VARCHAR(64)  DEFAULT NULL            COMMENT '错误码',
    start_at        DATETIME(3)  DEFAULT NULL            COMMENT '开始时间',
    duration_ms     BIGINT       DEFAULT NULL            COMMENT '耗时 ms',
    tenant_id       BIGINT       DEFAULT NULL            COMMENT '租户 ID',
    user_id         BIGINT       DEFAULT NULL            COMMENT '用户 ID',
    tags            TEXT         DEFAULT NULL            COMMENT '额外标签 JSON',
    client_ip       VARCHAR(64)  DEFAULT NULL            COMMENT '调用来源 IP',
    user_agent      VARCHAR(512) DEFAULT NULL            COMMENT 'User-Agent',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_trace_span_trace_id  (trace_id),
    INDEX idx_trace_span_span_id   (span_id),
    INDEX idx_trace_span_parent    (parent_span_id),
    INDEX idx_trace_span_tenant    (tenant_id),
    INDEX idx_trace_span_user      (user_id),
    INDEX idx_trace_span_start_at  (start_at),
    INDEX idx_trace_span_status    (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='P3.1 全链路追踪 Span 日志';
