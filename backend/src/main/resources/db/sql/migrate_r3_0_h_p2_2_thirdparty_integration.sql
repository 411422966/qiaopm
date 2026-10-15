-- ============================================
-- R3.0-H P2.2 第三方集成补强
--  1. wx_pay_order（微信支付订单）
--  2. wx_template_log（模板消息发送日志）
--  3. platform_sync_log（美团/抖音拉单同步日志）
--  4. platform_booking（美团/抖音预约同步）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS wx_pay_order;
CREATE TABLE wx_pay_order (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    appid               VARCHAR(64)  NOT NULL,
    out_trade_no        VARCHAR(64)  NOT NULL COMMENT '商户订单号',
    transaction_id      VARCHAR(64)  DEFAULT NULL COMMENT '微信支付订单号',
    openid              VARCHAR(64)  DEFAULT NULL,
    member_id           BIGINT       DEFAULT NULL,
    store_id            BIGINT       DEFAULT NULL,
    total_fee           INT          DEFAULT 0 COMMENT '金额（分）',
    body                VARCHAR(255) DEFAULT NULL,
    status              VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/REFUND/CLOSED/FAIL',
    paid_at             DATETIME     DEFAULT NULL,
    notify_payload      TEXT         DEFAULT NULL COMMENT '支付回调原文',
    notify_count        INT          DEFAULT 0,
    operator_name       VARCHAR(64)  DEFAULT NULL,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_out_trade (tenant_id, appid, out_trade_no),
    INDEX idx_member (member_id, status),
    INDEX idx_status (status, paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信支付订单';

DROP TABLE IF EXISTS wx_template_log;
CREATE TABLE wx_template_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    appid           VARCHAR(64)  NOT NULL,
    openid          VARCHAR(64)  NOT NULL,
    template_id     VARCHAR(64)  NOT NULL,
    template_data   TEXT         DEFAULT NULL COMMENT 'JSON',
    msg_id          VARCHAR(64)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAIL',
    err_code        VARCHAR(64)  DEFAULT NULL,
    err_msg         VARCHAR(500) DEFAULT NULL,
    send_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_openid (openid, send_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信模板消息日志';

DROP TABLE IF EXISTS platform_sync_log;
CREATE TABLE platform_sync_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    platform        VARCHAR(32)  NOT NULL COMMENT 'MEITUAN/DOUYIN',
    sync_type       VARCHAR(32)  NOT NULL COMMENT 'ORDER_PULL / BOOKING_PULL / STATUS_PUSH',
    start_time      DATETIME     DEFAULT NULL,
    end_time        DATETIME     DEFAULT NULL,
    total           INT          DEFAULT 0,
    success         INT          DEFAULT 0,
    fail            INT          DEFAULT 0,
    detail          TEXT         DEFAULT NULL COMMENT 'JSON 详情',
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_platform_type (platform, sync_type, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台同步日志';

DROP TABLE IF EXISTS platform_booking;
CREATE TABLE platform_booking (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    platform            VARCHAR(32)  NOT NULL COMMENT 'MEITUAN/DOUYIN',
    platform_booking_id VARCHAR(64)  NOT NULL COMMENT '平台预约 ID',
    store_id            BIGINT       DEFAULT NULL,
    member_id           BIGINT       DEFAULT NULL,
    customer_name       VARCHAR(64)  DEFAULT NULL,
    customer_phone      VARCHAR(32)  DEFAULT NULL,
    service_name        VARCHAR(255) DEFAULT NULL,
    booking_date        DATE         DEFAULT NULL,
    booking_time        DATETIME     DEFAULT NULL,
    status              VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/COMPLETED/CANCELLED',
    amount              DECIMAL(10,2) DEFAULT 0,
    raw_payload         TEXT         DEFAULT NULL,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_booking (tenant_id, platform, platform_booking_id),
    INDEX idx_store_date (store_id, booking_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='美团/抖音预约同步';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM wx_pay_order) AS pay_count,
    (SELECT COUNT(*) FROM wx_template_log) AS tpl_count,
    (SELECT COUNT(*) FROM platform_sync_log) AS sync_count,
    (SELECT COUNT(*) FROM platform_booking) AS pb_count;