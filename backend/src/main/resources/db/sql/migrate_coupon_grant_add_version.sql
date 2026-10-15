-- ============================================================
-- B-5 · market_coupon_grant 添加 version 字段用于乐观锁
-- ============================================================
-- 背景：
--   优惠券核销（useCoupon）当前是 check-then-update 模式：
--   1) selectById 拿到 grant
--   2) 检查 status == 1
--   3) updateById 设置 status = 2
--   并发场景下两次请求都能通过 status==1 检查，导致一张券被核销两次。
--
-- 修复：
--   market_coupon_grant 增加 version 字段（默认 0），核销时用
--   UPDATE ... WHERE id=? AND version=? 原子 CAS，
--   影响 0 行 = CAS 失败（已被人核销/状态变更），让调用方识别后抛错回滚。

USE meiyetec;

-- 1. 加 version 列（幂等：列已存在则跳过）
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'market_coupon_grant'
      AND COLUMN_NAME = 'version'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE market_coupon_grant ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号''',
    'SELECT 1 AS skipped');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'market_coupon_grant'
  AND COLUMN_NAME = 'version';