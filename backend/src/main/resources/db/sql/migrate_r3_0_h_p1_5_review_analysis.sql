-- ============================================
-- R3.0-H P1.5 评价分析 + 服务改进闭环
--  1. crm_review_dimension_config（自定义评价维度）
--  2. crm_service_improvement（评价驱动的服务改进任务）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS crm_review_dimension_config;
CREATE TABLE crm_review_dimension_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    dimension_code  VARCHAR(64)  NOT NULL COMMENT '维度编码（如 SERVICE/SKILL/ENV/PRICE/...）',
    dimension_name  VARCHAR(128) NOT NULL COMMENT '维度名',
    weight          DECIMAL(5,2) DEFAULT 1.00 COMMENT '权重（影响 NPS 计算）',
    target_score    DECIMAL(3,2) DEFAULT 4.50 COMMENT '目标分数（低于此值触发改进）',
    enabled         TINYINT      DEFAULT 1,
    sort_order      INT          DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_id, dimension_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价维度配置';

DROP TABLE IF EXISTS crm_service_improvement;
CREATE TABLE crm_service_improvement (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       DEFAULT NULL,
    store_id        BIGINT       DEFAULT NULL,
    improvement_no  VARCHAR(64)  NOT NULL COMMENT '改进单号（IMP-yyyyMMdd-XXXX）',
    title           VARCHAR(255) NOT NULL,
    source_type     VARCHAR(32)  DEFAULT 'REVIEW' COMMENT 'REVIEW / COMPLAINT / VOICE_OF_CUSTOMER / INTERNAL',
    source_id       BIGINT       DEFAULT NULL COMMENT '关联 crm_review.id',
    source_no       VARCHAR(64)  DEFAULT NULL,
    -- 触发原因
    dimension_code  VARCHAR(64)  DEFAULT NULL COMMENT '关联维度',
    current_score   DECIMAL(3,2) DEFAULT NULL COMMENT '当前分数',
    target_score    DECIMAL(3,2) DEFAULT NULL COMMENT '目标分数',
    gap_score       DECIMAL(3,2) DEFAULT NULL COMMENT '差距分数',
    -- 改进内容
    problem         TEXT         DEFAULT NULL COMMENT '问题描述',
    root_cause      TEXT         DEFAULT NULL COMMENT '根因分析',
    action_plan     TEXT         DEFAULT NULL COMMENT '行动计划',
    expected_effect VARCHAR(500) DEFAULT NULL,
    -- 责任人
    owner_id        BIGINT       DEFAULT NULL,
    owner_name      VARCHAR(64)  DEFAULT NULL,
    -- 状态机
    status          INT          DEFAULT 1 COMMENT '1=待评估 / 2=待审批 / 3=执行中 / 4=已完成 / 5=已取消',
    priority        INT          DEFAULT 1 COMMENT '1=低 / 2=中 / 3=高 / 4=紧急',
    -- 时间
    started_at      DATETIME     DEFAULT NULL,
    completed_at    DATETIME     DEFAULT NULL,
    deadline        DATETIME     DEFAULT NULL,
    -- 效果验证
    post_score      DECIMAL(3,2) DEFAULT NULL COMMENT '改进后分数',
    effect_note     VARCHAR(500) DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_tenant_status (tenant_id, status, priority),
    INDEX idx_source (source_type, source_id),
    INDEX idx_dimension (dimension_code, status),
    INDEX idx_owner (owner_id, status),
    UNIQUE KEY uk_no (tenant_id, improvement_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价驱动的服务改进任务';

DROP TABLE IF EXISTS crm_service_improvement_log;
CREATE TABLE crm_service_improvement_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    improvement_id  BIGINT       NOT NULL,
    action_type     VARCHAR(32)  NOT NULL COMMENT 'CREATE / ASSIGN / TRANSIT / COMMENT / COMPLETE / VERIFY',
    from_status     INT          DEFAULT NULL,
    to_status       INT          DEFAULT NULL,
    content         VARCHAR(1000) DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_improvement (improvement_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务改进操作日志';

-- 默认维度配置
INSERT IGNORE INTO crm_review_dimension_config (tenant_id, dimension_code, dimension_name, weight, target_score, sort_order) VALUES
    (1, 'SERVICE',     '服务态度', 1.00, 4.50, 1),
    (1, 'SKILL',       '专业技能', 1.50, 4.50, 2),
    (1, 'ENVIRONMENT', '环境氛围', 0.80, 4.30, 3),
    (1, 'PRICE',       '性价比',   1.00, 4.00, 4),
    (1, 'OVERALL',     '综合',     1.00, 4.50, 99);

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM crm_review_dimension_config) AS dim_count,
    (SELECT COUNT(*) FROM crm_service_improvement) AS imp_count,
    (SELECT COUNT(*) FROM crm_service_improvement_log) AS log_count;