-- ============================================
-- Phase 2.3 暂不 apply 的剔除段（来源：migrate_r3_0_e_p2_features.sql）
-- ============================================
-- 来源脚本 : backend/src/main/resources/db/sql/migrate_r3_0_e_p2_features.sql
-- 原始行号 : §1 crm_package_share（DROP+CREATE）+ crm_package_order ALTER 3 列 → L9-43
--          + §3 exp_experiment / exp_experiment_variant / exp_assignment（DROP+CREATE）→ L105-167
--          + 末尾 sys_permission 3 条 + sys_role_permission 1 组 INSERT → L169-186
-- 暂不 apply 原因 : 严格遵守 Phase 2.3 范围，仅保留 mkt_invitation + mkt_invitation_rule
-- 后续决策入口 : 待 Phase 2.4 / 后续批次单独评审，决定是否补 apply
-- 对应子集脚本 : audit/post-audit/scripts/migrate_mkt_invitation_subset.sql
-- 文件用途 : 仅作为「完整原始内容存档」+「后续批次评审参考」
--          **严禁**在 Phase 2.3 中 source 此文件
-- ============================================

-- ============================================
-- 1. 多人共享套餐（crm_package_share + crm_package_order ALTER）— 暂不 apply
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
-- 3. A/B Test 实验平台（exp_* 三张表）— 暂不 apply
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
-- 权限码 INSERT（crm:package-share / mkt:fission / exp:experiment）— 暂不 apply
-- ============================================
-- 注：mkt:fission 与本次保留的 mkt_invitation / mkt_invitation_rule 表相关，但
--     业务侧尚未启用菜单入口（前端 plugin/index.vue 无 fission tab），故一并暂不 apply。
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
