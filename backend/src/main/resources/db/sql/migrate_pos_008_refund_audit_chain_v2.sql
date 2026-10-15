-- ============================================================
-- POS-008 深化：退款多级审核工作流
--   1. pos_refund 加 audit_status 列（显式审核相位）
--   2. 调整默认审核规则阈值（5000/30000 → 500/5000）
-- ============================================================

ALTER TABLE pos_refund
    ADD COLUMN audit_status VARCHAR(32) DEFAULT NULL
        COMMENT '显式审核相位：null/DRAFT/PENDING_STORE/PENDING_HQ/PENDING_FINANCE/APPROVED/REJECTED'
        AFTER audit_required;

ALTER TABLE pos_refund
    ADD INDEX idx_audit_status (audit_status);

-- POS-008：默认审核规则阈值改为 500 / 5000
-- 老数据保留：仅在规则不存在时按新阈值写入
INSERT IGNORE INTO pos_refund_audit_rule (tenant_id, audit_level, audit_level_name, min_amount, max_amount, required, auto_approve, operator_name)
SELECT t.id, 1, '店长',     0,     500,   0, 1, 'system' FROM sys_tenant t;
-- 保留旧规则（L1 0~5000）作为兼容；新增 L1 0~500 仅在原规则为旧阈值时替换
UPDATE pos_refund_audit_rule
SET max_amount = 500, audit_level_name = '店长', auto_approve = 0
WHERE audit_level = 1 AND max_amount = 5000;

-- 新增 L2 总部规则（500~5000）
INSERT IGNORE INTO pos_refund_audit_rule (tenant_id, audit_level, audit_level_name, min_amount, max_amount, required, auto_approve, operator_name)
SELECT t.id, 2, '总部', 500, 5000, 1, 0, 'system' FROM sys_tenant t;
UPDATE pos_refund_audit_rule
SET min_amount = 500, max_amount = 5000, audit_level_name = '总部', required = 1, auto_approve = 0
WHERE audit_level = 2 AND max_amount = 30000;

-- 新增 L3 财务规则（5000~999999）
INSERT IGNORE INTO pos_refund_audit_rule (tenant_id, audit_level, audit_level_name, min_amount, max_amount, required, auto_approve, operator_name)
SELECT t.id, 3, '财务', 5000, 999999, 1, 0, 'system' FROM sys_tenant t;
UPDATE pos_refund_audit_rule
SET min_amount = 5000, audit_level_name = '财务', required = 1, auto_approve = 0
WHERE audit_level = 3 AND min_amount = 30000;

-- 权限：三级分别对应 l1/l2/l3（迁移已写过的 l1/l2/l3 直接复用）
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:store',   '退款审核-门店终审',  'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 30;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:hq',      '退款审核-总部终审',  'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 31;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit:finance', '退款审核-财务终审',  'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='pos:refund:audit') t LIMIT 1), 32;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('pos:refund:audit:store','pos:refund:audit:hq','pos:refund:audit:finance');

-- 验证
SELECT
    (SELECT COUNT(*) FROM pos_refund_audit_rule) AS audit_rule_count,
    (SELECT COUNT(*) FROM sys_permission WHERE permission_code IN ('pos:refund:audit:store','pos:refund:audit:hq','pos:refund:audit:finance')) AS audit_perm_count;
