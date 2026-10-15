-- ============================================
-- M09 commission module - DB verify
-- 2026-08-03
-- ============================================

-- 1) Table counts
SELECT 'commission_rule' AS tbl, COUNT(*) AS cnt FROM commission_rule WHERE deleted=0
UNION ALL
SELECT 'commission_record', COUNT(*) FROM commission_record WHERE deleted=0
UNION ALL
SELECT 'commission_settlement', COUNT(*) FROM commission_settlement WHERE deleted=0
UNION ALL
SELECT 'commission_settlement_record', COUNT(*) FROM commission_settlement_record WHERE deleted=0;

-- 2) Verify default rule exists
SELECT id, rule_name, business_type, calculation_method, rate, priority
FROM commission_rule
WHERE tenant_id=1 AND store_id=1 AND business_type='SERVICE' AND target_id IS NULL AND deleted=0;

-- 3) CRUD test (transaction, will rollback)
START TRANSACTION;

INSERT INTO commission_rule
(tenant_id, store_id, rule_name, business_type, target_id, calculation_method,
 fixed_amount, priority, effective_date, status, remark)
VALUES (1, 1, 'VERIFY-RULE', 'SERVICE', 999999, 'FIXED', 8.88, 999,
        '2026-01-01', 1, 'verify rollback');

SET @v_rule_id = LAST_INSERT_ID();
SELECT 'INSERT' AS step, id, rule_name FROM commission_rule WHERE id = @v_rule_id;

UPDATE commission_rule SET fixed_amount = 9.99 WHERE id = @v_rule_id;
SELECT 'UPDATE' AS step, id, fixed_amount FROM commission_rule WHERE id = @v_rule_id;

DELETE FROM commission_rule WHERE id = @v_rule_id;
SELECT 'DELETE' AS step, COUNT(*) AS remain FROM commission_rule WHERE id = @v_rule_id;

ROLLBACK;

-- 4) Index usage verification
EXPLAIN SELECT *
FROM commission_record
WHERE employee_id = 2 AND salary_month = '2026-08' AND status = 1;

EXPLAIN SELECT *
FROM commission_rule
WHERE tenant_id = 1 AND store_id = 1 AND business_type = 'SERVICE'
  AND target_id IS NULL AND status = 1
  AND effective_date <= CURDATE()
  AND (expiry_date IS NULL OR expiry_date >= CURDATE())
ORDER BY priority DESC LIMIT 1;
