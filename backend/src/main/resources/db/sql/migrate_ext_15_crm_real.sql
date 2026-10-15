-- ============================================
-- EXT-15：CRM 真实对接
--  1. crm_integration — 扩展 sync_token / sync_schedule / OAuth2 JWT 字段
--  2. crm_contact_map  — 新增 Contact 双向 ID 映射
-- 2026-09-04
-- ============================================

-- 1. crm_integration：扩展 Salesforce OAuth2 JWT Bearer Flow 所需字段
ALTER TABLE crm_integration
    ADD COLUMN sync_token              VARCHAR(255) DEFAULT NULL COMMENT '平台 sync token（增量同步游标）',
    ADD COLUMN sync_schedule           VARCHAR(64)  DEFAULT '0 */30 * * * *' COMMENT 'cron 表达式（默认 30 分钟）',
    ADD COLUMN sync_enabled            TINYINT      DEFAULT 1 COMMENT '0=禁用 1=启用排程',
    ADD COLUMN last_incremental_sync_at DATETIME    DEFAULT NULL COMMENT '上次增量同步时间戳',
    ADD COLUMN private_key             MEDIUMTEXT   DEFAULT NULL COMMENT 'OAuth2 JWT 签名私钥（PEM / 建议加密）',
    ADD COLUMN username                VARCHAR(128) DEFAULT NULL COMMENT '平台账号（Salesforce 用）',
    ADD COLUMN login_url               VARCHAR(255) DEFAULT NULL COMMENT '登录端点（Salesforce: login.salesforce.com / test.salesforce.com）',
    ADD COLUMN object_mappings         TEXT         DEFAULT NULL COMMENT '字段映射 JSON（local_field -> external_field）',
    ADD COLUMN sync_window_minutes     INT          DEFAULT 30 COMMENT '增量同步窗口（分钟），默认 30',
    ADD INDEX idx_sync_enabled (sync_enabled),
    ADD INDEX idx_platform_status (platform, status);

-- 2. crm_contact_map：双向 ID 映射表
DROP TABLE IF EXISTS crm_contact_map;
CREATE TABLE crm_contact_map (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    platform        VARCHAR(64)  NOT NULL COMMENT 'SALESFORCE / HUBSPOT / ...',
    local_contact_id BIGINT      DEFAULT NULL COMMENT '本系统 Contact/Member ID',
    external_id     VARCHAR(64)  NOT NULL COMMENT '平台侧 ID（Salesforce Contact Id）',
    external_etag   VARCHAR(128) DEFAULT NULL COMMENT '乐观锁 / 平台 revision marker',
    sync_direction  VARCHAR(16)  DEFAULT 'BIDIRECTIONAL' COMMENT 'INBOUND / OUTBOUND / BIDIRECTIONAL',
    last_inbound_at  DATETIME    DEFAULT NULL,
    last_outbound_at DATETIME    DEFAULT NULL,
    last_full_sync_at DATETIME   DEFAULT NULL,
    conflict_flag   TINYINT      DEFAULT 0 COMMENT '0=无冲突 1=有冲突待人工',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_external (platform, external_id),
    INDEX idx_local (local_contact_id),
    INDEX idx_tenant_platform (tenant_id, platform),
    INDEX idx_conflict (conflict_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='CRM Contact 双向 ID 映射';

-- 3. crm_sync_log：增加 token 字段记录每次同步的游标
ALTER TABLE crm_sync_log
    ADD COLUMN sync_token     VARCHAR(255) DEFAULT NULL,
    ADD COLUMN records_total  INT          DEFAULT 0,
    ADD COLUMN records_filtered INT        DEFAULT 0;

-- 4. 验证
SELECT
    (SELECT COUNT(*) FROM crm_integration) AS integ_count,
    (SELECT COUNT(*) FROM crm_contact_map) AS map_count,
    (SELECT COUNT(*) FROM crm_sync_log)    AS log_count;
