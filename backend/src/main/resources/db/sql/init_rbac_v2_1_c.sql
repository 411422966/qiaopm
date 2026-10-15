-- ============================================
-- R2.1-C：提成结算批次与冲正 - 数据库初始化
-- 2026-08-09
-- ============================================

-- 1. 提成规则版本化（每改一次生成一条快照）
DROP TABLE IF EXISTS commission_rule_version;
CREATE TABLE commission_rule_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    rule_id BIGINT NOT NULL COMMENT 'commission_rule.id',
    version_no INT NOT NULL DEFAULT 1 COMMENT '版本号（递增）',
    rule_name VARCHAR(100) NOT NULL,
    business_type VARCHAR(20) NOT NULL,
    target_id BIGINT,
    calculation_method VARCHAR(16) NOT NULL,
    rate DECIMAL(7,4),
    fixed_amount DECIMAL(12,2),
    priority INT NOT NULL DEFAULT 0,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    snapshot_reason VARCHAR(255) COMMENT 'CREATE/UPDATE/ROLLBACK',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_rule_version (rule_id, version_no),
    INDEX idx_business (business_type, target_id, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成规则版本快照';

-- 2. 结算批次（按业务时间段生成）
DROP TABLE IF EXISTS commission_settlement_batch;
CREATE TABLE commission_settlement_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    batch_no VARCHAR(64) NOT NULL,
    batch_type VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY/WEEKLY/CUSTOM',
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    salary_month CHAR(7) NOT NULL,
    employee_count INT DEFAULT 0,
    record_count INT DEFAULT 0,
    total_commission DECIMAL(12,2) DEFAULT 0,
    total_adjustment DECIMAL(12,2) DEFAULT 0,
    total_payable DECIMAL(12,2) DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1草稿 2已确认 3已发放 4已取消 5审批中',
    confirm_time DATETIME,
    confirm_by BIGINT,
    confirm_by_name VARCHAR(64),
    pay_time DATETIME,
    pay_by BIGINT,
    pay_by_name VARCHAR(64),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_batch (tenant_id, batch_no),
    INDEX idx_month (salary_month, status),
    INDEX idx_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成结算批次';

-- 3. 批次明细（批次-员工）
DROP TABLE IF EXISTS commission_settlement_batch_item;
CREATE TABLE commission_settlement_batch_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    batch_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(64),
    base_salary DECIMAL(12,2) DEFAULT 0,
    commission_amount DECIMAL(12,2) DEFAULT 0,
    adjustment_amount DECIMAL(12,2) DEFAULT 0,
    reversal_amount DECIMAL(12,2) DEFAULT 0 COMMENT '冲正总额（负数）',
    payable_amount DECIMAL(12,2) DEFAULT 0,
    record_count INT DEFAULT 0,
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_batch_emp (batch_id, employee_id),
    INDEX idx_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算批次员工明细';

-- 4. 提成冲正（独立记录，可追溯）
DROP TABLE IF EXISTS commission_reversal;
CREATE TABLE commission_reversal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    reversal_no VARCHAR(64) NOT NULL,
    reversal_type VARCHAR(16) NOT NULL COMMENT 'REFUND/UNDO_REDEEM/MANUAL',
    source_type VARCHAR(20) NOT NULL COMMENT 'ORDER/PACKAGE_REDEMPTION',
    source_id BIGINT NOT NULL,
    source_item_id BIGINT COMMENT '来源明细ID',
    original_record_id BIGINT COMMENT 'commission_record.id（被冲正的记录）',
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(64),
    amount DECIMAL(12,2) NOT NULL COMMENT '冲正金额（正数）',
    reason VARCHAR(500),
    status TINYINT DEFAULT 1 COMMENT '1待审核 2已生效 3已拒绝',
    approve_time DATETIME,
    approve_by BIGINT,
    approve_by_name VARCHAR(64),
    batch_id BIGINT COMMENT '关联的结算批次ID',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_reversal (tenant_id, reversal_no),
    INDEX idx_source (source_type, source_id),
    INDEX idx_employee (employee_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成冲正记录';

-- 5. 现有 commission_record 扩展字段（已存在则扩展）
-- ALTER 加字段用 IF NOT EXISTS 兜底
ALTER TABLE commission_record ADD COLUMN reversal_status TINYINT DEFAULT 0 COMMENT '0未冲正 1已冲正' AFTER status;
ALTER TABLE commission_record ADD COLUMN reversal_id BIGINT DEFAULT NULL COMMENT 'commission_reversal.id' AFTER reversal_status;
ALTER TABLE commission_record ADD COLUMN rule_version INT DEFAULT 1 COMMENT 'commission_rule_version.version_no' AFTER rule_id;

-- 6. 现有 commission_rule 扩展字段
ALTER TABLE commission_rule ADD COLUMN version_no INT DEFAULT 1 COMMENT '当前版本号' AFTER priority;
ALTER TABLE commission_rule ADD COLUMN is_active TINYINT DEFAULT 1 COMMENT '1现行 0历史' AFTER status;

-- 7. RBAC 权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'commission:batch', '提成结算批次', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 70;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'commission:batch:export', '批次导出', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='commission:batch') t LIMIT 1), 10;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'commission:reversal', '提成冲正', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='commission:batch') t LIMIT 1), 11;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code IN ('commission:batch','commission:batch:export','commission:reversal');

-- 8. 演示：把现有规则写一条 v1 快照
INSERT IGNORE INTO commission_rule_version
(tenant_id, store_id, rule_id, version_no, rule_name, business_type, target_id, calculation_method,
 rate, fixed_amount, priority, effective_date, expiry_date, snapshot_reason, operator_name)
SELECT tenant_id, store_id, id, 1, rule_name, business_type, target_id, calculation_method,
       rate, fixed_amount, priority, effective_date, expiry_date, 'CREATE', 'system'
FROM commission_rule
WHERE deleted = 0;

-- 9. 验证
SELECT 'commission_rule_version' tbl, COUNT(*) cnt FROM commission_rule_version
UNION ALL SELECT 'commission_settlement_batch', COUNT(*) FROM commission_settlement_batch
UNION ALL SELECT 'commission_settlement_batch_item', COUNT(*) FROM commission_settlement_batch_item
UNION ALL SELECT 'commission_reversal', COUNT(*) FROM commission_reversal;
