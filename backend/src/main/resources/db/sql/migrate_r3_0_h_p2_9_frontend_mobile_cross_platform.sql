-- ============================================
-- R3.0-H P2.9 前端 + 移动端 + 多端协同 + 跨端架构
--  1. cross_platform（跨端平台定义）
--  2. cross_platform_sdk（跨端 SDK 版本）
--  3. cross_platform_capability（端能力探测）
--  4. device_binding（设备绑定）
--  5. device_session（设备会话）
--  6. device_command（远程命令）
--  7. sync_session（多端同步会话）
--  8. sync_session_member（同步会话成员）
--  9. sync_event（同步事件）
-- 10. push_template（推送模板）
-- 11. push_record（推送记录）
-- 12. web_template（前端模板）
-- 13. web_route_audit（前端路由审计）
-- 14. mini_program_version（小程序版本）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS cross_platform;
CREATE TABLE cross_platform (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_code   VARCHAR(32)  NOT NULL COMMENT 'WEB / H5 / MP_WEIXIN / MP_ALIPAY / MP_BYTEDANCE / APP_IOS / APP_ANDROID / PAD',
    platform_name   VARCHAR(64)  NOT NULL,
    platform_type   VARCHAR(16)  DEFAULT 'WEB' COMMENT 'WEB / H5 / MP / APP / PAD',
    framework       VARCHAR(32)  DEFAULT NULL COMMENT 'vue / react / uniapp / taro',
    min_version     VARCHAR(16)  DEFAULT NULL,
    sdk_version     VARCHAR(32)  DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    icon_url        VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_code (platform_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨端平台定义';

DROP TABLE IF EXISTS cross_platform_sdk;
CREATE TABLE cross_platform_sdk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_code   VARCHAR(32)  NOT NULL,
    sdk_version     VARCHAR(32)  NOT NULL,
    download_url    VARCHAR(500) DEFAULT NULL,
    file_hash       VARCHAR(128) DEFAULT NULL,
    file_size       BIGINT       DEFAULT 0,
    changelog       TEXT         DEFAULT NULL,
    force_upgrade   TINYINT      DEFAULT 0,
    min_client      VARCHAR(16)  DEFAULT NULL,
    deprecated      TINYINT      DEFAULT 0,
    released_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_version (platform_code, sdk_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SDK 版本';

DROP TABLE IF EXISTS cross_platform_capability;
CREATE TABLE cross_platform_capability (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_code   VARCHAR(32)  NOT NULL,
    capability_key  VARCHAR(64)  NOT NULL COMMENT 'camera / gps / biometric / push / nfc / ble',
    capability_name VARCHAR(64)  DEFAULT NULL,
    supported       TINYINT      DEFAULT 1,
    version         VARCHAR(16)  DEFAULT NULL,
    extra_config    VARCHAR(500) DEFAULT NULL COMMENT 'JSON',
    UNIQUE KEY uk_platform_capability (platform_code, capability_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='端能力';

DROP TABLE IF EXISTS device_binding;
CREATE TABLE device_binding (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    user_id         BIGINT       NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    platform_code   VARCHAR(32)  NOT NULL,
    device_name     VARCHAR(128) DEFAULT NULL,
    device_model    VARCHAR(64)  DEFAULT NULL,
    os_version      VARCHAR(32)  DEFAULT NULL,
    app_version     VARCHAR(32)  DEFAULT NULL,
    push_token      VARCHAR(255) DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / OFFLINE / REVOKED',
    last_seen_at    DATETIME     DEFAULT NULL,
    ip              VARCHAR(64)  DEFAULT NULL,
    bind_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备绑定';

DROP TABLE IF EXISTS device_session;
CREATE TABLE device_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    platform_code   VARCHAR(32)  NOT NULL,
    session_token   VARCHAR(255) NOT NULL,
    refresh_token   VARCHAR(255) DEFAULT NULL,
    expires_at      DATETIME     NOT NULL,
    ip              VARCHAR(64)  DEFAULT NULL,
    user_agent      VARCHAR(500) DEFAULT NULL,
    active          TINYINT      DEFAULT 1,
    started_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    ended_at        DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_session_token (session_token),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备会话';

DROP TABLE IF EXISTS device_command;
CREATE TABLE device_command (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    device_id       VARCHAR(128) NOT NULL,
    command_type    VARCHAR(32)  NOT NULL COMMENT 'REFRESH / LOCK / LOGOUT / PUSH / WIPE',
    payload         TEXT         DEFAULT NULL COMMENT 'JSON',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING / DELIVERED / ACK / FAILED',
    priority        INT          DEFAULT 100,
    expires_at      DATETIME     DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    delivered_at    DATETIME     DEFAULT NULL,
    acked_at        DATETIME     DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    INDEX idx_device_status (device_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='远程命令';

DROP TABLE IF EXISTS sync_session;
CREATE TABLE sync_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    session_key     VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    device_count    INT          DEFAULT 0,
    started_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    ended_at        DATETIME     DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT 'ACTIVE / CLOSED / EXPIRED',
    last_event_at   DATETIME     DEFAULT NULL,
    event_count     INT          DEFAULT 0,
    UNIQUE KEY uk_session_key (session_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步会话';

DROP TABLE IF EXISTS sync_session_member;
CREATE TABLE sync_session_member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key     VARCHAR(64)  NOT NULL,
    device_id       VARCHAR(128) NOT NULL,
    platform_code   VARCHAR(32)  NOT NULL,
    joined_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    left_at         DATETIME     DEFAULT NULL,
    active          TINYINT      DEFAULT 1,
    INDEX idx_session (session_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步会话成员';

DROP TABLE IF EXISTS sync_event;
CREATE TABLE sync_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key     VARCHAR(64)  NOT NULL,
    from_device_id  VARCHAR(128) NOT NULL,
    event_type      VARCHAR(32)  NOT NULL COMMENT 'NAV / FOCUS / STATE_CHANGE / ACTION',
    entity_type     VARCHAR(32)  DEFAULT NULL,
    entity_id       VARCHAR(64)  DEFAULT NULL,
    payload         TEXT         DEFAULT NULL,
    occurred_at     DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_session_time (session_key, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步事件';

DROP TABLE IF EXISTS push_template;
CREATE TABLE push_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    template_code   VARCHAR(64)  NOT NULL,
    title           VARCHAR(128) NOT NULL,
    content         VARCHAR(500) DEFAULT NULL,
    platform_codes  VARCHAR(255) DEFAULT NULL COMMENT '逗号分隔 支持平台',
    channel         VARCHAR(32)  DEFAULT 'NOTICE' COMMENT 'NOTICE / MARKETING / VERIFY / ALERT',
    deep_link       VARCHAR(255) DEFAULT NULL,
    icon_url        VARCHAR(255) DEFAULT NULL,
    sound           VARCHAR(64)  DEFAULT 'default',
    vibration       TINYINT      DEFAULT 1,
    enabled         TINYINT      DEFAULT 1,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推送模板';

DROP TABLE IF EXISTS push_record;
CREATE TABLE push_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    template_code   VARCHAR(64)  DEFAULT NULL,
    user_id         BIGINT       NOT NULL,
    device_id       VARCHAR(128) DEFAULT NULL,
    platform_code   VARCHAR(32)  DEFAULT NULL,
    channel         VARCHAR(32)  DEFAULT 'NOTICE',
    title           VARCHAR(128) DEFAULT NULL,
    content         VARCHAR(500) DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED / READ',
    error_message   VARCHAR(500) DEFAULT NULL,
    sent_at         DATETIME     DEFAULT NULL,
    read_at         DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推送记录';

DROP TABLE IF EXISTS web_template;
CREATE TABLE web_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code   VARCHAR(64)  NOT NULL,
    template_name   VARCHAR(128) NOT NULL,
    platform_code   VARCHAR(32)  DEFAULT 'WEB',
    framework       VARCHAR(16)  DEFAULT 'vue' COMMENT 'vue / react / uniapp / taro',
    description     VARCHAR(500) DEFAULT NULL,
    pages_json      TEXT         DEFAULT NULL COMMENT 'JSON pages 定义',
    theme           VARCHAR(32)  DEFAULT 'default',
    version         VARCHAR(16)  DEFAULT '1.0.0',
    enabled         TINYINT      DEFAULT 1,
    download_url    VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前端模板';

DROP TABLE IF EXISTS web_route_audit;
CREATE TABLE web_route_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    user_id         BIGINT       DEFAULT NULL,
    device_id       VARCHAR(128) DEFAULT NULL,
    platform_code   VARCHAR(32)  DEFAULT NULL,
    route_path      VARCHAR(255) NOT NULL,
    route_name      VARCHAR(64)  DEFAULT NULL,
    duration_ms     INT          DEFAULT 0,
    api_calls       INT          DEFAULT 0,
    ip              VARCHAR(64)  DEFAULT NULL,
    user_agent      VARCHAR(500) DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    visited_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user_time (user_id, visited_at),
    INDEX idx_route_time (route_path, visited_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前端路由审计';

DROP TABLE IF EXISTS mini_program_version;
CREATE TABLE mini_program_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_code   VARCHAR(32)  NOT NULL COMMENT 'MP_WEIXIN / MP_ALIPAY / MP_BYTEDANCE',
    version_code    VARCHAR(32)  NOT NULL,
    version_name    VARCHAR(64)  NOT NULL,
    download_url    VARCHAR(500) DEFAULT NULL,
    qr_code_url     VARCHAR(500) DEFAULT NULL,
    force_upgrade   TINYINT      DEFAULT 0,
    min_client      VARCHAR(16)  DEFAULT NULL,
    grayscale       INT          DEFAULT 0 COMMENT '灰度比例 0-100',
    status          VARCHAR(16)  DEFAULT 'DRAFT' COMMENT 'DRAFT / TESTING / RELEASED / ROLLBACK',
    released_at     DATETIME     DEFAULT NULL,
    changelog       TEXT         DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_version (platform_code, version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序版本';

-- ============================================
-- 预置 5 个跨端平台
-- ============================================
INSERT IGNORE INTO cross_platform (platform_code, platform_name, platform_type, framework) VALUES
    ('WEB',          'PC Web',         'WEB', 'vue'),
    ('H5',           '移动 H5',         'H5',  'vue'),
    ('MP_WEIXIN',    '微信小程序',       'MP',  'uniapp'),
    ('MP_ALIPAY',    '支付宝小程序',     'MP',  'uniapp'),
    ('APP_IOS',      'iOS 原生',        'APP', NULL),
    ('APP_ANDROID',  'Android 原生',    'APP', NULL),
    ('PAD',          'PAD 大屏',        'PAD', 'vue');

-- ============================================
-- 预置 8 种端能力
-- ============================================
INSERT IGNORE INTO cross_platform_capability (platform_code, capability_key, capability_name) VALUES
    ('WEB',          'camera',     '摄像头'),
    ('WEB',          'push',       '推送'),
    ('H5',           'gps',        '定位'),
    ('H5',           'camera',     '摄像头'),
    ('H5',           'biometric',  '生物识别'),
    ('MP_WEIXIN',    'gps',        '定位'),
    ('MP_WEIXIN',    'camera',     '摄像头'),
    ('APP_IOS',      'nfc',        'NFC'),
    ('APP_IOS',      'biometric',  'Face ID'),
    ('APP_ANDROID',  'nfc',        'NFC'),
    ('APP_ANDROID',  'biometric',  '指纹');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM cross_platform) AS platform_count,
    (SELECT COUNT(*) FROM cross_platform_capability) AS cap_count,
    (SELECT COUNT(*) FROM web_template) AS template_count;