-- ============================================
-- R3.0-E P2：高级差异化（最小骨架）
--  1. 多人共享套餐（crm_package_share）
--  2. 裂变邀请（mkt_invitation_*）
--  3. A/B Test 实验平台（exp_*）
-- 2026-08-14
-- ============================================

-- ============================================
-- 1. 多人共享套餐
-- ============================================
DROP TABLE IF EXISTS crm_package_share;
CREATE TABLE crm_package_share (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    package_order_id BIGINT      NOT NULL COMMENT '原套餐订单 ID',
    member_id       BIGINT       NOT NULL COMMENT '使用人（被分享者）',
    member_name     VARCHAR(64)  DEFAULT NULL,
    member_phone    VARCHAR(20)  DEFAULT NULL,
    use_times       INT          DEFAULT 0 COMMENT '该成员已使用次数',
    max_times       INT          DEFAULT NULL COMMENT '配额 NULL=不限',
    invited_by      BIGINT       DEFAULT NULL COMMENT '发起邀请的会员',
    invite_at       DATETIME     DEFAULT NULL COMMENT '邀请时间',
    first_used_at   DATETIME     DEFAULT NULL,
    status          TINYINT      DEFAULT 1 COMMENT '1待使用 2已激活 3已用完 4已撤销',
    remark          VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_pkg_member (package_order_id, member_id, deleted),
    INDEX idx_pkg (package_order_id, status),
    INDEX idx_member (member_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐共享成员';

ALTER TABLE crm_package_order
    ADD COLUMN shared_enabled TINYINT DEFAULT 0 COMMENT '是否允许共享 1=是' AFTER status,
    ADD COLUMN max_share_members INT DEFAULT 1 COMMENT '最大共享人数' AFTER shared_enabled,
    ADD COLUMN shared_count INT DEFAULT 0 COMMENT '已邀请人数' AFTER max_share_members;

-- ============================================
-- 2. 裂变邀请
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
-- 3. A/B Test 实验平台
-- ============================================
DROP TABLE IF EXISTS exp_experiment;
CREATE TABLE exp_experiment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       DEFAULT NULL,
    exp_code        VARCHAR(64)  NOT NULL COMMENT '实验编码',
    exp_name        VARCHAR(128) NOT NULL,
    exp_type        VARCHAR(32)  DEFAULT 'UI' COMMENT 'UI/PRICE/COPY/FUNCTION/FLOW',
    description     VARCHAR(500) DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'DRAFT' COMMENT 'DRAFT/RUNNING/PAUSED/FINISHED',
    started_at      DATETIME     DEFAULT NULL,
    ended_at        DATETIME     DEFAULT NULL,
    target_metric   VARCHAR(64)  DEFAULT NULL COMMENT '转化率/客单价/复购率',
    sample_size     INT          DEFAULT 0,
    confidence_level DECIMAL(5,2) DEFAULT 95.00 COMMENT '95.00%',
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_exp_code (tenant_id, exp_code),
    INDEX idx_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AB 实验';

DROP TABLE IF EXISTS exp_experiment_variant;
CREATE TABLE exp_experiment_variant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    experiment_id   BIGINT       NOT NULL,
    variant_code    VARCHAR(32)  NOT NULL COMMENT 'A/B/C 等',
    variant_name    VARCHAR(128) NOT NULL,
    traffic_weight  INT          DEFAULT 50 COMMENT '流量权重（百分比）',
    payload         TEXT         DEFAULT NULL COMMENT '变体内容（JSON）',
    is_control      TINYINT      DEFAULT 0 COMMENT '是否对照组',
    exposure_count  BIGINT       DEFAULT 0,
    conversion_count BIGINT      DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_exp_variant (experiment_id, variant_code),
    INDEX idx_exp (experiment_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验变体';

DROP TABLE IF EXISTS exp_assignment;
CREATE TABLE exp_assignment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    experiment_id   BIGINT       NOT NULL,
    variant_id      BIGINT       NOT NULL,
    subject_id      VARCHAR(64)  NOT NULL COMMENT '实验对象（用户ID/设备/门店）',
    exposure_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    converted       TINYINT      DEFAULT 0,
    converted_at    DATETIME     DEFAULT NULL,
    metric_value    DECIMAL(18,4) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exp_subject (experiment_id, subject_id),
    INDEX idx_variant (variant_id, converted),
    INDEX idx_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验分配记录';

-- ============================================
-- 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:package-share', '套餐共享', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 70;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:fission', '裂变邀请', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:channel') t LIMIT 1), 80;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'exp:experiment', 'AB 实验', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:report') t LIMIT 1), 90;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('crm:package-share','mkt:fission','exp:experiment');

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM crm_package_share WHERE deleted=0) AS shared_pkg,
    (SELECT COUNT(*) FROM mkt_invitation WHERE deleted=0) AS invite_count,
    (SELECT COUNT(*) FROM mkt_invitation_rule WHERE deleted=0) AS rules,
    (SELECT COUNT(*) FROM exp_experiment WHERE deleted=0) AS exp_count,
    (SELECT COUNT(*) FROM exp_experiment_variant WHERE deleted=0) AS variant_count;