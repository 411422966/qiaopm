-- ============================================
-- Phase 2.3 子集脚本：mkt_dispatch + mkt_dispatch_target
-- ============================================
-- 来源脚本 : backend/src/main/resources/db/sql/migrate_r3_0_f_p1_remaining.sql
-- 原始行号 : L1-158（共 158 行 / 8477 B / 2026-08-14）
-- 2.3 取舍 : 仅保留原脚本 §1「总部统一下发」段（L9-60）
-- 剔除段   : §2 schedule_suggestion（L62-80）
--          + §3 biz_forecast_demand（L82-100）
--          + §4 crm_recommend_log（L102-122）
--          + 末尾 sys_permission / sys_role_permission（L124-146）
--          + 末尾验证 SELECT（L148-158 中涉及 schedule_* / biz_* / crm_recommend_log 的子查询）
-- 剔除原因 : 严格遵守 Phase 2.3 范围（§5.1 #3 mkt_dispatch + mkt_dispatch_target）
--          §2-4 与权限段不在 2.3 范围，留待后续单独批次决策
--          详见 audit/post-audit/phase-2.3-postmortem.md + phase-2.3-migration-plan.md §1
-- 评审状态 : 待用户 review，未 apply
-- ============================================

-- ============================================
-- 1. 总部统一下发（保留）
-- ============================================
DROP TABLE IF EXISTS mkt_dispatch;
CREATE TABLE mkt_dispatch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       NOT NULL COMMENT '下发品牌',
    dispatch_no     VARCHAR(64)  NOT NULL COMMENT '下发单号',
    dispatch_type   VARCHAR(32)  NOT NULL COMMENT 'COUPON优惠券/PACKAGE套餐/DISCOUNT折扣/ACTIVITY活动',
    title           VARCHAR(255) NOT NULL,
    payload         TEXT         DEFAULT NULL COMMENT 'JSON：原对象 ID/参数/规则',
    target_type     VARCHAR(32)  DEFAULT 'SPECIFIC' COMMENT 'ALL全部/BRAND品牌级/SPECIFIC指定门店',
    target_store_ids VARCHAR(2000) DEFAULT NULL COMMENT 'JSON 数组',
    status          VARCHAR(16)  DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/EXECUTING/DONE/CANCELLED',
    plan_start_time DATETIME     DEFAULT NULL,
    plan_end_time   DATETIME     DEFAULT NULL,
    published_at    DATETIME     DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_no (tenant_id, dispatch_no),
    INDEX idx_brand (brand_id, status, deleted),
    INDEX idx_type (dispatch_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='总部统一下发策略';

DROP TABLE IF EXISTS mkt_dispatch_target;
CREATE TABLE mkt_dispatch_target (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    dispatch_id     BIGINT       NOT NULL,
    store_id        BIGINT       NOT NULL,
    store_name      VARCHAR(64)  DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED/EXECUTED',
    accepted_at     DATETIME     DEFAULT NULL,
    rejected_reason VARCHAR(500) DEFAULT NULL,
    executed_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_dispatch_store (dispatch_id, store_id, deleted),
    INDEX idx_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下发目标门店';

-- ============================================
-- 验证（只统计保留的 2 张表）
-- ============================================
SELECT
    (SELECT COUNT(*) FROM mkt_dispatch WHERE deleted=0) AS dispatch_count,
    (SELECT COUNT(*) FROM mkt_dispatch_target WHERE deleted=0) AS dispatch_target;
