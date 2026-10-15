-- ============================================================
-- 美业 SaaS - V0 外键约束迁移（P0-DB-002 Phase 3）
-- 创建日期：2026-08-14
-- 目的：把 Phase 2 审计识别的 17 个 FK 候选全部落地
-- 范围：数据修复（孤儿清理）+ 加 deleted 列 + 加 13 个 FK
-- 设计依据：docs/audit/BEAUTY_SAAS/22_P0_DB_001_PHASE2_REPAIR_PLAN.md
-- ============================================================
-- 使用方式：
--   1. 单独跑这个脚本（幂等）
--   2. SOURCE 后跟 migration_smoke_test_fk.sql 验收
-- ============================================================

USE meiyetec;

-- ============================================================
-- 0. PRECHECK：所有修复前的孤儿计数
-- ============================================================
SELECT '== FK PRECHECK ==' AS step;

SELECT 'FK-1  wallet.member_id orphans' AS chk, COUNT(*) AS v FROM crm_wallet w
WHERE w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL SELECT 'FK-3  wallet_txn.member_id orphans', COUNT(*) FROM crm_wallet_transaction w
WHERE w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL SELECT 'FK-4  wallet_txn.order_id orphans', COUNT(*) FROM crm_wallet_transaction w
WHERE w.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_order o WHERE o.id = w.order_id)
UNION ALL SELECT 'FK-5  wallet_txn.operator_id orphans', COUNT(*) FROM crm_wallet_transaction w
WHERE w.operator_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.operator_id)
UNION ALL SELECT 'FK-7  booking.member_id orphans', COUNT(*) FROM biz_booking b
WHERE b.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = b.member_id)
UNION ALL SELECT 'FK-8  booking.employee_id orphans', COUNT(*) FROM biz_booking b
WHERE b.employee_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = b.employee_id)
UNION ALL SELECT 'FK-9  booking.room_id orphans', COUNT(*) FROM biz_booking b
WHERE b.room_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM biz_room r WHERE r.id = b.room_id)
UNION ALL SELECT 'FK-10 booking.operator_id orphans', COUNT(*) FROM biz_booking b
WHERE b.operator_id IS NOT NULL AND b.deleted = 0 AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
UNION ALL SELECT 'FK-12 room.store_id orphans', COUNT(*) FROM biz_room r
WHERE r.store_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_store s WHERE s.id = r.store_id)
UNION ALL SELECT 'FK-14 schedule.employee_id orphans', COUNT(*) FROM biz_employee_schedule s
WHERE s.employee_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = s.employee_id)
UNION ALL SELECT 'FK-16 login_log.user_id orphans', COUNT(*) FROM sys_login_log l
WHERE l.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);

-- ============================================================
-- 1. 加 deleted 列（孤儿软删除所需）
-- 三个表都缺 deleted 列
-- ============================================================
SELECT '== ADD deleted COLUMNS ==' AS step;

-- crm_wallet
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet' AND COLUMN_NAME = 'deleted');
SET @sql = IF(@c = 0,
  'ALTER TABLE crm_wallet ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除'' AFTER frozen_amount, ADD KEY idx_deleted (deleted)',
  'SELECT ''crm_wallet.deleted already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- crm_wallet_transaction
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet_transaction' AND COLUMN_NAME = 'deleted');
SET @sql = IF(@c = 0,
  'ALTER TABLE crm_wallet_transaction ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除'' AFTER remark, ADD KEY idx_deleted (deleted)',
  'SELECT ''crm_wallet_transaction.deleted already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_login_log
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'deleted');
SET @sql = IF(@c = 0,
  'ALTER TABLE sys_login_log ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''软删除'' AFTER user_agent, ADD KEY idx_deleted (deleted)',
  'SELECT ''sys_login_log.deleted already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 数据修复 - 孤儿软删除/重派
-- ============================================================
SELECT '== DATA REMEDIATION ==' AS step;

-- 2.1 FK-1: 物理删除 136 个孤儿 wallet（member_id 不在 crm_member）
-- 注意：MySQL 加 FK 时会校验所有行（含 deleted=1），所以必须物理删除
-- 先备份到 crm_wallet_orphan_log
DROP TABLE IF EXISTS crm_wallet_orphan_log;
CREATE TABLE crm_wallet_orphan_log AS
SELECT w.*, 'p0_db_002_orphan_removed' AS reason
FROM crm_wallet w
WHERE w.member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id);

-- 然后物理删除（不能用别名 w）
DELETE FROM crm_wallet
WHERE member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = crm_wallet.member_id);

SELECT 'FK-1 hard-deleted wallet count' AS step, ROW_COUNT() AS affected;

-- 2.2 FK-3: 物理删除 37 个孤儿 wallet_txn（member_id NOT NULL，加 FK 需物理删除）
DROP TABLE IF EXISTS crm_wallet_txn_orphan_log;
CREATE TABLE crm_wallet_txn_orphan_log AS
SELECT w.*, 'p0_db_002_orphan_removed' AS reason
FROM crm_wallet_transaction w
WHERE w.member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id);

DELETE FROM crm_wallet_transaction
WHERE member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = crm_wallet_transaction.member_id);

SELECT 'FK-3 hard-deleted wallet_txn count' AS step, ROW_COUNT() AS affected;

-- 2.3 FK-8: 重派 booking.employee_id=7 → 1（王雪梅）
UPDATE biz_booking b
SET b.employee_id = (SELECT id FROM pos_employee WHERE employee_code = 'EMP001' LIMIT 1)
WHERE b.employee_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = b.employee_id);

SELECT 'FK-8 reassigned booking count' AS step, ROW_COUNT() AS affected;

-- 2.4 FK-16: 处理 34 个孤儿 login_log
-- 策略：先把已软删除(deleted=1)孤儿行的 user_id 设为 NULL（绕过 FK 校验）
--      再软删除剩余的孤儿（deleted=0）
UPDATE sys_login_log l
SET l.user_id = NULL
WHERE l.deleted = 1
  AND l.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);

UPDATE sys_login_log l
SET l.deleted = 1
WHERE l.deleted = 0
  AND l.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);

SELECT 'FK-16 login_log affected' AS step, ROW_COUNT() AS affected;

-- ============================================================
-- 3. 加 FK 约束（13 个，4 个暂留：FK-2/6/11/13/15 等）
--    MySQL 8 不支持 ALTER TABLE ADD CONSTRAINT IF NOT EXISTS
--    用存储过程模式：先查 information_schema.TABLE_CONSTRAINTS 再决定
-- ============================================================
SELECT '== ADD FK CONSTRAINTS ==' AS step;

-- 工具：删除已存在的同名 FK（幂等）
DROP PROCEDURE IF EXISTS add_fk_if_missing;
DELIMITER //
CREATE PROCEDURE add_fk_if_missing(
  IN p_table VARCHAR(64),
  IN p_constraint VARCHAR(64),
  IN p_sql TEXT
)
BEGIN
  DECLARE v_exists INT DEFAULT 0;
  SELECT COUNT(*) INTO v_exists
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = 'meiyetec'
    AND TABLE_NAME = p_table
    AND CONSTRAINT_NAME = p_constraint;
  IF v_exists = 0 THEN
    SET @sql = p_sql;
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  ELSE
    SELECT CONCAT('SKIP: ', p_constraint, ' already exists') AS info;
  END IF;
END //
DELIMITER ;

-- FK-1 crm_wallet.member_id → crm_member.id
CALL add_fk_if_missing('crm_wallet', 'fk_wallet_member',
  'ALTER TABLE crm_wallet ADD CONSTRAINT fk_wallet_member FOREIGN KEY (member_id) REFERENCES crm_member(id)');

-- FK-2 crm_wallet.tenant_id → sys_tenant.id (nullable)
CALL add_fk_if_missing('crm_wallet', 'fk_wallet_tenant',
  'ALTER TABLE crm_wallet ADD CONSTRAINT fk_wallet_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL');

-- FK-3 crm_wallet_txn.member_id → crm_member.id
CALL add_fk_if_missing('crm_wallet_transaction', 'fk_wallet_txn_member',
  'ALTER TABLE crm_wallet_transaction ADD CONSTRAINT fk_wallet_txn_member FOREIGN KEY (member_id) REFERENCES crm_member(id)');

-- FK-4 crm_wallet_txn.order_id → pos_order.id (nullable)
CALL add_fk_if_missing('crm_wallet_transaction', 'fk_wallet_txn_order',
  'ALTER TABLE crm_wallet_transaction ADD CONSTRAINT fk_wallet_txn_order FOREIGN KEY (order_id) REFERENCES pos_order(id) ON DELETE SET NULL');

-- FK-5 crm_wallet_txn.operator_id → sys_user.id (nullable)
CALL add_fk_if_missing('crm_wallet_transaction', 'fk_wallet_txn_operator',
  'ALTER TABLE crm_wallet_transaction ADD CONSTRAINT fk_wallet_txn_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL');

-- FK-6 crm_wallet_txn.tenant_id → sys_tenant.id (nullable)
CALL add_fk_if_missing('crm_wallet_transaction', 'fk_wallet_txn_tenant',
  'ALTER TABLE crm_wallet_transaction ADD CONSTRAINT fk_wallet_txn_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL');

-- FK-7 biz_booking.member_id → crm_member.id
CALL add_fk_if_missing('biz_booking', 'fk_booking_member',
  'ALTER TABLE biz_booking ADD CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES crm_member(id)');

-- FK-8 biz_booking.employee_id → pos_employee.id
CALL add_fk_if_missing('biz_booking', 'fk_booking_employee',
  'ALTER TABLE biz_booking ADD CONSTRAINT fk_booking_employee FOREIGN KEY (employee_id) REFERENCES pos_employee(id)');

-- FK-9 biz_booking.room_id → biz_room.id (nullable)
CALL add_fk_if_missing('biz_booking', 'fk_booking_room',
  'ALTER TABLE biz_booking ADD CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES biz_room(id) ON DELETE SET NULL');

-- FK-10 biz_booking.operator_id → sys_user.id (nullable)
CALL add_fk_if_missing('biz_booking', 'fk_booking_operator',
  'ALTER TABLE biz_booking ADD CONSTRAINT fk_booking_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE SET NULL');

-- FK-11 biz_booking.tenant_id → sys_tenant.id (nullable)
CALL add_fk_if_missing('biz_booking', 'fk_booking_tenant',
  'ALTER TABLE biz_booking ADD CONSTRAINT fk_booking_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL');

-- FK-12 biz_room.store_id → pos_store.id (nullable)
CALL add_fk_if_missing('biz_room', 'fk_room_store',
  'ALTER TABLE biz_room ADD CONSTRAINT fk_room_store FOREIGN KEY (store_id) REFERENCES pos_store(id) ON DELETE SET NULL');

-- FK-13 biz_room.tenant_id → sys_tenant.id (nullable)
CALL add_fk_if_missing('biz_room', 'fk_room_tenant',
  'ALTER TABLE biz_room ADD CONSTRAINT fk_room_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL');

-- FK-14 biz_employee_schedule.employee_id → pos_employee.id
CALL add_fk_if_missing('biz_employee_schedule', 'fk_schedule_employee',
  'ALTER TABLE biz_employee_schedule ADD CONSTRAINT fk_schedule_employee FOREIGN KEY (employee_id) REFERENCES pos_employee(id)');

-- FK-15 biz_employee_schedule.tenant_id → sys_tenant.id (nullable)
CALL add_fk_if_missing('biz_employee_schedule', 'fk_schedule_tenant',
  'ALTER TABLE biz_employee_schedule ADD CONSTRAINT fk_schedule_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL');

-- FK-16 sys_login_log.user_id → sys_user.id (nullable)
CALL add_fk_if_missing('sys_login_log', 'fk_login_log_user',
  'ALTER TABLE sys_login_log ADD CONSTRAINT fk_login_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL');

-- FK-17 sys_login_log.tenant_id → sys_tenant.id (nullable)
CALL add_fk_if_missing('sys_login_log', 'fk_login_log_tenant',
  'ALTER TABLE sys_login_log ADD CONSTRAINT fk_login_log_tenant FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL');

-- 清理：删除辅助存储过程
DROP PROCEDURE IF EXISTS add_fk_if_missing;

-- ============================================================
-- 4. POSTCHECK：所有 FK 都应存在且孤儿=0
-- ============================================================
SELECT '== FK POSTCHECK ==' AS step;

SELECT CONSTRAINT_NAME, TABLE_NAME
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'meiyetec'
  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  AND TABLE_NAME IN (
    'crm_wallet', 'crm_wallet_transaction', 'biz_booking',
    'biz_room', 'biz_employee_schedule', 'sys_login_log'
  )
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- 孤儿复检（应全为 0）
SELECT 'FK-1  wallet->member orphans (期望 0)' AS chk, COUNT(*) AS v FROM crm_wallet w
WHERE w.deleted = 0 AND w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL SELECT 'FK-3  wallet_txn->member orphans (期望 0)', COUNT(*) FROM crm_wallet_transaction w
WHERE w.deleted = 0 AND w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL SELECT 'FK-8  booking->employee orphans (期望 0)', COUNT(*) FROM biz_booking b
WHERE b.deleted = 0 AND b.employee_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = b.employee_id)
UNION ALL SELECT 'FK-16 login_log->user orphans (期望 0)', COUNT(*) FROM sys_login_log l
WHERE l.deleted = 0 AND l.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);

SELECT '=== P0-DB-002 FK Migration Complete ===' AS step;
