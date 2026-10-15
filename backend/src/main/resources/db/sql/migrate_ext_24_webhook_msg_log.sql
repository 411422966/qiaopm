-- ============================================================
-- EXT-24：webhook_msg_log 表（webhook 推送去重 + 审计）
-- 对应功能：/webhook/youzan 接收端的 msg_id 幂等去重
-- 写入策略：每个 webhook 推送一行，msg_id + source 唯一
--          重复 msg_id 直接 upsert 命中 → 视为幂等成功
-- ============================================================

CREATE TABLE IF NOT EXISTS webhook_msg_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source VARCHAR(32) NOT NULL COMMENT 'YOUZAN / WECHAT / STRIPE / ...',
  msg_id VARCHAR(96) NOT NULL COMMENT 'webhook 携带的唯一消息 ID（去重键）',
  msg_type VARCHAR(64) DEFAULT NULL COMMENT 'TRADE_ORDER / TRADE_PAID / ...',
  external_order_id VARCHAR(64) DEFAULT NULL COMMENT '外部订单号（辅助索引，非唯一）',
  status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED / PROCESSED / FAILED / DUPLICATE',
  raw_payload MEDIUMTEXT DEFAULT NULL COMMENT '原始 body（去重场景下可空）',
  signature VARCHAR(128) DEFAULT NULL COMMENT 'X-Youzan-Sign 头值，便于审计',
  error_message VARCHAR(500) DEFAULT NULL,
  processed_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_msg (source, msg_id),
  INDEX idx_source_order (source, external_order_id),
  INDEX idx_created (created_at),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='Webhook 推送去重 + 审计日志（EXT-24）';
