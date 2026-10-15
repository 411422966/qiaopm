-- ============================================
-- R3.0-H P2.1 开放平台补强
--  1. platform_webhook_event（事件订阅配置）
--  2. platform_webhook_delivery（投递记录 + 重试）
--  3. open_api_quota（API 配额与限流）
--  4. open_app_secret_audit（密钥轮换审计）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS platform_webhook_event;
CREATE TABLE platform_webhook_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    name            VARCHAR(128) NOT NULL COMMENT '事件名（如 order.paid/booking.created）',
    description     VARCHAR(500) DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 事件类型';

DROP TABLE IF EXISTS platform_webhook_delivery;
CREATE TABLE platform_webhook_delivery (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    subscription_id BIGINT       NOT NULL COMMENT '关联 platform_webhook_subscription.id',
    event_name      VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    payload_hash    VARCHAR(64)  NOT NULL COMMENT 'payload SHA-256（幂等去重用）',
    target_url      VARCHAR(500) NOT NULL,
    signing_secret  VARCHAR(64)  NOT NULL COMMENT 'HMAC-SHA256 签名密钥',
    signature       VARCHAR(128) NOT NULL COMMENT '本次投递签名',
    attempt         INT          DEFAULT 1 COMMENT '第几次尝试',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING / SUCCESS / FAILED / DEAD_LETTER',
    http_status     INT          DEFAULT NULL,
    response_body   TEXT         DEFAULT NULL,
    duration_ms     INT          DEFAULT NULL,
    next_retry_at   DATETIME     DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_subscription (subscription_id, status),
    INDEX idx_status_retry (status, next_retry_at),
    INDEX idx_event_hash (event_name, payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 投递 + 重试';

DROP TABLE IF EXISTS platform_webhook_subscription;
CREATE TABLE platform_webhook_subscription (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    name            VARCHAR(128) NOT NULL,
    target_url      VARCHAR(500) NOT NULL,
    signing_secret  VARCHAR(64)  NOT NULL COMMENT 'HMAC-SHA256 密钥',
    events_csv      VARCHAR(500) DEFAULT '*' COMMENT '订阅的事件列表（逗号分隔），*=全部',
    enabled         TINYINT      DEFAULT 1,
    retry_strategy  VARCHAR(32)  DEFAULT 'EXP_BACKOFF' COMMENT 'EXP_BACKOFF / FIXED_DELAY',
    max_attempts    INT          DEFAULT 5,
    timeout_seconds INT          DEFAULT 10,
    last_triggered_at DATETIME   DEFAULT NULL,
    success_count   BIGINT       DEFAULT 0,
    failure_count   BIGINT       DEFAULT 0,
    description     VARCHAR(500) DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 订阅配置';

DROP TABLE IF EXISTS open_api_quota;
CREATE TABLE open_api_quota (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    app_id          BIGINT       NOT NULL,
    window          VARCHAR(16)  NOT NULL COMMENT 'MINUTE / HOUR / DAY / MONTH',
    quota_limit     INT          NOT NULL,
    used_count      INT          DEFAULT 0,
    window_start    DATETIME     NOT NULL,
    window_end      DATETIME     NOT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_window (app_id, window, window_start),
    INDEX idx_window_end (window_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API 配额与限流';

DROP TABLE IF EXISTS open_app_secret_audit;
CREATE TABLE open_app_secret_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    app_id          BIGINT       NOT NULL,
    old_secret_prefix VARCHAR(16) DEFAULT NULL,
    new_secret_prefix VARCHAR(16) DEFAULT NULL,
    secret_hash     VARCHAR(64)  NOT NULL COMMENT 'SHA-256(secret) 用于审计追踪',
    action          VARCHAR(32)  NOT NULL COMMENT 'CREATE / ROTATE / REVOKE',
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app (app_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密钥轮换审计';

-- 预置事件类型
INSERT IGNORE INTO platform_webhook_event (tenant_id, name, description) VALUES
    (1, 'order.paid',         '订单支付成功'),
    (1, 'order.refunded',     '订单退款'),
    (1, 'booking.created',    '预约创建'),
    (1, 'booking.confirmed',  '预约确认'),
    (1, 'member.created',     '会员注册'),
    (1, 'member.updated',     '会员更新'),
    (1, 'review.created',     '评价提交'),
    (1, 'complaint.created',  '投诉创建'),
    (1, 'inventory.low',      '库存低预警'),
    (1, 'payment.received',   '收款成功');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM platform_webhook_event) AS event_count,
    (SELECT COUNT(*) FROM platform_webhook_subscription) AS sub_count,
    (SELECT COUNT(*) FROM open_api_quota) AS quota_count,
    (SELECT COUNT(*) FROM open_app_secret_audit) AS audit_count;