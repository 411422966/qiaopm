-- ============================================
-- R3.0-H P2.8 架构演进 + DDD + 微服务 + Event Sourcing
--  1. arch_decision（架构决策记录 ADR）
--  2. arch_roadmap（演进路线图）
--  3. arch_dependency（服务依赖图）
--  4. bounded_context（限界上下文）
--  5. aggregate_root（聚合根）
--  6. domain_event_store（领域事件存储）
--  7. event_snapshot（事件快照）
--  8. event_projection（投影）
--  9. microservice_boundary（微服务边界）
-- 10. service_contract（服务契约）
-- 11. service_route_rule（API 网关路由）
-- 12. cqrs_query_model（CQRS 读模型）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS arch_decision;
CREATE TABLE arch_decision (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    adr_no          VARCHAR(32)  NOT NULL COMMENT 'ADR-0001',
    title           VARCHAR(255) NOT NULL,
    context         TEXT         DEFAULT NULL COMMENT '背景 / 问题',
    decision        TEXT         NOT NULL COMMENT '决策内容',
    consequences    TEXT         DEFAULT NULL COMMENT '决策后果',
    status          VARCHAR(16)  DEFAULT 'PROPOSED' COMMENT 'PROPOSED / ACCEPTED / DEPRECATED / SUPERSEDED',
    author          VARCHAR(64)  DEFAULT NULL,
    decision_date   DATE         DEFAULT NULL,
    superseded_by   VARCHAR(32)  DEFAULT NULL,
    tags            VARCHAR(255) DEFAULT NULL COMMENT 'JSON 标签',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_adr_no (adr_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构决策记录';

DROP TABLE IF EXISTS arch_roadmap;
CREATE TABLE arch_roadmap (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    milestone_code  VARCHAR(64)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    phase           VARCHAR(16)  DEFAULT 'P2' COMMENT 'P0 / P1 / P2 / P3',
    description     TEXT         DEFAULT NULL,
    deliverables    TEXT         DEFAULT NULL COMMENT 'JSON 交付物',
    target_date     DATE         DEFAULT NULL,
    actual_date     DATE         DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PLANNED' COMMENT 'PLANNED / IN_PROGRESS / DONE / DELAYED',
    owner           VARCHAR(64)  DEFAULT NULL,
    progress        INT          DEFAULT 0 COMMENT '0-100',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_milestone (milestone_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='架构演进路线图';

DROP TABLE IF EXISTS arch_dependency;
CREATE TABLE arch_dependency (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_service    VARCHAR(64)  NOT NULL,
    to_service      VARCHAR(64)  NOT NULL,
    dependency_type VARCHAR(16)  DEFAULT 'SYNC_CALL' COMMENT 'SYNC_CALL / ASYNC_EVENT / DB_SHARE / CACHE',
    protocol        VARCHAR(16)  DEFAULT 'HTTP' COMMENT 'HTTP / GRPC / MQ / DUBBO',
    criticality     VARCHAR(16)  DEFAULT 'NORMAL' COMMENT 'LOW / NORMAL / HIGH / BLOCKER',
    qps_estimate    INT          DEFAULT 0,
    description     VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_from (from_service),
    INDEX idx_to (to_service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务依赖图';

DROP TABLE IF EXISTS bounded_context;
CREATE TABLE bounded_context (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    context_code    VARCHAR(64)  NOT NULL,
    context_name    VARCHAR(128) NOT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    domain_layer    VARCHAR(32)  DEFAULT 'CORE' COMMENT 'CORE / SUPPORTING / GENERIC',
    aggregate_count INT          DEFAULT 0,
    upstream_ctx    VARCHAR(255) DEFAULT NULL COMMENT '逗号分隔上游 context',
    downstream_ctx  VARCHAR(255) DEFAULT NULL COMMENT '逗号分隔下游 context',
    team_owner      VARCHAR(64)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DEPRECATED / PLANNED',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_context_code (context_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限界上下文';

DROP TABLE IF EXISTS aggregate_root;
CREATE TABLE aggregate_root (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    context_code    VARCHAR(64)  NOT NULL,
    aggregate_code  VARCHAR(64)  NOT NULL,
    aggregate_name  VARCHAR(128) NOT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    root_table      VARCHAR(64)  DEFAULT NULL,
    root_id_field   VARCHAR(64)  DEFAULT 'id',
    invariant_count INT          DEFAULT 0 COMMENT '不变量数量',
    event_count     INT          DEFAULT 0,
    version         VARCHAR(16)  DEFAULT '1.0.0',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_context_agg (context_code, aggregate_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合根';

DROP TABLE IF EXISTS domain_event_store;
CREATE TABLE domain_event_store (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL COMMENT '聚合根类型',
    aggregate_id    VARCHAR(64)  NOT NULL COMMENT '聚合根 ID',
    event_id        VARCHAR(64)  NOT NULL COMMENT '事件 ID（UUID）',
    event_type      VARCHAR(64)  NOT NULL COMMENT '事件类型',
    event_version   INT          DEFAULT 1,
    payload         LONGTEXT     DEFAULT NULL COMMENT 'JSON 载荷',
    occurred_at     DATETIME(3)  NOT NULL,
    tenant_id       BIGINT       DEFAULT NULL,
    user_id         BIGINT       DEFAULT NULL,
    correlation_id  VARCHAR(64)  DEFAULT NULL,
    causation_id    VARCHAR(64)  DEFAULT NULL,
    sequence        BIGINT       NOT NULL COMMENT '聚合内序号',
    INDEX idx_agg (aggregate_type, aggregate_id, sequence),
    UNIQUE KEY uk_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件存储';

DROP TABLE IF EXISTS event_snapshot;
CREATE TABLE event_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    sequence        BIGINT       NOT NULL COMMENT '基于 sequence 的快照',
    state           LONGTEXT     DEFAULT NULL COMMENT 'JSON 状态',
    version         VARCHAR(16)  DEFAULT '1.0.0',
    created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_agg_seq (aggregate_type, aggregate_id, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件快照';

DROP TABLE IF EXISTS event_projection;
CREATE TABLE event_projection (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    projection_name VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    last_sequence   BIGINT       DEFAULT 0 COMMENT '已处理的最后序号',
    event_count     BIGINT       DEFAULT 0,
    last_event_at   DATETIME(3)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'RUNNING' COMMENT 'RUNNING / PAUSED / FAILED',
    error_message   VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_projection_agg (projection_name, aggregate_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投影进度';

DROP TABLE IF EXISTS microservice_boundary;
CREATE TABLE microservice_boundary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_code    VARCHAR(64)  NOT NULL,
    service_name    VARCHAR(128) NOT NULL,
    context_code    VARCHAR(64)  DEFAULT NULL COMMENT '对应限界上下文',
    port            INT          DEFAULT 8080,
    base_path       VARCHAR(128) DEFAULT '/',
    api_version     VARCHAR(16)  DEFAULT 'v1',
    responsibility  VARCHAR(500) DEFAULT NULL,
    dependencies    VARCHAR(255) DEFAULT NULL COMMENT '逗号分隔服务',
    deploy_unit     VARCHAR(64)  DEFAULT NULL COMMENT 'JAR / DOCKER / K8S',
    status          VARCHAR(16)  DEFAULT 'PLANNED' COMMENT 'PLANNED / RUNNING / DEPRECATED',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_code (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微服务边界';

DROP TABLE IF EXISTS service_contract;
CREATE TABLE service_contract (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_code    VARCHAR(64)  NOT NULL,
    method_name     VARCHAR(128) NOT NULL,
    http_method     VARCHAR(8)   DEFAULT 'GET',
    path            VARCHAR(255) NOT NULL,
    request_schema  TEXT         DEFAULT NULL COMMENT 'JSON Schema',
    response_schema TEXT         DEFAULT NULL COMMENT 'JSON Schema',
    auth_required   TINYINT      DEFAULT 1,
    rate_limit      INT          DEFAULT 1000,
    timeout_ms      INT          DEFAULT 3000,
    version         VARCHAR(16)  DEFAULT 'v1',
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_method (service_code, method_name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务契约';

DROP TABLE IF EXISTS service_route_rule;
CREATE TABLE service_route_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_pattern   VARCHAR(255) NOT NULL,
    target_service  VARCHAR(64)  NOT NULL,
    target_path     VARCHAR(255) DEFAULT NULL,
    strip_prefix    TINYINT      DEFAULT 0,
    retry_count     INT          DEFAULT 0,
    timeout_ms      INT          DEFAULT 5000,
    rate_limit_qps  INT          DEFAULT 1000,
    auth_required   TINYINT      DEFAULT 1,
    priority        INT          DEFAULT 100,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_route_pattern (route_pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API 网关路由';

DROP TABLE IF EXISTS cqrs_query_model;
CREATE TABLE cqrs_query_model (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name      VARCHAR(64)  NOT NULL,
    source_aggregate VARCHAR(64) DEFAULT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    schema_sql      TEXT         DEFAULT NULL COMMENT '建表 SQL',
    last_refreshed_at DATETIME   DEFAULT NULL,
    refresh_interval_sec INT     DEFAULT 60,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_name (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CQRS 读模型';

-- ============================================
-- 预置：6 个限界上下文
-- ============================================
INSERT IGNORE INTO bounded_context (context_code, context_name, description, domain_layer, team_owner) VALUES
    ('member',        '会员域',     '会员/标签/画像/等级/积分',         'CORE',        'team-member'),
    ('booking',       '预约域',     '预约/排班/资源',                  'CORE',        'team-booking'),
    ('pos',           '交易域',     '收银/订单/退款/优惠',             'CORE',        'team-pos'),
    ('inventory',     '库存域',     '库存/批次/调拨/多仓',             'CORE',        'team-inventory'),
    ('marketing',     '营销域',     '优惠券/活动/自动化/RFM/LTV',     'SUPPORTING',  'team-marketing'),
    ('analytics',     '分析域',     '报表/BI/AI/数据中台',             'SUPPORTING',  'team-data');

-- ============================================
-- 预置：6 个聚合根
-- ============================================
INSERT IGNORE INTO aggregate_root (context_code, aggregate_code, aggregate_name, root_table) VALUES
    ('member',     'Member',        '会员聚合',     'crm_member'),
    ('booking',    'Appointment',   '预约聚合',     'booking_appointment'),
    ('pos',        'Order',         '订单聚合',     'pos_order'),
    ('inventory',  'StockBatch',    '库存批次聚合', 'stock_batch'),
    ('marketing',  'Coupon',        '优惠券聚合',   'marketing_coupon'),
    ('analytics',  'Report',        '报表聚合',     'report_metric_snapshot');

-- ============================================
-- 预置：架构演进路线图
-- ============================================
INSERT IGNORE INTO arch_roadmap (milestone_code, title, phase, status, progress, target_date) VALUES
    ('M-P0-1', '单模块 + MVC',                              'P0', 'DONE',     100, NULL),
    ('M-P0-2', '权限/多租户/数据权限',                       'P0', 'DONE',     100, NULL),
    ('M-P1-1', '业务 P1 模块化',                             'P1', 'DONE',     100, NULL),
    ('M-P1-2', '业务 P2/P3 模块化',                          'P1', 'DONE',     100, NULL),
    ('M-P2-1', 'DDD 重构（限界上下文 + 聚合根）',              'P2', 'IN_PROGRESS', 50, NULL),
    ('M-P2-2', '事件驱动 + Event Sourcing（核心域）',         'P2', 'IN_PROGRESS', 30, NULL),
    ('M-P2-3', '微服务拆分（按限界上下文）',                  'P2', 'PLANNED',     0, NULL),
    ('M-P3-1', 'Service Mesh + Serverless',                 'P3', 'PLANNED',     0, NULL);

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM arch_decision) AS adr_count,
    (SELECT COUNT(*) FROM arch_roadmap) AS roadmap_count,
    (SELECT COUNT(*) FROM bounded_context) AS context_count,
    (SELECT COUNT(*) FROM aggregate_root) AS agg_count,
    (SELECT COUNT(*) FROM microservice_boundary) AS service_count,
    (SELECT COUNT(*) FROM service_contract) AS contract_count,
    (SELECT COUNT(*) FROM service_route_rule) AS route_count;