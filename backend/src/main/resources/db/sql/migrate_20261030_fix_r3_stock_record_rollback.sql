-- ====================================================================
-- migrate_20261030_fix_r3_stock_record_rollback.sql — 回滚 migrate_20261030_fix_r3_stock_record.sql
-- 关联：backend/src/main/resources/db/sql/migrate_20261030_fix_r3_stock_record.sql
-- 警告：DROP COLUMN 会丢失 stock_record 已有行的 dual_verify_status 取值，
--       执行前请确认该列已有数据可接受丢失（或已备份）。
-- ====================================================================

USE meiyetec;
SET @db = DATABASE();

-- 1. 删除阈值配置
DELETE FROM `sys_config` WHERE `config_key` = 'stock_dual_verify_threshold';

-- 2. 删除补列
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'stock_record'
        AND COLUMN_NAME = 'dual_verify_status') = 0,
    'SELECT ''SKIP column stock_record.dual_verify_status not exists'' AS info',
    'ALTER TABLE `stock_record` DROP COLUMN `dual_verify_status`'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 验证
SELECT 'VERIFY rollback' AS step,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'stock_record'
           AND COLUMN_NAME = 'dual_verify_status') AS col_should_be_0,
       (SELECT COUNT(*) FROM sys_config
         WHERE config_key = 'stock_dual_verify_threshold') AS config_should_be_0;
