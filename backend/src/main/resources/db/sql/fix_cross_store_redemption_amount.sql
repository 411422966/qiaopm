-- =============================================
-- 修复跨店核销记录 (crm_cross_store_redemption) 的金额字段不一致
--
-- 背景：
--   历史数据 (id 1-6 的种子记录) 没有正确的 net_amount / cross_fee 计算。
--   表现为：原金额 ≠ 实收 + 手续费
--
-- 修复口径（与业务约定一致）：
--   original_amount = net_amount + cross_fee
--
-- 安全策略：
--   1. SELECT 预览（受影响行 + 修复前后对比）—— 不 UPDATE
--   2. SELECT 仅当 original_amount > 0 且 net_amount + cross_fee != original_amount
--   3. UPDATE 之前先创建临时备份表，事后可回滚
--   4. UPDATE 后再次 SELECT 验证
-- =============================================

USE meiyetec;

SET NAMES utf8mb4;

-- -----------------------------------------------------
-- Step 1. 备份：把要改的行写到 backup_crm_cross_store_redemption_amount_fix_YYYYMMDD
-- -----------------------------------------------------
DROP TABLE IF EXISTS backup_crm_cross_store_redemption_amount_fix;
CREATE TABLE backup_crm_cross_store_redemption_amount_fix AS
SELECT id, tenant_id, order_no, redemption_type,
       original_amount, cross_fee, net_amount,
       (original_amount - cross_fee)           AS expected_net_amount,
       (original_amount - (cross_fee + net_amount)) AS drift_amount
FROM crm_cross_store_redemption
WHERE deleted = 0
  AND original_amount > 0
  AND ROUND(original_amount - (cross_fee + net_amount), 2) <> 0;

SELECT 'STEP1 备份预览（受影响行）' AS step;
SELECT * FROM backup_crm_cross_store_redemption_amount_fix ORDER BY id;

-- -----------------------------------------------------
-- Step 2. 实际 UPDATE：把 net_amount 补齐为 original_amount - cross_fee
--         (cross_fee 保留原值，避免误改历史已收的手续费)
-- -----------------------------------------------------
START TRANSACTION;

UPDATE crm_cross_store_redemption r
JOIN backup_crm_cross_store_redemption_amount_fix b ON r.id = b.id
SET r.net_amount = ROUND(r.original_amount - r.cross_fee, 2)
WHERE r.deleted = 0;

SELECT ROW_COUNT() AS updated_rows;

-- -----------------------------------------------------
-- Step 3. 验证：修复后应当 0 行满足"仍不一致"
-- -----------------------------------------------------
SELECT 'STEP3 验证（修复后应为空）' AS step;
SELECT id, order_no, original_amount, cross_fee, net_amount,
       ROUND(original_amount - (cross_fee + net_amount), 2) AS drift
FROM crm_cross_store_redemption
WHERE deleted = 0
  AND original_amount > 0
  AND ROUND(original_amount - (cross_fee + net_amount), 2) <> 0;

-- 无异常则提交；有异常则 ROLLBACK
COMMIT;

-- -----------------------------------------------------
-- Step 4. 顺带补一个 CHECK 约束防再犯（MySQL 8.0.16+）
--         tolerance 0.01 元，避免浮点误差
-- -----------------------------------------------------
ALTER TABLE crm_cross_store_redemption
    DROP CHECK IF EXISTS chk_redemption_amount_balance;

ALTER TABLE crm_cross_store_redemption
    ADD CONSTRAINT chk_redemption_amount_balance
    CHECK (
        original_amount = 0
        OR ABS((cross_fee + net_amount) - original_amount) <= 0.01
    );

-- -----------------------------------------------------
-- Step 5. 修复后统计
-- -----------------------------------------------------
SELECT '修复后净收合计' AS metric,
       SUM(net_amount)   AS value,
       COUNT(*)         AS row_count
FROM crm_cross_store_redemption
WHERE deleted = 0 AND status = 1;
