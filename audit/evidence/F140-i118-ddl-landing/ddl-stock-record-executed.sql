USE meiyetec;
SET @db := 'meiyetec';
SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='stock_record' AND COLUMN_NAME='dual_verify_status'),
  'SELECT ''SKIP stock_record.dual_verify_status already exists'' AS info',
  'ALTER TABLE `stock_record` ADD COLUMN `dual_verify_status` VARCHAR(16) DEFAULT ''NOT_REQUIRED'' COMMENT ''dual verify status'''
);
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_type`, `default_value`, `remark`, `create_time`)
VALUES ('stock_dual_verify_threshold', '1000', 'NUMBER', '1000', 'dual verify threshold in CNY', NOW());

SELECT 'stock_record.dual_verify_status' AS obj, COUNT(*) AS cnt
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=@db AND TABLE_NAME='stock_record' AND COLUMN_NAME='dual_verify_status'
UNION ALL
SELECT 'sys_config.threshold', COUNT(*) FROM sys_config WHERE config_key='stock_dual_verify_threshold';
