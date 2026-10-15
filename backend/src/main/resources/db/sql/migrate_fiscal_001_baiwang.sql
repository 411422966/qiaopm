-- ============================================
-- FISCAL-001：财税直连深化（百望云 / 航天信息）
--  1. fiscal_config  —— 开票渠道凭据（参考 dcep_config）
--  2. fiscal_invoice —— 扩展平台响应字段（platformInvoiceNo / pdfUrl / requestId / response）
-- 2026-09-04
-- ============================================

-- ============================================================
-- 1. fiscal_config：开票渠道配置（一条 = 一组凭据）
-- ============================================================
DROP TABLE IF EXISTS fiscal_config;
CREATE TABLE fiscal_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    channel         VARCHAR(32)  NOT NULL DEFAULT 'BAI_WANG' COMMENT 'BAI_WANG / YIN_TIAN / MOCK',
    app_key         VARCHAR(128) DEFAULT NULL COMMENT '应用 Key（明文或已加密）',
    app_secret      TEXT         DEFAULT NULL COMMENT '应用 Secret（建议上层加密）',
    tax_no          VARCHAR(64)  DEFAULT NULL COMMENT '销方税号',
    invoice_type    VARCHAR(32)  DEFAULT 'GENERAL' COMMENT 'GENERAL=普票 / SPECIAL=专票',
    merchant_name   VARCHAR(255) DEFAULT NULL,
    platform_url    VARCHAR(256) DEFAULT NULL COMMENT '覆盖 SDK 默认 URL（沙箱/生产）',
    notify_url      VARCHAR(256) DEFAULT NULL COMMENT '异步回调地址',
    environment     VARCHAR(32)  DEFAULT 'sandbox' COMMENT 'sandbox / production',
    enabled         TINYINT      DEFAULT 0 COMMENT '0=禁用 1=启用',
    extra_json      TEXT         DEFAULT NULL COMMENT '渠道额外参数（税率列表 / 商品编码 等）',
    description     VARCHAR(512) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_channel (tenant_id, channel),
    INDEX idx_env_enabled (environment, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='财税开票渠道配置';

-- ============================================================
-- 2. fiscal_invoice：扩展平台响应字段
-- ============================================================
ALTER TABLE fiscal_invoice
    ADD COLUMN platform_invoice_no VARCHAR(64)  DEFAULT NULL COMMENT '百望 / 航天 平台发票号',
    ADD COLUMN platform_void_no    VARCHAR(64)  DEFAULT NULL COMMENT '红冲号',
    ADD COLUMN platform_pdf_url    VARCHAR(500) DEFAULT NULL,
    ADD COLUMN platform_request_id VARCHAR(64)  DEFAULT NULL COMMENT '平台请求流水（幂等）',
    ADD COLUMN platform_response   TEXT         DEFAULT NULL COMMENT '完整平台响应（JSON）',
    ADD COLUMN request_payload     TEXT         DEFAULT NULL COMMENT '请求载荷（JSON）',
    ADD COLUMN request_started_at  DATETIME     DEFAULT NULL,
    ADD COLUMN request_completed_at DATETIME    DEFAULT NULL,
    ADD COLUMN request_mode        VARCHAR(16)  DEFAULT 'MOCK' COMMENT 'BAI_WANG / YIN_TIAN / MOCK',
    ADD COLUMN retry_count         INT          DEFAULT 0,
    ADD INDEX idx_platform_invoice (platform_invoice_no),
    ADD INDEX idx_platform_request (platform_request_id),
    ADD INDEX idx_mode_status (request_mode, invoice_status);

-- ============================================================
-- 3. 验证
-- ============================================================
SELECT
    (SELECT COUNT(*) FROM fiscal_config)   AS config_count,
    (SELECT COUNT(*) FROM fiscal_invoice)  AS invoice_count;
