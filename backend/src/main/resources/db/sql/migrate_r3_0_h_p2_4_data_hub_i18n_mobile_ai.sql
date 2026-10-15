-- ============================================
-- R3.0-H P2.4 数据中台 + i18n + 移动API + AI增强
--  1. i18n_locale（多语言字典）
--  2. i18n_translation（翻译条目）
--  3. mobile_device（移动端设备绑定）
--  4. ai_recommend_cache（推荐缓存）
--  5. ai_sales_forecast（销售预测）
--  6. ai_review_summary（评价 NLP 摘要）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS i18n_locale;
CREATE TABLE i18n_locale (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    locale_code     VARCHAR(16)  NOT NULL COMMENT 'zh-CN / en-US / ja-JP 等',
    locale_name     VARCHAR(64)  NOT NULL COMMENT '显示名（简体中文 / English）',
    native_name     VARCHAR(64)  DEFAULT NULL COMMENT '本地名（中文 / English）',
    is_default      TINYINT      DEFAULT 0,
    enabled         TINYINT      DEFAULT 1,
    rtl             TINYINT      DEFAULT 0 COMMENT '从右到左阅读（阿拉伯语等）',
    sort_order      INT          DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_id, locale_code),
    deleted         TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语言列表';

DROP TABLE IF EXISTS i18n_translation;
CREATE TABLE i18n_translation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    locale_code     VARCHAR(16)  NOT NULL,
    message_key     VARCHAR(128) NOT NULL COMMENT 'key（如 common.confirm / service.skin_care）',
    message_value   TEXT         NOT NULL COMMENT '翻译值',
    module          VARCHAR(64)  DEFAULT NULL COMMENT '模块名（common/order/member...）',
    description     VARCHAR(500) DEFAULT NULL COMMENT '原文/语境说明',
    enabled         TINYINT      DEFAULT 1,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_locale_key (tenant_id, locale_code, message_key),
    INDEX idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='翻译条目';

DROP TABLE IF EXISTS mobile_device;
CREATE TABLE mobile_device (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    user_id         BIGINT       DEFAULT NULL,
    device_id       VARCHAR(128) NOT NULL COMMENT '设备唯一标识（push token 或 deviceId）',
    platform        VARCHAR(32)  NOT NULL COMMENT 'iOS / Android / MiniProgram / H5',
    push_token      VARCHAR(255) DEFAULT NULL,
    app_version     VARCHAR(32)  DEFAULT NULL,
    os_version      VARCHAR(32)  DEFAULT NULL,
    device_model    VARCHAR(64)  DEFAULT NULL,
    last_login_at   DATETIME     DEFAULT NULL,
    login_count     INT          DEFAULT 0,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device (tenant_id, device_id),
    INDEX idx_user (user_id, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移动端设备';

DROP TABLE IF EXISTS ai_recommend_cache;
CREATE TABLE ai_recommend_cache (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    member_id       BIGINT       NOT NULL,
    recommend_type  VARCHAR(32)  NOT NULL COMMENT 'SERVICE / PRODUCT / COUPON / CONTENT',
    recommend_items TEXT         NOT NULL COMMENT 'JSON 推荐结果',
    algorithm       VARCHAR(64)  DEFAULT 'COLLAB-V1' COMMENT 'COLLAB-V1 / CONTENT-V1 / HOT-V1',
    score           DECIMAL(10,4) DEFAULT 0,
    cache_key       VARCHAR(128) DEFAULT NULL,
    expires_at      DATETIME     DEFAULT NULL,
    hit_count       INT          DEFAULT 0,
    last_hit_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_type (tenant_id, member_id, recommend_type),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐缓存';

DROP TABLE IF EXISTS ai_sales_forecast;
CREATE TABLE ai_sales_forecast (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       DEFAULT NULL,
    forecast_date   DATE         NOT NULL,
    forecast_type   VARCHAR(32)  NOT NULL COMMENT 'REVENUE / ORDERS / MEMBERS / NEW_CUSTOMERS',
    predicted_value DECIMAL(20,4) NOT NULL,
    upper_bound     DECIMAL(20,4) DEFAULT NULL,
    lower_bound     DECIMAL(20,4) DEFAULT NULL,
    confidence      DECIMAL(5,4) DEFAULT 0.8 COMMENT '0.0~1.0',
    algorithm       VARCHAR(64)  DEFAULT 'LINEAR-XS-V1' COMMENT 'LINEAR / EWMA / HOLT_WINTERS / ENSEMBLE',
    base_window_days INT         DEFAULT 90 COMMENT '训练窗口',
    input_features  TEXT         DEFAULT NULL COMMENT 'JSON 训练特征',
    comment         VARCHAR(500) DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_type_date (tenant_id, forecast_type, forecast_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售预测';

DROP TABLE IF EXISTS ai_review_summary;
CREATE TABLE ai_review_summary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    summary_key     VARCHAR(64)  NOT NULL COMMENT '日期/范围 key（如 2026-08 / 2026-W32 / last_7d）',
    summary_type    VARCHAR(32)  NOT NULL COMMENT 'DAILY / WEEKLY / MONTHLY / RANGE',
    total_reviews   INT          DEFAULT 0,
    positive_count  INT          DEFAULT 0,
    neutral_count   INT          DEFAULT 0,
    negative_count  INT          DEFAULT 0,
    avg_rating      DECIMAL(3,2) DEFAULT 0,
    nps             DECIMAL(5,2) DEFAULT 0,
    positive_keywords   TEXT     DEFAULT NULL COMMENT 'JSON 词频 [{word, count}]',
    negative_keywords   TEXT     DEFAULT NULL,
    summary_text    TEXT         DEFAULT NULL COMMENT 'AI 摘要文本',
    sentiment_score DECIMAL(5,4) DEFAULT 0 COMMENT '-1.0 ~ 1.0',
    algorithm       VARCHAR(64)  DEFAULT 'NLP-KEYWORD-V1',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_key (tenant_id, summary_key, summary_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价 NLP 摘要';

-- 预置语言
INSERT IGNORE INTO i18n_locale (tenant_id, locale_code, locale_name, native_name, is_default, sort_order) VALUES
    (1, 'zh-CN', '简体中文', '简体中文', 1, 1),
    (1, 'zh-TW', '繁體中文', '繁體中文', 0, 2),
    (1, 'en-US', 'English', 'English', 0, 3),
    (1, 'ja-JP', '日本語', '日本語', 0, 4),
    (1, 'ko-KR', '한국어', '한국어', 0, 5),
    (1, 'th-TH', 'ภาษาไทย', 'ภาษาไทย', 0, 6),
    (1, 'vi-VN', 'Tiếng Việt', 'Tiếng Việt', 0, 7);

-- 预置核心翻译（zh-CN / en-US）
INSERT IGNORE INTO i18n_translation (tenant_id, locale_code, message_key, message_value, module) VALUES
    (1, 'zh-CN', 'common.confirm', '确认', 'common'),
    (1, 'zh-CN', 'common.cancel',  '取消', 'common'),
    (1, 'zh-CN', 'common.save',    '保存', 'common'),
    (1, 'zh-CN', 'common.delete',  '删除', 'common'),
    (1, 'zh-CN', 'order.created',  '订单已创建', 'order'),
    (1, 'zh-CN', 'order.paid',     '订单已支付', 'order'),
    (1, 'zh-CN', 'member.welcome', '欢迎光临', 'member'),
    (1, 'en-US', 'common.confirm', 'Confirm', 'common'),
    (1, 'en-US', 'common.cancel',  'Cancel', 'common'),
    (1, 'en-US', 'common.save',    'Save', 'common'),
    (1, 'en-US', 'common.delete',  'Delete', 'common'),
    (1, 'en-US', 'order.created',  'Order Created', 'order'),
    (1, 'en-US', 'order.paid',     'Order Paid', 'order'),
    (1, 'en-US', 'member.welcome', 'Welcome', 'member');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM i18n_locale) AS locale_count,
    (SELECT COUNT(*) FROM i18n_translation) AS trans_count,
    (SELECT COUNT(*) FROM mobile_device) AS device_count,
    (SELECT COUNT(*) FROM ai_recommend_cache) AS rec_count,
    (SELECT COUNT(*) FROM ai_sales_forecast) AS forecast_count,
    (SELECT COUNT(*) FROM ai_review_summary) AS summary_count;