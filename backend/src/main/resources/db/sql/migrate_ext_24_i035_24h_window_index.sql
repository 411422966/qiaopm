-- ============================================================
-- EXT-24 I035 修复：webhook_msg_log 加 24h 时间窗去重索引
-- 对应 Issue：I035（webhook msg_id 时间窗缺失）
-- 修复点：YouzanWebhookController.msgId 幂等去重改为 24h 窗口
-- 配套：新建组合索引 (source, msg_id, created_at) 优化窗口查询
--
-- 风险评估：
--   - CREATE INDEX 在大表上可能锁表（InnoDB online DDL 8.0+ 不锁）
--   - ALTER TABLE ADD INDEX 在 MySQL 5.7 仍会短暂锁表（建议低峰执行）
--   - 历史数据无影响（created_at 已有 INDEX idx_created 单列索引）
--
-- 兼容性：
--   - MySQL 8.0+:  ALGORITHM=INPLACE, LOCK=NONE（在线）
--   - MySQL 5.7:   需低峰执行，锁表时间 = 数据量 / IO 速度
-- ============================================================

-- 仅在索引不存在时创建（MySQL 8.0.29+）
SET @idx_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'webhook_msg_log'
    AND INDEX_NAME   = 'idx_source_msgid_created'
);

SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE webhook_msg_log ADD INDEX idx_source_msgid_created (source, msg_id, created_at) COMMENT ''EXT-24 I035 24h 窗口去重优化''',
  'SELECT ''index idx_source_msgid_created already exists'' AS msg'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
