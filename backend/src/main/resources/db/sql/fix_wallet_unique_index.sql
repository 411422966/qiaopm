-- P0-T03: crm_wallet 加 member_id + tenant_id 联合唯一索引
-- 防止同一会员在同一租户重复创建钱包
-- 执行前会自动检测索引是否存在，避免重复创建

SET @dbname = DATABASE();
SET @tablename = 'crm_wallet';
SET @indexname = 'uk_member_tenant';

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = @dbname AND table_name = @tablename AND index_name = @indexname) > 0,
  'SELECT ''Index uk_member_tenant already exists, skipping.'' AS message',
  CONCAT('ALTER TABLE ', @tablename, ' ADD UNIQUE INDEX ', @indexname, ' (member_id, tenant_id)')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
