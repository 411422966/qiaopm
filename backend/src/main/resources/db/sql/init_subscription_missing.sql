-- ============================================================
-- SUBS 订阅会员补表 (从 backend/sql/fix_subs_missing_tables.sql 迁移 / 2026-08-27)
-- 说明:
--   - 本文件内容原位于 backend/sql/fix_subs_missing_tables.sql
--   - 迁移原因: 与项目主 SQL 目录 (backend/src/main/resources/db/sql/) 统一, 详见
--     BUSINESS-FEATURE-INVENTORY.md 11.5.1 运维笔记。
--   - 涉及 3 张表:
--       subscription_plan     (SUBS-001 订阅方案)
--       subscription_billing  (SUBS-001 订阅账单)
--       member_day_execution  (SUBS-002 会员日执行记录)
--   - 其中 subscription_member 已存在于 init_p0_missing_tables.sql, 不再重复。
-- 关联表:
--   - subscription_plan.tenant_id           -> sys_tenant.id
--   - subscription_billing.sub_member_id    -> subscription_member.id
--   - subscription_billing.member_id        -> member.id
--   - subscription_billing.plan_id          -> subscription_plan.id
--   - member_day_execution.config_id        -> member_day_config.id
--   - member_day_execution.member_id        -> member.id
--   - member_day_execution.order_id         -> pos_order.id
-- ============================================================

-- SUBS-001 subscription_plan
CREATE TABLE IF NOT EXISTS subscription_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'PK',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    plan_code VARCHAR(50) DEFAULT '' COMMENT '方案编码 (业务唯一)',
    plan_name VARCHAR(100) DEFAULT '' COMMENT '方案名称',
    plan_type VARCHAR(20) DEFAULT '' COMMENT '方案类型 MONTHLY/QUARTERLY/YEARLY',
    price DECIMAL(12,2) DEFAULT 0 COMMENT '价格',
    billing_cycle_days INT DEFAULT 30 COMMENT '计费周期(天)',
    trial_days INT DEFAULT 0 COMMENT '试用期(天)',
    service_count INT DEFAULT 0 COMMENT '包含服务次数',
    included_services VARCHAR(500) DEFAULT '' COMMENT '包含服务ID列表 (逗号分隔)',
    discount_rate DECIMAL(4,2) DEFAULT 0 COMMENT '商品折扣率 0.00-1.00',
    gift_points INT DEFAULT 0 COMMENT '赠送积分',
    extra_benefits VARCHAR(500) DEFAULT '' COMMENT '附加权益',
    description VARCHAR(500) DEFAULT '' COMMENT '方案说明',
    cover_image VARCHAR(255) DEFAULT '' COMMENT '封面图 URL',
    total_subscribers INT DEFAULT 0 COMMENT '累计订阅数',
    active_subscribers INT DEFAULT 0 COMMENT '当前生效订阅数',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/DEPRECATED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_subp_tenant (tenant_id),
    INDEX idx_subp_code (plan_code),
    INDEX idx_subp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='订阅方案 (SubscriptionPlan)';

-- SUBS-001 subscription_billing
CREATE TABLE IF NOT EXISTS subscription_billing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'PK',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    sub_member_id BIGINT DEFAULT NULL COMMENT '订阅会员记录ID -> subscription_member.id',
    member_id BIGINT DEFAULT NULL COMMENT '会员ID -> member.id',
    plan_id BIGINT DEFAULT NULL COMMENT '订阅方案ID -> subscription_plan.id',
    billing_date DATE DEFAULT NULL COMMENT '账单日期',
    amount DECIMAL(12,2) DEFAULT 0 COMMENT '账单金额',
    payment_method VARCHAR(20) DEFAULT '' COMMENT '支付方式 WECHAT/ALIPAY/CARD/POINTS',
    payment_status VARCHAR(20) DEFAULT '' COMMENT '支付状态 PENDING/PAID/FAILED/REFUNDED',
    bill_no VARCHAR(50) DEFAULT '' COMMENT '账单编号 (业务唯一)',
    transaction_id VARCHAR(100) DEFAULT '' COMMENT '第三方支付流水号',
    error_message VARCHAR(500) DEFAULT '' COMMENT '支付失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    paid_at DATETIME DEFAULT NULL COMMENT '支付完成时间',
    INDEX idx_subb_sub_member (sub_member_id),
    INDEX idx_subb_member (member_id),
    INDEX idx_subb_plan (plan_id),
    INDEX idx_subb_billing_date (billing_date),
    INDEX idx_subb_status (payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='订阅账单 (SubscriptionBilling)';

-- SUBS-002 member_day_execution
CREATE TABLE IF NOT EXISTS member_day_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'PK',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    config_id BIGINT DEFAULT NULL COMMENT '会员日配置ID -> member_day_config.id',
    member_id BIGINT DEFAULT NULL COMMENT '会员ID -> member.id',
    member_name VARCHAR(100) DEFAULT '' COMMENT '会员姓名 (冗余)',
    member_phone VARCHAR(50) DEFAULT '' COMMENT '会员手机号 (冗余)',
    trigger_reason VARCHAR(50) DEFAULT '' COMMENT '触发原因 BIRTHDAY/ANNIVERSARY/CUSTOM',
    trigger_date DATE DEFAULT NULL COMMENT '触发日期',
    channel VARCHAR(20) DEFAULT '' COMMENT '触达渠道 SMS/WECHAT/PUSH',
    discount_amount DECIMAL(12,2) DEFAULT 0 COMMENT '优惠金额',
    gift_amount DECIMAL(12,2) DEFAULT 0 COMMENT '赠送金额',
    gift_points INT DEFAULT 0 COMMENT '赠送积分',
    coupon_code VARCHAR(50) DEFAULT '' COMMENT '关联优惠券编码',
    send_status VARCHAR(20) DEFAULT '' COMMENT '发送状态 PENDING/SENT/FAILED',
    sent_at DATETIME DEFAULT NULL COMMENT '发送时间',
    used_at DATETIME DEFAULT NULL COMMENT '使用时间',
    order_id BIGINT DEFAULT NULL COMMENT '关联订单ID -> pos_order.id',
    error_message VARCHAR(500) DEFAULT '' COMMENT '发送失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mde_config (config_id),
    INDEX idx_mde_member (member_id),
    INDEX idx_mde_trigger_date (trigger_date),
    INDEX idx_mde_status (send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会员日触达执行记录 (MemberDayExecution)';