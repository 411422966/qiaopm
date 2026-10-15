-- ============================================================
-- POS-008 退款审核完整工作流
--  1. pos_refund 加 current_audit_level 列（多级审核）
--  2. 新增 pos_refund_audit_log（审核流水 / 时间线）
--  3. 新增 pos_refund_audit_rule（审核规则配置）
-- ============================================================

ALTER TABLE pos_refund
    ADD COLUMN current_audit_level INT DEFAULT 0 COMMENT '当前审核级别：0无需审核 / 1店长 / 2区域经理 / 3财务总监' AFTER status,
    ADD COLUMN audit_required TINYINT DEFAULT 0 COMMENT '是否需要审核：0否 1是' AFTER current_audit_level,
    ADD INDEX idx_audit_level (current_audit_level);

DROP TABLE IF EXISTS pos_refund_audit_log;
CREATE TABLE pos_refund_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    refund_id       BIGINT       NOT NULL,
    refund_no       VARCHAR(64)  DEFAULT NULL,
    audit_level     INT          DEFAULT 0 COMMENT '审核级别：0无需 / 1店长 / 2区域经理 / 3财务总监',
    audit_level_name VARCHAR(64) DEFAULT NULL,
    action          VARCHAR(32)  NOT NULL COMMENT 'SUBMIT/AUTO_APPROVE/L1_APPROVE/L2_APPROVE/L3_APPROVE/REJECT/RESUBMIT/CANCEL/PROCESS/SUCCESS/FAILED',
    actor_id        BIGINT       DEFAULT NULL,
    actor_name      VARCHAR(64)  DEFAULT NULL,
    actor_role      VARCHAR(64)  DEFAULT NULL,
    before_status   VARCHAR(32)  DEFAULT NULL,
    after_status    VARCHAR(32)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_refund (tenant_id, refund_id),
    INDEX idx_create_time (create_time),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款审核流水';

DROP TABLE IF EXISTS pos_refund_audit_rule;
CREATE TABLE pos_refund_audit_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    audit_level     INT          NOT NULL COMMENT '审核级别：1店长 2区域经理 3财务总监',
    audit_level_name VARCHAR(64) DEFAULT NULL,
    min_amount      DECIMAL(12,2) DEFAULT 0 COMMENT '触发审核的最小金额（不含）',
    max_amount      DECIMAL(12,2) DEFAULT 999999 COMMENT '触发审核的最大金额（含）',
    required        TINYINT      DEFAULT 1 COMMENT '本级别是否必审',
    auto_approve    TINYINT      DEFAULT 0 COMMENT '本级别是否自动通过',
    enabled         TINYINT      DEFAULT 1,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_level (tenant_id, audit_level),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款审核规则配置';

-- 默认审核规则（按租户）
INSERT IGNORE INTO pos_refund_audit_rule (tenant_id, audit_level, audit_level_name, min_amount, max_amount, required, auto_approve, operator_name)
SELECT t.id, 1, '店长', 0, 5000, 0, 1, 'system' FROM sys_tenant t;

INSERT IGNORE INTO pos_refund_audit_rule (tenant_id, audit_level, audit_level_name, min_amount, max_amount, required, auto_approve, operator_name)
SELECT t.id, 2, '区域经理', 5000, 30000, 1, 0, 'system' FROM sys_tenant t;

INSERT IGNORE INTO pos_refund_audit_rule (tenant_id, audit_level, audit_level_name, min_amount, max_amount, required, auto_approve, operator_name)
SELECT t.id, 3, '财务总监', 30000, 999999, 1, 0, 'system' FROM sys_tenant t;

-- 权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:l1', '退款审核-店长', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 10;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:l2', '退款审核-区域经理', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 11;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:l3', '退款审核-财务总监', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 12;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:rule', '退款审核规则', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 20;

-- 角色权限分配
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('pos:refund:audit:l1','pos:refund:audit:l2','pos:refund:audit:l3','pos:refund:audit:rule');

-- 验证
SELECT
    (SELECT COUNT(*) FROM pos_refund_audit_log) AS audit_log_count,
    (SELECT COUNT(*) FROM pos_refund_audit_rule) AS audit_rule_count;
