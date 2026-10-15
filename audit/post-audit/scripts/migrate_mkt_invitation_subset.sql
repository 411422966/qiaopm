-- ============================================
-- Phase 2.3 子集脚本：mkt_invitation + mkt_invitation_rule
-- ============================================
-- 来源脚本 : backend/src/main/resources/db/sql/migrate_r3_0_e_p2_features.sql
-- 原始行号 : L1-197（共 197 行 / 10976 B / 2026-08-14）
-- 2.3 取舍 : 仅保留原脚本 §2「裂变邀请」段（L45-103）+ §2 末尾 INSERT seed
-- 剔除段   : §1 crm_package_share + crm_package_order ALTER（L9-43）
--          + §3 exp_experiment / exp_experiment_variant / exp_assignment（L105-167）
--          + 末尾 sys_permission / sys_role_permission（L169-186）
--          + 末尾验证 SELECT（L188-197 中涉及 crm_package_share / exp_* 的子查询）
-- 剔除原因 : 严格遵守 Phase 2.3 范围（§5.1 #3 mkt_invitation + mkt_invitation_rule）
--          §1 / §3 / 权限段不在 2.3 范围，留待后续单独批次决策
--          详见 audit/post-audit/phase-2.3-postmortem.md + phase-2.3-migration-plan.md §1
-- 评审状态 : 待用户 review，未 apply
-- ============================================

-- ============================================
-- 2. 裂变邀请（保留）
-- ============================================
DROP TABLE IF EXISTS mkt_invitation;
CREATE TABLE mkt_invitation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    inviter_id      BIGINT       NOT NULL COMMENT '邀请人',
    inviter_name    VARCHAR(64)  DEFAULT NULL,
    invitee_id      BIGINT       DEFAULT NULL COMMENT '被邀请人（NULL=尚未注册）',
    invitee_phone   VARCHAR(20)  DEFAULT NULL,
    invitee_name    VARCHAR(64)  DEFAULT NULL,
    invite_code     VARCHAR(32)  NOT NULL COMMENT '邀请码',
    channel         VARCHAR(32)  DEFAULT 'LINK' COMMENT 'LINK/SMS/WECHAT/QRCODE',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/REGISTERED/ORDERED/REWARDED',
    registered_at   DATETIME     DEFAULT NULL,
    first_order_id  BIGINT       DEFAULT NULL,
    first_order_at  DATETIME     DEFAULT NULL,
    invite_reward   DECIMAL(12,2) DEFAULT 0 COMMENT '邀请人奖励（订单消费返现）',
    invitee_reward  DECIMAL(12,2) DEFAULT 0 COMMENT '被邀请人奖励（注册礼包）',
    reward_status   VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_invite_code (tenant_id, invite_code),
    INDEX idx_inviter (inviter_id, status),
    INDEX idx_invitee (invitee_id, status),
    INDEX idx_phone (invitee_phone, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='裂变邀请记录';

DROP TABLE IF EXISTS mkt_invitation_rule;
CREATE TABLE mkt_invitation_rule (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    brand_id           BIGINT       DEFAULT NULL,
    rule_code          VARCHAR(64)  NOT NULL,
    rule_name          VARCHAR(128) NOT NULL,
    enabled            TINYINT      DEFAULT 1,
    inviter_reward_type VARCHAR(32) DEFAULT 'CASH' COMMENT 'CASH现金/COUPON券/POINTS积分/NONE无',
    inviter_reward_value DECIMAL(12,2) DEFAULT 0,
    invitee_reward_type VARCHAR(32) DEFAULT 'COUPON',
    invitee_reward_value DECIMAL(12,2) DEFAULT 0,
    invitee_reward_coupon_id BIGINT DEFAULT NULL,
    min_order_amount   DECIMAL(12,2) DEFAULT 0 COMMENT '被邀请人最低消费门槛',
    require_first_order TINYINT    DEFAULT 1,
    daily_limit_per_inviter INT DEFAULT 50,
    start_time         DATETIME     DEFAULT NULL,
    end_time           DATETIME     DEFAULT NULL,
    operator_id        BIGINT       DEFAULT NULL,
    operator_name      VARCHAR(64)  DEFAULT NULL,
    create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT      DEFAULT 0,
    UNIQUE KEY uk_rule_code (tenant_id, rule_code),
    INDEX idx_enabled (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='裂变规则配置';

INSERT IGNORE INTO mkt_invitation_rule (tenant_id, brand_id, rule_code, rule_name, enabled,
    inviter_reward_type, inviter_reward_value, invitee_reward_type, invitee_reward_value,
    require_first_order, min_order_amount, operator_name)
VALUES (1, 1, 'FISSION-001', '老带新通用规则', 1,
    'CASH', 50.00, 'COUPON', 100.00,
    1, 200.00, 'system');

-- ============================================
-- 验证（只统计保留的 2 张表）
-- ============================================
SELECT
    (SELECT COUNT(*) FROM mkt_invitation WHERE deleted=0) AS invite_count,
    (SELECT COUNT(*) FROM mkt_invitation_rule WHERE deleted=0) AS rules;
