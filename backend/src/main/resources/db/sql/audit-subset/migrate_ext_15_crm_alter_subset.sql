-- ============================================================
-- Batch 4 子集脚本：crm_integration 9 列 + crm_sync_log 3 列
-- 来源：migrate_ext_15_crm_real.sql（L7-14 + L38-41）
-- 原始 SHA256：5f40b5e5cd4660ecd4ff073026c750827079680f6b49d811029e44567cb39f20
--
-- 范围：
--   ✅ crm_integration:  9 列 + 2 索引（L7-14）
--   ✅ crm_sync_log:     3 列（L38-41）
--
-- 范围外（已剔除）：
--   ❌ crm_contact_map CREATE TABLE（L16-36）
--   ❌ 末尾验证 SELECT（L43-51）
-- ============================================================

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

-- 2. crm_sync_log：增加 token 字段记录每次同步的游标
ALTER TABLE crm_sync_log
    ADD COLUMN sync_token     VARCHAR(255) DEFAULT NULL,
    ADD COLUMN records_total  INT          DEFAULT 0,
    ADD COLUMN records_filtered INT        DEFAULT 0;
