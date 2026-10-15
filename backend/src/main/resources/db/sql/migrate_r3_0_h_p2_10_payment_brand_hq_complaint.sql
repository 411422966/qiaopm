-- ============================================
-- R3.0-H P2.10 支付配置 + 总部看板 + 品牌管理 + 投诉工单
--  1. payment_config（支付配置 - 修复 Dcep Mock）
--  2. payment_channel（支付渠道）
--  3. payment_transaction_log（支付流水日志）
--  4. brand（品牌）
--  5. brand_store_rel（品牌门店关系）
--  6. hq_dashboard_widget（总部看板组件）
--  7. hq_dashboard_snapshot（总部看板快照）
--  8. hq_cross_store_metric（跨门店指标）
--  9. complaint_category（投诉分类）
-- 10. complaint_sla_policy（投诉 SLA 策略）
-- 11. complaint_escalation（投诉升级路径）
-- 12. complaint_satisfaction（投诉满意度）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS payment_config;
CREATE TABLE payment_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    config_code     VARCHAR(64)  NOT NULL,
    config_name     VARCHAR(128) NOT NULL,
    channel_code    VARCHAR(32)  NOT NULL COMMENT 'WECHAT / ALIPAY / UNIONPAY / DCEP / BANK / CASH / WALLET / CARD',
    merchant_id     VARCHAR(128) DEFAULT NULL,
    app_id          VARCHAR(128) DEFAULT NULL,
    api_key         VARCHAR(255) DEFAULT NULL,
    api_secret      VARCHAR(255) DEFAULT NULL,
    cert_path       VARCHAR(255) DEFAULT NULL,
    private_key     VARCHAR(500) DEFAULT NULL,
    public_key      VARCHAR(500) DEFAULT NULL,
    notify_url      VARCHAR(255) DEFAULT NULL,
    return_url      VARCHAR(255) DEFAULT NULL,
    fee_rate        DECIMAL(6,4) DEFAULT 0.0060 COMMENT '手续费率 0.6%',
    fee_type        VARCHAR(16)  DEFAULT 'PERCENTAGE' COMMENT 'PERCENTAGE / FIXED',
    min_fee         DECIMAL(10,2) DEFAULT 0,
    max_fee         DECIMAL(10,2) DEFAULT NULL,
    daily_limit     DECIMAL(18,2) DEFAULT NULL,
    monthly_limit   DECIMAL(18,2) DEFAULT NULL,
    env             VARCHAR(16)  DEFAULT 'PROD' COMMENT 'SANDBOX / PROD / MOCK',
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED / PENDING',
    extra_config    VARCHAR(500) DEFAULT NULL COMMENT 'JSON',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_id, config_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付配置';

DROP TABLE IF EXISTS payment_channel;
CREATE TABLE payment_channel (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code    VARCHAR(32)  NOT NULL,
    channel_name    VARCHAR(64)  NOT NULL,
    channel_type    VARCHAR(16)  DEFAULT 'THIRD_PARTY' COMMENT 'THIRD_PARTY / BANK / INTERNAL',
    icon_url        VARCHAR(255) DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    sort_order      INT          DEFAULT 100,
    description     VARCHAR(500) DEFAULT NULL,
    UNIQUE KEY uk_channel_code (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付渠道';

DROP TABLE IF EXISTS payment_transaction_log;
CREATE TABLE payment_transaction_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    config_id       BIGINT       DEFAULT NULL,
    channel_code    VARCHAR(32)  NOT NULL,
    out_trade_no    VARCHAR(64)  NOT NULL,
    trade_no        VARCHAR(64)  DEFAULT NULL COMMENT '渠道流水号',
    amount          DECIMAL(18,2) DEFAULT 0,
    fee             DECIMAL(10,2) DEFAULT 0,
    status          VARCHAR(16)  DEFAULT 'INIT' COMMENT 'INIT / SUCCESS / FAILED / REFUNDED / CLOSED',
    request_payload TEXT         DEFAULT NULL,
    response_payload TEXT        DEFAULT NULL,
    error_code      VARCHAR(64)  DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    client_ip       VARCHAR(64)  DEFAULT NULL,
    cost_ms         INT          DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_out_trade (out_trade_no),
    INDEX idx_trade (trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水日志';

DROP TABLE IF EXISTS brand;
CREATE TABLE brand (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_code      VARCHAR(64)  NOT NULL,
    brand_name      VARCHAR(128) NOT NULL,
    logo_url        VARCHAR(255) DEFAULT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    contact_phone   VARCHAR(32)  DEFAULT NULL,
    contact_email   VARCHAR(128) DEFAULT NULL,
    website         VARCHAR(255) DEFAULT NULL,
    address         VARCHAR(500) DEFAULT NULL,
    business_license VARCHAR(128) DEFAULT NULL,
    legal_person    VARCHAR(64)  DEFAULT NULL,
    registered_at   DATE         DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED / CLOSED',
    sort_order      INT          DEFAULT 100,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_brand (tenant_id, brand_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌';

DROP TABLE IF EXISTS brand_store_rel;
CREATE TABLE brand_store_rel (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id        BIGINT       NOT NULL,
    store_id        BIGINT       NOT NULL,
    store_code      VARCHAR(64)  DEFAULT NULL,
    join_at         DATE         DEFAULT NULL,
    leave_at        DATE         DEFAULT NULL,
    is_flagship     TINYINT      DEFAULT 0,
    is_active       TINYINT      DEFAULT 1,
    UNIQUE KEY uk_brand_store (brand_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌门店关系';

DROP TABLE IF EXISTS hq_dashboard_widget;
CREATE TABLE hq_dashboard_widget (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    widget_code     VARCHAR(64)  NOT NULL,
    widget_name     VARCHAR(128) NOT NULL,
    widget_type     VARCHAR(32)  DEFAULT 'KPI' COMMENT 'KPI / CHART / TABLE / LIST',
    metric_name     VARCHAR(64)  DEFAULT NULL,
    query_sql       TEXT         DEFAULT NULL,
    layout_x        INT          DEFAULT 0,
    layout_y        INT          DEFAULT 0,
    width           INT          DEFAULT 4,
    height          INT          DEFAULT 3,
    refresh_sec     INT          DEFAULT 60,
    enabled         TINYINT      DEFAULT 1,
    sort_order      INT          DEFAULT 100,
    UNIQUE KEY uk_tenant_widget (tenant_id, widget_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='总部看板组件';

DROP TABLE IF EXISTS hq_dashboard_snapshot;
CREATE TABLE hq_dashboard_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    period_type     VARCHAR(16)  DEFAULT 'DAY' COMMENT 'DAY / WEEK / MONTH',
    period_key      VARCHAR(16)  NOT NULL COMMENT '2026-08-14',
    brand_id        BIGINT       DEFAULT NULL,
    widget_code     VARCHAR(64)  DEFAULT NULL,
    metric_value    DECIMAL(18,4) DEFAULT 0,
    extra           VARCHAR(500) DEFAULT NULL,
    snapshot_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_period (tenant_id, period_type, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='总部看板快照';

DROP TABLE IF EXISTS hq_cross_store_metric;
CREATE TABLE hq_cross_store_metric (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       DEFAULT NULL,
    period_type     VARCHAR(16)  DEFAULT 'DAY',
    period_key      VARCHAR(16)  NOT NULL,
    total_stores    INT          DEFAULT 0,
    active_stores   INT          DEFAULT 0,
    total_revenue   DECIMAL(18,2) DEFAULT 0,
    total_orders    BIGINT       DEFAULT 0,
    total_members   BIGINT       DEFAULT 0,
    new_members     BIGINT       DEFAULT 0,
    avg_order_amt   DECIMAL(10,2) DEFAULT 0,
    INDEX idx_tenant_period (tenant_id, period_type, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨门店指标';

DROP TABLE IF EXISTS complaint_category;
CREATE TABLE complaint_category (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    category_code   VARCHAR(64)  NOT NULL,
    category_name   VARCHAR(128) NOT NULL,
    parent_id       BIGINT       DEFAULT NULL,
    default_priority VARCHAR(16) DEFAULT 'P3' COMMENT 'P1 / P2 / P3 / P4',
    default_sla_minutes INT      DEFAULT 1440 COMMENT '默认 SLA（分钟）',
    enabled         TINYINT      DEFAULT 1,
    sort_order      INT          DEFAULT 100,
    UNIQUE KEY uk_tenant_category (tenant_id, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉分类';

DROP TABLE IF EXISTS complaint_sla_policy;
CREATE TABLE complaint_sla_policy (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    priority        VARCHAR(16)  NOT NULL COMMENT 'P1 / P2 / P3 / P4',
    response_minutes INT         DEFAULT 30,
    resolve_minutes INT          DEFAULT 1440,
    escalate_to     VARCHAR(255) DEFAULT NULL,
    notify_channels VARCHAR(255) DEFAULT NULL,
    compensation_limit DECIMAL(10,2) DEFAULT 0,
    UNIQUE KEY uk_tenant_priority (tenant_id, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉 SLA 策略';

DROP TABLE IF EXISTS complaint_escalation;
CREATE TABLE complaint_escalation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL,
    from_assignee   VARCHAR(64)  DEFAULT NULL,
    to_assignee     VARCHAR(64)  NOT NULL,
    escalate_reason VARCHAR(500) DEFAULT NULL,
    escalated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    accepted_at     DATETIME     DEFAULT NULL,
    INDEX idx_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉升级';

DROP TABLE IF EXISTS complaint_satisfaction;
CREATE TABLE complaint_satisfaction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT       NOT NULL,
    rating          INT          DEFAULT NULL COMMENT '1-5',
    feedback        VARCHAR(500) DEFAULT NULL,
    nps_score       INT          DEFAULT NULL COMMENT '-100 ~ 100',
    rated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉满意度';

-- ============================================
-- 预置：8 个支付渠道
-- ============================================
INSERT IGNORE INTO payment_channel (channel_code, channel_name, channel_type, sort_order) VALUES
    ('WECHAT',    '微信支付',     'THIRD_PARTY', 1),
    ('ALIPAY',    '支付宝',       'THIRD_PARTY', 2),
    ('UNIONPAY',  '银联',         'THIRD_PARTY', 3),
    ('DCEP',      '数字人民币',   'THIRD_PARTY', 4),
    ('BANK',      '银行转账',     'BANK',        5),
    ('CASH',      '现金',         'INTERNAL',    6),
    ('WALLET',    '会员储值',     'INTERNAL',    7),
    ('CARD',      '预付卡',       'INTERNAL',    8);

-- ============================================
-- 预置：8 个投诉分类
-- ============================================
INSERT IGNORE INTO complaint_category (tenant_id, category_code, category_name, default_priority, default_sla_minutes, sort_order) VALUES
    (1, 'SERVICE',     '服务态度',     'P3', 480,   1),
    (1, 'QUALITY',     '服务效果',     'P2', 240,   2),
    (1, 'BILLING',     '账单争议',     'P2', 240,   3),
    (1, 'ENVIRONMENT', '环境卫生',     'P4', 1440,  4),
    (1, 'EQUIPMENT',   '设备故障',     'P3', 480,   5),
    (1, 'PRODUCT',     '产品质量',     'P2', 240,   6),
    (1, 'PRIVACY',     '隐私投诉',     'P1', 60,    7),
    (1, 'OTHER',       '其他',         'P4', 1440,  99);

-- ============================================
-- 预置：4 个 SLA 策略
-- ============================================
INSERT IGNORE INTO complaint_sla_policy (tenant_id, priority, response_minutes, resolve_minutes) VALUES
    (1, 'P1', 15, 60),
    (1, 'P2', 30, 240),
    (1, 'P3', 60, 480),
    (1, 'P4', 120, 1440);

-- ============================================
-- 预置：6 个总部看板组件
-- ============================================
INSERT IGNORE INTO hq_dashboard_widget (tenant_id, widget_code, widget_name, widget_type, metric_name, sort_order) VALUES
    (1, 'TOTAL_STORES',   '门店总数',     'KPI',   'total_stores',   1),
    (1, 'TOTAL_REVENUE',  '总营收',       'KPI',   'total_revenue',  2),
    (1, 'TOTAL_ORDERS',   '总订单',       'KPI',   'total_orders',   3),
    (1, 'TOTAL_MEMBERS',  '会员总数',     'KPI',   'total_members',  4),
    (1, 'NEW_MEMBERS',    '新增会员',     'KPI',   'new_members',    5),
    (1, 'TOP_STORES',     '门店排行',     'TABLE', 'top_stores',     6);

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM payment_channel) AS channel_count,
    (SELECT COUNT(*) FROM complaint_category) AS cat_count,
    (SELECT COUNT(*) FROM complaint_sla_policy) AS sla_count,
    (SELECT COUNT(*) FROM hq_dashboard_widget) AS widget_count;