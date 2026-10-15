-- ============================================================
-- Batch 6 子集：crm_contact_map（仅 CREATE TABLE 段）
-- ============================================================
-- 来源脚本：backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql
-- 源段行号：L23-L43（第 23 行注释起，第 43 行 CREATE 末尾）
--           实际行号（Read 工具确认）：L23=注释，L24=DROP，L25-L43=CREATE
-- 源脚本定义（EXT-15）：CRM Contact 双向 ID 映射表
-- 适配说明：
--   1. 保留 DROP TABLE IF EXISTS — 幂等性保护（Batch 6 首次创建时为 no-op）
--   2. 保留 CREATE TABLE 全段 — 13 数据列 + 1 PK + 4 secondary 索引
--   3. 保留 ENGINE / CHARSET / COLLATE / COMMENT — 与源脚本一致
--   4. 不改动 DDL 一字
--   5. 不含任何外键、触发器、种子数据、跨表引用
--   6. 末尾验证 SELECT 已剔除（避免 Batch 4 已 apply 表的验证混淆）
-- 不含：crm_integration 扩展、crm_sync_log 扩展、EXT-15 范围外全部内容
-- 对应功能：
--   - CrmSyncScheduler.loadKnownExternalIds（EXT-24 读 known 集合）
--   - SalesforceCrmAdapter / MockCrmAdapter 写 crm_contact_map
--   - upsertContactMap 业务幂等（platform + externalId 唯一）
-- ============================================================
--
-- ⚠️ 警告：本脚本含 DROP TABLE。
--    如果 crm_contact_map 已有真实数据，DROP 会清空。
--    重跑前必须先备份：
--      mysqldump -h 127.0.0.1 -P 3307 -u root -proot123456 \
--        meiyetec crm_contact_map > crm_contact_map_backup_$(date +%Y%m%d).sql
--    本机（2026-09-21）首次创建时 crm_contact_map 不存在，DROP 为 no-op，无损。
-- ============================================================

DROP TABLE IF EXISTS crm_contact_map;
CREATE TABLE crm_contact_map (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    platform        VARCHAR(64)  NOT NULL COMMENT 'SALESFORCE / HUBSPOT / ...',
    local_contact_id BIGINT      DEFAULT NULL COMMENT 'Local Contact/Member ID',
    external_id     VARCHAR(64)  NOT NULL COMMENT 'platform side ID (Salesforce Contact Id)',
    external_etag   VARCHAR(128) DEFAULT NULL COMMENT 'optimistic lock / platform revision marker',
    sync_direction  VARCHAR(16)  DEFAULT 'BIDIRECTIONAL' COMMENT 'INBOUND / OUTBOUND / BIDIRECTIONAL',
    last_inbound_at  DATETIME    DEFAULT NULL,
    last_outbound_at DATETIME    DEFAULT NULL,
    last_full_sync_at DATETIME   DEFAULT NULL,
    conflict_flag   TINYINT      DEFAULT 0 COMMENT '0=no conflict 1=manual review needed',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_external (platform, external_id),
    INDEX idx_local (local_contact_id),
    INDEX idx_tenant_platform (tenant_id, platform),
    INDEX idx_conflict (conflict_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='CRM Contact bidirectional ID mapping';
