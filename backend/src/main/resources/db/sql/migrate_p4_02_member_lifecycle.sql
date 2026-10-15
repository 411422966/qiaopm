-- ====================================================================
-- P4-02 · QPM-006: 会员生命周期营销 — 4 张新表
-- Migration: p4_02_member_lifecycle.sql
-- Generated: 2026-08-17
-- 备注：仅新建 P4-02 新增表，复用现有表
--   - birthday_member, birthday_send_log
--   - member_day_config, member_day_execution
--   - crm_rfm_snapshot, mkt_journey, mkt_journey_instance
--   - marketing_touch_task, marketing_touch_log, mkt_channel_template
-- ====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------------------------------
-- T1. 统一规则引擎：member_lifecycle_rule
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `member_lifecycle_rule`;
CREATE TABLE `member_lifecycle_rule` (
    `id`                     BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT          NOT NULL                COMMENT '租户 ID',
    `brand_id`               BIGINT          DEFAULT NULL            COMMENT '品牌 ID',
    `rule_code`              VARCHAR(50)     NOT NULL                COMMENT '规则编码',
    `rule_type`              VARCHAR(20)     NOT NULL                COMMENT 'BIRTHDAY/MEMBER_DAY/RETENTION/INVITE',
    `rule_name`              VARCHAR(100)    NOT NULL                COMMENT '规则名称',
    `description`            VARCHAR(500)    DEFAULT NULL            COMMENT '描述',
    `trigger_config_json`    JSON            DEFAULT NULL            COMMENT '触发条件 JSON',
    `template_id`            BIGINT          DEFAULT NULL            COMMENT '触达模板 ID',
    `rfm_segment`            VARCHAR(20)     DEFAULT NULL            COMMENT '适用 RFM 分群',
    `invoke_journey_id`      BIGINT          DEFAULT NULL            COMMENT '关联旅程 ID',
    `invoke_coupon_amount`   DECIMAL(10,2)   DEFAULT NULL            COMMENT '自动发放优惠金额',
    `invoke_points`          INT             DEFAULT NULL            COMMENT '自动赠送积分',
    `schedule_cron`          VARCHAR(50)     DEFAULT NULL            COMMENT 'cron 表达式',
    `max_per_day`            INT             DEFAULT 1000            COMMENT '每日最大触发数',
    `enabled`                TINYINT         NOT NULL DEFAULT 0      COMMENT '1=启用 0=禁用',
    `status`                 VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/PAUSED/ARCHIVED',
    `priority`               INT             NOT NULL DEFAULT 100    COMMENT '优先级',
    `operator_name`          VARCHAR(50)     DEFAULT NULL            COMMENT '操作人',
    `create_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_rule_code` (`tenant_id`, `rule_code`, `deleted`),
    KEY `idx_tenant_type` (`tenant_id`, `rule_type`, `enabled`),
    KEY `idx_tenant_priority` (`tenant_id`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员生命周期规则引擎';

-- --------------------------------------------------------------------
-- T2. 统一触发日志：member_lifecycle_log
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `member_lifecycle_log`;
CREATE TABLE `member_lifecycle_log` (
    `id`                     BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT          NOT NULL                COMMENT '租户 ID',
    `rule_id`                BIGINT          NOT NULL                COMMENT '规则 ID',
    `rule_code`              VARCHAR(50)     NOT NULL                COMMENT '规则编码',
    `rule_type`              VARCHAR(20)     NOT NULL                COMMENT '规则类型',
    `member_id`              BIGINT          NOT NULL                COMMENT '目标会员 ID',
    `member_name`            VARCHAR(50)     DEFAULT NULL            COMMENT '会员姓名',
    `member_phone`           VARCHAR(20)     DEFAULT NULL            COMMENT '会员手机',
    `trigger_date`           DATE            NOT NULL                COMMENT '触发日期',
    `trigger_reason`         VARCHAR(200)    DEFAULT NULL            COMMENT '触发原因',
    `channel`                VARCHAR(20)     NOT NULL                COMMENT '触达渠道',
    `template_id`            BIGINT          DEFAULT NULL            COMMENT '触达模板',
    `template_content`       TEXT            DEFAULT NULL            COMMENT '模板内容快照',
    `coupon_code`            VARCHAR(50)     DEFAULT NULL            COMMENT '发放的优惠码',
    `points_awarded`         INT             DEFAULT NULL            COMMENT '发放的积分',
    `gift_value`             VARCHAR(100)    DEFAULT NULL            COMMENT '礼物描述',
    `journey_instance_id`    BIGINT          DEFAULT NULL            COMMENT '关联旅程实例 ID',
    `status`                 VARCHAR(20)     NOT NULL                COMMENT 'PENDING/SENT/FAILED/USED/EXPIRED',
    `error_message`          VARCHAR(500)    DEFAULT NULL            COMMENT '失败原因',
    `external_msg_id`        VARCHAR(100)    DEFAULT NULL            COMMENT '外部消息 ID',
    `converted_at`           DATETIME        DEFAULT NULL            COMMENT '转化时间',
    `related_order_id`       BIGINT          DEFAULT NULL            COMMENT '关联订单',
    `sent_at`                DATETIME        DEFAULT NULL            COMMENT '发送时间',
    `used_at`                DATETIME        DEFAULT NULL            COMMENT '使用时间',
    `create_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`                TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_member_date` (`rule_id`, `member_id`, `trigger_date`, `deleted`),
    KEY `idx_tenant_rule_date` (`tenant_id`, `rule_id`, `trigger_date`),
    KEY `idx_tenant_member` (`tenant_id`, `member_id`, `rule_type`),
    KEY `idx_status_date` (`status`, `trigger_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员生命周期触发日志';

-- --------------------------------------------------------------------
-- T3. 裂变邀请码：member_invite_code
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `member_invite_code`;
CREATE TABLE `member_invite_code` (
    `id`                     BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT          NOT NULL                COMMENT '租户 ID',
    `inviter_id`             BIGINT          NOT NULL                COMMENT '邀请人会员 ID',
    `invite_code`            VARCHAR(20)     NOT NULL                COMMENT '邀请码',
    `invite_channel`         VARCHAR(20)     DEFAULT NULL            COMMENT '分享渠道 WX/QR/LINK/SMS',
    `invite_count`           INT             NOT NULL DEFAULT 0      COMMENT '被邀请人次',
    `success_count`          INT             NOT NULL DEFAULT 0      COMMENT '成功注册数',
    `reward_coupon_template` BIGINT          DEFAULT NULL            COMMENT '奖励券模板',
    `reward_coupon_amount`   DECIMAL(10,2)   DEFAULT NULL            COMMENT '奖励券金额',
    `expire_at`              DATETIME        DEFAULT NULL            COMMENT '失效时间',
    `status`                 VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/DISABLED',
    `create_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_code` (`invite_code`),
    KEY `idx_tenant_inviter` (`tenant_id`, `inviter_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员裂变邀请码';

-- --------------------------------------------------------------------
-- T4. 裂变奖励记录：member_invite_reward
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS `member_invite_reward`;
CREATE TABLE `member_invite_reward` (
    `id`                     BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`              BIGINT          NOT NULL                COMMENT '租户 ID',
    `invite_code_id`         BIGINT          NOT NULL                COMMENT '邀请码 ID',
    `inviter_id`             BIGINT          NOT NULL                COMMENT '邀请人',
    `invitee_id`             BIGINT          NOT NULL                COMMENT '被邀请人',
    `invitee_name`           VARCHAR(50)     DEFAULT NULL            COMMENT '被邀请人姓名',
    `invitee_phone`          VARCHAR(20)     DEFAULT NULL            COMMENT '被邀请人手机',
    `register_at`            DATETIME        NOT NULL                COMMENT '被邀请人注册时间',
    `first_order_id`         BIGINT          DEFAULT NULL            COMMENT '首单 ID',
    `first_order_at`         DATETIME        DEFAULT NULL            COMMENT '首单时间',
    `inviter_reward_type`    VARCHAR(20)     NOT NULL                COMMENT '邀请人奖励类型 COUPON/POINTS/CASH',
    `inviter_reward_value`   VARCHAR(100)    NOT NULL                COMMENT '邀请人奖励值',
    `inviter_reward_status`  VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/USED',
    `invitee_reward_type`    VARCHAR(20)     NOT NULL                COMMENT '被邀请人奖励类型',
    `invitee_reward_value`   VARCHAR(100)    NOT NULL                COMMENT '被邀请人奖励值',
    `invitee_reward_status`  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    `operator_name`          VARCHAR(50)     DEFAULT NULL,
    `create_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_inviter` (`tenant_id`, `inviter_id`),
    KEY `idx_tenant_invitee` (`tenant_id`, `invitee_id`),
    KEY `idx_invite_code` (`invite_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员裂变奖励记录';

-- --------------------------------------------------------------------
-- 初始化：4 大规则类型示例数据（每个租户默认启用）
-- --------------------------------------------------------------------
-- 注意：实际数据初始化在 MemberLifecycleController.initDefaults() 完成
-- 这里仅创建表结构，不预置数据

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- 验证
-- ====================================================================
-- SELECT COUNT(*) FROM member_lifecycle_rule;
-- SELECT COUNT(*) FROM member_lifecycle_log;
-- SELECT COUNT(*) FROM member_invite_code;
-- SELECT COUNT(*) FROM member_invite_reward;