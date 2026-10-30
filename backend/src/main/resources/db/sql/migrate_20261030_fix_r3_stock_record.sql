-- ====================================================================
-- migrate_20261030_fix_r3_stock_record.sql — R3 补列：stock_record.dual_verify_status
-- 关联问题单：audit/issues/I133-schema-drift-missing-columns.md（R3 补充）
-- 关联证据：audit/evidence/F140-i118-ddl-landing/
-- 根因：backend/src/main/resources/db/sql/migrate_p2_11_dual_verify.sql:43-44
--       定义了 stock_record.dual_verify_status，但该迁移从未在目标库执行；
--       同批次 I118 落地时只补了 stock_check.dual_verify_status，漏了 stock_record。
--       StockDualVerifyService.updateRecordStatus（:112-119）会写该字段，
--       StockController.trace（:1523-1527）通过 MyBatis-Plus 查询 stock_record
--       会 SELECT 该列 → GET /stock/trace/{id} 500
--       Unknown column 'dual_verify_status' in 'field list'
-- 影响端点：GET /stock/trace/{id}（及一切查询 StockRecord 的链路）
-- 目标库：127.0.0.1:3307/meiyetec
-- 兼容：MySQL < 8.0.29 不支持 ADD COLUMN IF NOT EXISTS，
--       故一律走 information_schema 判存 + PREPARE 动态执行
-- 执行方式：
--           mysql -h 127.0.0.1 -P 3307 -u <user> -p meiyetec < backend/src/main/resources/db/sql/migrate_20261030_fix_r3_stock_record.sql
-- 回滚：   mysql ... < backend/src/main/resources/db/sql/migrate_20261030_fix_r3_stock_record_rollback.sql
-- 已执行：2026-10-30（目标库 meiyetec 实测成功）
-- ====================================================================

USE meiyetec;
SET @db = DATABASE();

-- --------------------------------------------------------------------
-- 0. 前置检查：目标表必须存在
-- --------------------------------------------------------------------
SELECT 'PRECHECK stock_record exists' AS step,
       COUNT(*) AS found
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'stock_record';

-- --------------------------------------------------------------------
-- 1. 补列：stock_record.dual_verify_status
--    类型/默认值与 migrate_p2_11_dual_verify.sql:44 保持一致
-- --------------------------------------------------------------------
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'stock_record'
        AND COLUMN_NAME = 'dual_verify_status') > 0,
    'SELECT ''SKIP column stock_record.dual_verify_status already exists'' AS info',
    'ALTER TABLE `stock_record` ADD COLUMN `dual_verify_status` VARCHAR(16) DEFAULT ''NOT_REQUIRED'' COMMENT ''双人核销状态: NOT_REQUIRED无需 PENDING待复核 VERIFIED已复核 REJECTED已驳回'''
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------
-- 2. 阈值配置：sys_config.stock_dual_verify_threshold
--    读方：StockDualVerifyService.getThreshold（:55-63），缺省回落 1000
--    注：实际 sys_config 列为 config_key / config_value / config_group /
--        description / sort_order / created_at / updated_at，
--        与 migrate_p2_11_dual_verify.sql:60-62 假设的
--        config_type / default_value / remark 不一致 → 按实际列写
-- --------------------------------------------------------------------
INSERT IGNORE INTO `sys_config`
  (`config_key`, `config_value`, `config_group`, `description`, `sort_order`, `created_at`, `updated_at`)
VALUES
  ('stock_dual_verify_threshold', '1000', 'stock', '双人核销阈值（元），total_amount > 此值时触发双人复核，默认1000', 100, NOW(), NOW());

-- --------------------------------------------------------------------
-- 3. 验证
-- --------------------------------------------------------------------
SELECT 'VERIFY column' AS step,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'stock_record'
           AND COLUMN_NAME = 'dual_verify_status') AS dual_verify_status_col_should_be_1;

SELECT 'VERIFY config' AS step,
       (SELECT COUNT(*) FROM sys_config
         WHERE config_key = 'stock_dual_verify_threshold') AS threshold_should_be_1;
