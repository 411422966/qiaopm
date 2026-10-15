-- DCEP-01 硬钱包扩展（2026-09-04）
-- 为 biz_dcep_hardware_wallet 增加：
--   pin_hash        VARCHAR(255)  PIN BCrypt 哈希
--   private_key_enc VARCHAR(2048) 私钥（AES 加密存储，演示用 base64 即可）
--   daily_used      DECIMAL(15,2) 当日累计消费
--   daily_reset_at  DATETIME      当日限额重置时间

ALTER TABLE biz_dcep_hardware_wallet
    ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255) DEFAULT NULL COMMENT 'PIN BCrypt 哈希',
    ADD COLUMN IF NOT EXISTS private_key_enc VARCHAR(2048) DEFAULT NULL COMMENT '私钥(AES加密)',
    ADD COLUMN IF NOT EXISTS daily_used DECIMAL(15,2) DEFAULT 0 COMMENT '当日累计消费',
    ADD COLUMN IF NOT EXISTS daily_reset_at DATETIME DEFAULT NULL COMMENT '当日限额重置时间';

-- 索引：用于按钱包号快速查找
ALTER TABLE biz_dcep_hardware_wallet
    ADD INDEX IF NOT EXISTS idx_se_chip (se_chip_id),
    ADD INDEX IF NOT EXISTS idx_nfc_sim (nfc_sim_id);
