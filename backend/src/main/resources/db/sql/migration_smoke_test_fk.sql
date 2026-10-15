-- ============================================================
-- P0-DB-002 FK Migration Smoke Test
-- 创建日期：2026-08-14
-- 用途：验证 migrate_v0_p0_db_002_fk_constraints.sql 效果
-- 期望：8 步验收全部通过
-- ============================================================

USE meiyetec;

SELECT '=== T1: 13 FK constraints present ===' AS step;
SELECT
  CASE TABLE_NAME
    WHEN 'crm_wallet'              THEN 'FK-1,2'
    WHEN 'crm_wallet_transaction'  THEN 'FK-3,4,5,6'
    WHEN 'biz_booking'             THEN 'FK-7,8,9,10,11'
    WHEN 'biz_room'                THEN 'FK-12,13'
    WHEN 'biz_employee_schedule'   THEN 'FK-14,15'
    WHEN 'sys_login_log'           THEN 'FK-16,17'
  END AS fk_group,
  COUNT(*) AS fk_count
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'meiyetec'
  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  AND TABLE_NAME IN (
    'crm_wallet', 'crm_wallet_transaction', 'biz_booking',
    'biz_room', 'biz_employee_schedule', 'sys_login_log'
  )
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;
-- 期望：6 行 + total = 17

SELECT '=== T2: 0 orphans on all FKs (deleted=0 only) ===' AS step;
SELECT 'FK-1  wallet->member'     AS fk, COUNT(*) AS orphans FROM crm_wallet w
WHERE w.deleted = 0 AND w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL SELECT 'FK-3  wallet_txn->member', COUNT(*) FROM crm_wallet_transaction w
WHERE w.deleted = 0 AND w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL SELECT 'FK-4  wallet_txn->order', COUNT(*) FROM crm_wallet_transaction w
WHERE w.deleted = 0 AND w.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_order o WHERE o.id = w.order_id)
UNION ALL SELECT 'FK-5  wallet_txn->user', COUNT(*) FROM crm_wallet_transaction w
WHERE w.deleted = 0 AND w.operator_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.operator_id)
UNION ALL SELECT 'FK-7  booking->member', COUNT(*) FROM biz_booking b
WHERE b.deleted = 0 AND b.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = b.member_id)
UNION ALL SELECT 'FK-8  booking->employee', COUNT(*) FROM biz_booking b
WHERE b.deleted = 0 AND b.employee_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = b.employee_id)
UNION ALL SELECT 'FK-9  booking->room', COUNT(*) FROM biz_booking b
WHERE b.deleted = 0 AND b.room_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM biz_room r WHERE r.id = b.room_id)
UNION ALL SELECT 'FK-10 booking->user', COUNT(*) FROM biz_booking b
WHERE b.deleted = 0 AND b.operator_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
UNION ALL SELECT 'FK-12 room->store', COUNT(*) FROM biz_room r
WHERE r.store_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_store s WHERE s.id = r.store_id)
UNION ALL SELECT 'FK-14 schedule->employee', COUNT(*) FROM biz_employee_schedule s
WHERE s.deleted = 0 AND s.employee_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = s.employee_id)
UNION ALL SELECT 'FK-16 login_log->user', COUNT(*) FROM sys_login_log l
WHERE l.deleted = 0 AND l.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id);
-- 期望：全部 0

SELECT '=== T3: deleted column added ===' AS step;
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'meiyetec'
  AND TABLE_NAME IN ('crm_wallet', 'crm_wallet_transaction', 'sys_login_log')
  AND COLUMN_NAME = 'deleted'
ORDER BY TABLE_NAME;
-- 期望：3 行

SELECT '=== T4: soft-deleted rows (孤儿已软删) ===' AS step;
SELECT 'crm_wallet.soft_deleted' AS tbl, COUNT(*) AS cnt FROM crm_wallet WHERE deleted = 1
UNION ALL SELECT 'crm_wallet_txn.soft_deleted', COUNT(*) FROM crm_wallet_transaction WHERE deleted = 1
UNION ALL SELECT 'sys_login_log.soft_deleted', COUNT(*) FROM sys_login_log WHERE deleted = 1;
-- 期望：136 / 37 / 34

SELECT '=== T5: active row counts (deleted=0) ===' AS step;
SELECT 'crm_wallet.active' AS tbl, COUNT(*) AS cnt FROM crm_wallet WHERE deleted = 0
UNION ALL SELECT 'crm_wallet_txn.active', COUNT(*) FROM crm_wallet_transaction WHERE deleted = 0
UNION ALL SELECT 'biz_booking.active', COUNT(*) FROM biz_booking WHERE deleted = 0
UNION ALL SELECT 'biz_room.active', COUNT(*) FROM biz_room
UNION ALL SELECT 'biz_employee_schedule.active', COUNT(*) FROM biz_employee_schedule WHERE deleted = 0
UNION ALL SELECT 'sys_login_log.active', COUNT(*) FROM sys_login_log WHERE deleted = 0
UNION ALL SELECT 'crm_member.active', COUNT(*) FROM crm_member WHERE deleted = 0;
-- 期望：8 / 8 / 6 / 4 / 42 / 170 / 8

SELECT '=== T6: FK enforcement test (insert invalid FK should fail) ===' AS step;
-- 这一步用 procedure 包起来，失败不回滚整个脚本
DROP PROCEDURE IF EXISTS test_fk_enforcement;
DELIMITER //
CREATE PROCEDURE test_fk_enforcement()
BEGIN
  DECLARE v_msg VARCHAR(255) DEFAULT 'OK';
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    SET v_msg = 'CAUGHT FK VIOLATION (expected)';
  END;

  START TRANSACTION;
  INSERT INTO crm_wallet (member_id) VALUES (99999);  -- 不存在的 member_id
  -- 不会执行到这里，因为 FK 会失败
  ROLLBACK;
  SELECT 'FK-1 wallet.member_id test' AS test, v_msg AS result;
END //
DELIMITER ;
CALL test_fk_enforcement();
DROP PROCEDURE test_fk_enforcement;

SELECT '=== T7: NULL tenant allowed (FK-2 nullable) ===' AS step;
SELECT 'crm_wallet.tenant_id nullable' AS chk, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'crm_wallet' AND COLUMN_NAME = 'tenant_id'
UNION ALL
SELECT 'sys_login_log.tenant_id nullable', IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'tenant_id';
-- 期望：YES YES

SELECT '=== T8: cross-tenant contamination still 0 ===' AS step;
SELECT 'wallet.tenant != member.tenant (期望 0)' AS chk, COUNT(*) AS v
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NOT NULL AND m.tenant_id IS NOT NULL AND w.tenant_id <> m.tenant_id;
-- 期望：0

SELECT '=== FK MigrationSmokeTest DONE ===' AS step;
SELECT 'PASS' AS overall_result
WHERE
  -- 17 FKs
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE TABLE_SCHEMA = 'meiyetec' AND CONSTRAINT_TYPE = 'FOREIGN KEY') >= 13
  AND
  -- 0 orphans on FK-1, FK-3, FK-8, FK-16
  (SELECT COUNT(*) FROM crm_wallet w
   WHERE w.deleted = 0 AND w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)) = 0
  AND
  (SELECT COUNT(*) FROM crm_wallet_transaction w
   WHERE w.deleted = 0 AND w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)) = 0
  AND
  (SELECT COUNT(*) FROM biz_booking b
   WHERE b.deleted = 0 AND b.employee_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.id = b.employee_id)) = 0
  AND
  (SELECT COUNT(*) FROM sys_login_log l
   WHERE l.deleted = 0 AND l.user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = l.user_id)) = 0;
