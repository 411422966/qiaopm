-- ============================================
-- R3.0-H P0.5 回访计划 + 触发日志
-- 2026-08-20
-- ============================================

-- 1. 回访计划表（crm_follow_up_plan）
--    兼容 init_rbac_v3_0_c.sql：若表已存在，跳过；缺失字段通过 ALTER 补齐。
CREATE TABLE IF NOT EXISTS crm_follow_up_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL DEFAULT 1,
    brand_id        BIGINT,
    store_id        BIGINT,
    plan_code       VARCHAR(64) NOT NULL,
    plan_name       VARCHAR(128) NOT NULL,
    plan_type       VARCHAR(32) NOT NULL COMMENT 'POST_TREATMENT/BIRTHDAY/EXPIRE/CUSTOM',
    -- 触发条件
    trigger_offset_days INT DEFAULT 0 COMMENT '相对触发日偏移天数（+3=3天后）',
    trigger_event   VARCHAR(64) COMMENT 'ORDER_COMPLETED/CARD_DELIVERED/BIRTHDAY/EXPIRE_30D',
    -- 模板
    subject_template VARCHAR(255),
    content_template TEXT,
    script_template TEXT,
    -- 优先级/SLA
    priority        TINYINT DEFAULT 1,
    sla_minutes     INT DEFAULT 1440,
    min_amount      DECIMAL(12,2) DEFAULT 0,
    -- 默认归属
    default_owner_id   BIGINT,
    default_owner_name VARCHAR(64),
    -- 状态
    enabled         TINYINT DEFAULT 1,
    last_run_at     DATETIME DEFAULT NULL,
    last_run_count  INT DEFAULT NULL,
    -- 操作
    create_by       VARCHAR(64),
    update_by       VARCHAR(64),
    operator_id     BIGINT,
    operator_name   VARCHAR(64),
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, plan_code),
    INDEX idx_type (plan_type, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访计划模板';

-- 补齐字段：若表已存在但缺以下列，由应用层容错；这里给出推荐的 ALTER 片段（手动执行）：
-- ALTER TABLE crm_follow_up_plan ADD COLUMN IF NOT EXISTS store_id BIGINT;
-- ALTER TABLE crm_follow_up_plan ADD COLUMN IF NOT EXISTS min_amount DECIMAL(12,2) DEFAULT 0;
-- ALTER TABLE crm_follow_up_plan ADD COLUMN IF NOT EXISTS last_run_at DATETIME DEFAULT NULL;
-- ALTER TABLE crm_follow_up_plan ADD COLUMN IF NOT EXISTS last_run_count INT DEFAULT NULL;
-- ALTER TABLE crm_follow_up_plan ADD COLUMN IF NOT EXISTS create_by VARCHAR(64);
-- ALTER TABLE crm_follow_up_plan ADD COLUMN IF NOT EXISTS update_by VARCHAR(64);

-- 2. 触发日志表
CREATE TABLE IF NOT EXISTS crm_follow_up_trigger_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL DEFAULT 1,
    plan_id         BIGINT NOT NULL,
    plan_code       VARCHAR(64),
    generated_count INT DEFAULT 0,
    trigger_source  VARCHAR(32) COMMENT 'MANUAL/SCHEDULED',
    trigger_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_plan_time (plan_id, trigger_time),
    KEY idx_tenant_time (tenant_id, trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访计划触发日志';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_name = 'crm_follow_up_plan') AS plan_table_exists,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_name = 'crm_follow_up_trigger_log') AS trigger_log_table_exists;
