-- ============================================
-- P0 修复：缺失的表（gift_card, member_day_config, subscription_member,
-- report_export_log, crm_tenant）
-- 2026-08-21
-- ============================================

-- ============================================
-- 1. 礼品卡
-- ============================================
DROP TABLE IF EXISTS gift_card;
CREATE TABLE gift_card (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    card_no             VARCHAR(64)  NOT NULL,
    card_type           VARCHAR(32)  DEFAULT 'AMOUNT' COMMENT 'AMOUNT/SERVICE/TIMES',
    face_value          DECIMAL(18,2) DEFAULT 0,
    balance             DECIMAL(18,2) DEFAULT 0,
    service_id          BIGINT       DEFAULT NULL,
    service_count       INT          DEFAULT 0,
    buyer_id            BIGINT       DEFAULT NULL,
    buyer_name          VARCHAR(64)  DEFAULT NULL,
    recipient_name      VARCHAR(64)  DEFAULT NULL,
    recipient_phone     VARCHAR(32)  DEFAULT NULL,
    gift_message        VARCHAR(500) DEFAULT NULL,
    theme               VARCHAR(64)  DEFAULT NULL,
    cover_image         VARCHAR(255) DEFAULT NULL,
    purchase_order_id   BIGINT       DEFAULT NULL,
    purchase_time       DATETIME     DEFAULT NULL,
    expire_time         DATETIME     DEFAULT NULL,
    status              VARCHAR(16)  DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
    use_time            DATETIME     DEFAULT NULL,
    use_order_id        BIGINT       DEFAULT NULL,
    use_store_id        BIGINT       DEFAULT NULL,
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_cardno (tenant_id, card_no),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品卡';

-- ============================================
-- 2. 会员日配置
-- ============================================
DROP TABLE IF EXISTS member_day_config;
CREATE TABLE member_day_config (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    config_name             VARCHAR(128) NOT NULL,
    day_type                VARCHAR(32)  DEFAULT 'BIRTHDAY' COMMENT 'BIRTHDAY/FIXED/ANNIVERSARY',
    trigger_rule            VARCHAR(255) DEFAULT NULL,
    discount_rate           DECIMAL(5,2) DEFAULT 1.00,
    gift_amount             DECIMAL(18,2) DEFAULT 0,
    gift_points             INT          DEFAULT 0,
    gift_coupon_id          BIGINT       DEFAULT NULL,
    free_service_ids        VARCHAR(500) DEFAULT NULL,
    coupon_amount           DECIMAL(18,2) DEFAULT 0,
    target_level            VARCHAR(64)  DEFAULT NULL,
    target_store_ids        VARCHAR(500) DEFAULT NULL,
    birthday_offset_days    INT          DEFAULT 0,
    fixed_month             INT          DEFAULT NULL,
    fixed_day               INT          DEFAULT NULL,
    repeat_annually         TINYINT      DEFAULT 1,
    banner_image            VARCHAR(255) DEFAULT NULL,
    sms_template            VARCHAR(500) DEFAULT NULL,
    push_title              VARCHAR(128) DEFAULT NULL,
    push_content            VARCHAR(500) DEFAULT NULL,
    priority                INT          DEFAULT 100,
    status                  VARCHAR(16)  DEFAULT 'ACTIVE',
    effective_date          DATE         DEFAULT NULL,
    expire_date             DATE         DEFAULT NULL,
    created_at              DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员日配置';

-- ============================================
-- 3. 订阅会员
-- ============================================
DROP TABLE IF EXISTS subscription_member;
CREATE TABLE subscription_member (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    sub_no              VARCHAR(64)  NOT NULL,
    plan_id             BIGINT       DEFAULT NULL,
    plan_code           VARCHAR(64)  DEFAULT NULL,
    member_id           BIGINT       NOT NULL,
    start_date          DATE         NOT NULL,
    next_bill_date      DATE         DEFAULT NULL,
    end_date            DATE         DEFAULT NULL,
    used_count          INT          DEFAULT 0,
    remaining_count     INT          DEFAULT 0,
    auto_renew          TINYINT      DEFAULT 0,
    status              VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/CANCELLED',
    payment_method      VARCHAR(32)  DEFAULT NULL,
    total_paid          DECIMAL(18,2) DEFAULT 0,
    renew_count         INT          DEFAULT 0,
    cancelled_at        DATETIME     DEFAULT NULL,
    cancel_reason       VARCHAR(255) DEFAULT NULL,
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sub_no (sub_no),
    INDEX idx_member (member_id),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅会员';

-- ============================================
-- 4. 报表导出日志
-- ============================================
DROP TABLE IF EXISTS report_export_log;
CREATE TABLE report_export_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        BIGINT       DEFAULT NULL,
    name            VARCHAR(255) NOT NULL,
    type            INT          DEFAULT 1,
    date_range      VARCHAR(64)  DEFAULT NULL,
    file_url        VARCHAR(500) DEFAULT NULL,
    file_size       BIGINT       DEFAULT 0,
    row_count       INT          DEFAULT 0,
    status          INT          DEFAULT 1 COMMENT '1处理中 2成功 3失败',
    error_msg       VARCHAR(1000) DEFAULT NULL,
    export_by       BIGINT       DEFAULT NULL,
    finished_at     DATETIME     DEFAULT NULL,
    file_path       VARCHAR(500) DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表导出日志';

-- ============================================
-- 5. CRM 租户 (crm_tenant 视图同步表)
-- ============================================
DROP TABLE IF EXISTS crm_tenant;
CREATE TABLE crm_tenant (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code         VARCHAR(64)  NOT NULL,
    tenant_name         VARCHAR(128) NOT NULL,
    contact_person      VARCHAR(64)  DEFAULT NULL,
    contact_phone       VARCHAR(32)  DEFAULT NULL,
    province            VARCHAR(32)  DEFAULT NULL,
    city                VARCHAR(32)  DEFAULT NULL,
    address             VARCHAR(255) DEFAULT NULL,
    brand_count         INT          DEFAULT 0,
    store_count         INT          DEFAULT 0,
    member_count        INT          DEFAULT 0,
    plan                VARCHAR(32)  DEFAULT 'STANDARD',
    plan_expire_at      DATETIME     DEFAULT NULL,
    status              VARCHAR(16)  DEFAULT 'ACTIVE',
    logo_url            VARCHAR(255) DEFAULT NULL,
    remark              VARCHAR(500) DEFAULT NULL,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             INT          DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 租户';
INSERT INTO crm_tenant (tenant_code, tenant_name, plan, status)
VALUES ('DEFAULT', 'Default Tenant', 'STANDARD', 'ACTIVE');