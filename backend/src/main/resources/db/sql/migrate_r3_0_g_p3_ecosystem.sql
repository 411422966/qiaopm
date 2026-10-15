-- ============================================
-- R3.0-G P3：生态/国际化/AI 平台（最小骨架）
--  1. 微信小程序预约（wx_*）
--  2. 美团/抖音对接（platform_*）
--  3. Open API 开发者平台（open_*）
--  4. IoT 设备集成（iot_*）
-- 2026-08-14
-- ============================================

-- ============================================
-- 1. 微信小程序预约
-- ============================================
DROP TABLE IF EXISTS wx_member_bind;
CREATE TABLE wx_member_bind (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    member_id       BIGINT       NOT NULL,
    appid           VARCHAR(64)  NOT NULL,
    openid          VARCHAR(64)  NOT NULL,
    unionid         VARCHAR(64)  DEFAULT NULL,
    nickname        VARCHAR(128) DEFAULT NULL,
    avatar_url      VARCHAR(500) DEFAULT NULL,
    phone           VARCHAR(20)  DEFAULT NULL,
    session_key     VARCHAR(64)  DEFAULT NULL,
    bind_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    last_login_at   DATETIME     DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_app_open (appid, openid, deleted),
    INDEX idx_member (member_id, enabled, deleted),
    INDEX idx_unionid (unionid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信小程序-会员绑定';

DROP TABLE IF EXISTS wx_booking_record;
CREATE TABLE wx_booking_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    appid           VARCHAR(64)  NOT NULL,
    openid          VARCHAR(64)  NOT NULL,
    member_id       BIGINT       DEFAULT NULL,
    store_id        BIGINT       NOT NULL,
    service_id      BIGINT       DEFAULT NULL,
    service_name    VARCHAR(128) DEFAULT NULL,
    employee_id     BIGINT       DEFAULT NULL,
    employee_name   VARCHAR(64)  DEFAULT NULL,
    booking_date    DATE         NOT NULL,
    booking_time    DATETIME     NOT NULL,
    duration_minutes INT         DEFAULT 60,
    contact_phone   VARCHAR(20)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/COMPLETED/CANCELLED',
    confirmed_at    DATETIME     DEFAULT NULL,
    completed_at    DATETIME     DEFAULT NULL,
    cancelled_at    DATETIME     DEFAULT NULL,
    source          VARCHAR(32)  DEFAULT 'WX_MINI',
    operator_id     BIGINT       DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_open (appid, openid, status),
    INDEX idx_store_date (store_id, booking_date, status),
    INDEX idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信小程序预约记录';

DROP TABLE IF EXISTS wx_app_config;
CREATE TABLE wx_app_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       DEFAULT NULL,
    appid           VARCHAR(64)  NOT NULL,
    app_name        VARCHAR(128) NOT NULL,
    app_secret      VARCHAR(128) DEFAULT NULL COMMENT '密文存储',
    mch_id          VARCHAR(64)  DEFAULT NULL COMMENT '微信支付商户号',
    mch_key         VARCHAR(255) DEFAULT NULL COMMENT '密文存储',
    template_ids    TEXT         DEFAULT NULL COMMENT 'JSON 订阅消息模板',
    enabled         TINYINT      DEFAULT 1,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_app (tenant_id, appid, deleted),
    INDEX idx_brand (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信小程序配置';

-- ============================================
-- 2. 美团/抖音 第三方平台对接
-- ============================================
DROP TABLE IF EXISTS platform_order;
CREATE TABLE platform_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       NOT NULL,
    platform        VARCHAR(32)  NOT NULL COMMENT 'MEITUAN/DOUYIN/OTHER',
    platform_order_id VARCHAR(64) NOT NULL,
    platform_deal_id VARCHAR(64) DEFAULT NULL,
    deal_name       VARCHAR(255) DEFAULT NULL,
    customer_name   VARCHAR(64)  DEFAULT NULL,
    customer_phone  VARCHAR(20)  DEFAULT NULL,
    quantity        INT          DEFAULT 1,
    amount          DECIMAL(12,2) DEFAULT 0,
    commission      DECIMAL(12,2) DEFAULT 0,
    verification_code VARCHAR(16) DEFAULT NULL COMMENT '核销码',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/VERIFIED/REFUNDED/CANCELLED',
    verified_at     DATETIME     DEFAULT NULL,
    refunded_at     DATETIME     DEFAULT NULL,
    raw_payload     TEXT         DEFAULT NULL COMMENT '原始 webhook JSON',
    member_id       BIGINT       DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_platform_order (platform, platform_order_id, deleted),
    INDEX idx_store_status (store_id, status),
    INDEX idx_verification (verification_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台订单';

DROP TABLE IF EXISTS platform_deal;
CREATE TABLE platform_deal (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       NOT NULL,
    platform        VARCHAR(32)  NOT NULL COMMENT 'MEITUAN/DOUYIN',
    platform_deal_id VARCHAR(64) NOT NULL,
    deal_name       VARCHAR(255) NOT NULL,
    original_price  DECIMAL(12,2) DEFAULT 0,
    sale_price      DECIMAL(12,2) DEFAULT 0,
    stock           INT          DEFAULT 0,
    sync_status     VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SYNCED/FAILED',
    sync_at         DATETIME     DEFAULT NULL,
    last_pull_at    DATETIME     DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_deal (platform, platform_deal_id, deleted),
    INDEX idx_store (store_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台券/团购';

DROP TABLE IF EXISTS platform_webhook_log;
CREATE TABLE platform_webhook_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform        VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload_hash    VARCHAR(64)  DEFAULT NULL,
    raw_body        TEXT         DEFAULT NULL,
    processed       TINYINT      DEFAULT 0,
    processed_msg   VARCHAR(500) DEFAULT NULL,
    received_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_platform_event (platform, event_type, processed),
    INDEX idx_hash (payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方 webhook 日志';

-- ============================================
-- 3. Open API 开发者平台
-- ============================================
DROP TABLE IF EXISTS open_app;
CREATE TABLE open_app (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    app_code        VARCHAR(64)  NOT NULL,
    app_name        VARCHAR(128) NOT NULL,
    app_secret      VARCHAR(128) NOT NULL COMMENT '密文存储',
    app_type        VARCHAR(32)  DEFAULT 'PARTNER' COMMENT 'PARTNER 合作伙伴 / INTERNAL 内部',
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/REVOKED',
    contact_email   VARCHAR(128) DEFAULT NULL,
    contact_name    VARCHAR(64)  DEFAULT NULL,
    rate_limit      INT          DEFAULT 1000 COMMENT '每分钟请求数',
    allowed_ips     VARCHAR(1000) DEFAULT NULL COMMENT '逗号分隔',
    scopes          VARCHAR(500) DEFAULT NULL COMMENT '逗号分隔权限码',
    approved_at     DATETIME     DEFAULT NULL,
    expires_at      DATETIME     DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_appcode (tenant_id, app_code, deleted),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放平台应用';

DROP TABLE IF EXISTS open_access_token;
CREATE TABLE open_access_token (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id          BIGINT       NOT NULL,
    access_token    VARCHAR(128) NOT NULL,
    refresh_token   VARCHAR(128) DEFAULT NULL,
    expires_at      DATETIME     NOT NULL,
    refresh_expires_at DATETIME  DEFAULT NULL,
    scopes          VARCHAR(500) DEFAULT NULL,
    client_ip       VARCHAR(64)  DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app (app_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放平台令牌';

DROP TABLE IF EXISTS open_api_log;
CREATE TABLE open_api_log (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    app_id          BIGINT       DEFAULT NULL,
    app_code        VARCHAR(64)  DEFAULT NULL,
    method          VARCHAR(10)  DEFAULT NULL,
    path            VARCHAR(255) DEFAULT NULL,
    request_id      VARCHAR(64)  DEFAULT NULL,
    client_ip       VARCHAR(64)  DEFAULT NULL,
    http_status     INT          DEFAULT 0,
    duration_ms     INT          DEFAULT 0,
    error_code      VARCHAR(32)  DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    request_body    TEXT         DEFAULT NULL,
    response_body   MEDIUMTEXT   DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_created (app_id, created_at),
    INDEX idx_path (path, http_status),
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放平台 API 调用日志';

-- ============================================
-- 4. IoT 设备集成
-- ============================================
DROP TABLE IF EXISTS iot_device;
CREATE TABLE iot_device (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       DEFAULT NULL,
    device_sn       VARCHAR(64)  NOT NULL COMMENT '设备序列号',
    device_type     VARCHAR(32)  NOT NULL COMMENT 'LOCKER 智能柜/SCALE 称重/MIRROR 魔镜/CAMERA 摄像头/...',
    device_name     VARCHAR(128) DEFAULT NULL,
    vendor          VARCHAR(64)  DEFAULT NULL COMMENT '厂商',
    model           VARCHAR(64)  DEFAULT NULL,
    firmware        VARCHAR(32)  DEFAULT NULL,
    mac_address     VARCHAR(64)  DEFAULT NULL,
    ip_address      VARCHAR(64)  DEFAULT NULL,
    online_status   VARCHAR(16)  DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE/FAULT',
    last_heartbeat  DATETIME     DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    remark          VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_sn (tenant_id, device_sn, deleted),
    INDEX idx_store (store_id, device_type, online_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 设备';

DROP TABLE IF EXISTS iot_device_event;
CREATE TABLE iot_device_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    device_id       BIGINT       NOT NULL,
    device_sn       VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL COMMENT 'HEARTBEAT/UNLOCK/SCALE/CAPTURE/ERROR/...',
    event_data      TEXT         DEFAULT NULL COMMENT 'JSON',
    severity        VARCHAR(16)  DEFAULT 'INFO' COMMENT 'INFO/WARN/ERROR',
    handled         TINYINT      DEFAULT 0,
    handled_at      DATETIME     DEFAULT NULL,
    handled_remark  VARCHAR(500) DEFAULT NULL,
    occurred_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device (device_id, occurred_at),
    INDEX idx_type (event_type, handled),
    INDEX idx_sn (device_sn, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备事件流';

DROP TABLE IF EXISTS iot_device_command;
CREATE TABLE iot_device_command (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    device_id       BIGINT       NOT NULL,
    command_code    VARCHAR(64)  NOT NULL COMMENT 'UNLOCK/RESTART/UPGRADE/RESET/...',
    payload         TEXT         DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SENT/SUCCESS/FAILED/TIMEOUT',
    sent_at         DATETIME     DEFAULT NULL,
    ack_at          DATETIME     DEFAULT NULL,
    result          TEXT         DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_status (device_id, status),
    INDEX idx_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下行指令队列';

-- ============================================
-- 权限
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'wx:booking', '微信预约', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:channel') t LIMIT 1), 100;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'platform:order', '平台订单', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:channel') t LIMIT 1), 101;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'open:platform', '开放平台', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='sys:config') t LIMIT 1), 80;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'iot:device', 'IoT 设备', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:report') t LIMIT 1), 100;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('wx:booking','platform:order','open:platform','iot:device');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM wx_member_bind WHERE deleted=0) AS wx_bind,
    (SELECT COUNT(*) FROM wx_app_config WHERE deleted=0) AS wx_config,
    (SELECT COUNT(*) FROM platform_order WHERE deleted=0) AS platform_orders,
    (SELECT COUNT(*) FROM open_app WHERE deleted=0) AS open_apps,
    (SELECT COUNT(*) FROM iot_device WHERE deleted=0) AS devices;