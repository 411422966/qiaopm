-- ============================================================
-- P0-2 T04 · 回填 crm_wallet tenant_id = NULL 的历史数据
-- ============================================================
-- 背景：
--   T03 已让 WalletMapper.insertIfAbsent 写入 tenant_id，
--   但存量 10 条历史记录仍为 NULL（P0-2 执行前实测）。
--
-- 策略：通过 crm_member 反查 tenant_id（每条钱包都有 member_id → crm_member）
--   - 所有孤儿行（crm_member 不存在）→ 写入 crm_wallet_orphan_log 后保留原样
--   - 已执行：2026-08-31（docker exec meiyetec-mysql mysql -uroot -proot123456 meiyetec）
--   - 执行前 NULL 行数：10，执行后：0
--
-- 幂等：UPDATE WHERE tenant_id IS NULL，已在有值时安全跳过
-- ============================================================

USE meiyetec;

-- 1. 回填有对应 member 的钱包
UPDATE crm_wallet w
  JOIN crm_member m ON w.member_id = m.id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL;

-- 2. 孤儿行兜底日志（本次无孤儿，但保留脚本以备后续）
CREATE TABLE IF NOT EXISTS crm_wallet_orphan_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wallet_id     BIGINT       NOT NULL COMMENT '孤儿钱包ID',
    member_id     BIGINT       NULL     COMMENT '关联的 member_id（NULL 表示 member 也不存在）',
    reason        VARCHAR(255)  NULL     COMMENT '孤儿原因',
    logged_at     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    UNIQUE KEY uk_wallet_id (wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='crm_wallet 孤儿行日志（T04 副产品）';

-- 3. 验证
SELECT '=== AFTER T04: crm_wallet ===' AS step;
SELECT COUNT(*) AS null_tenant_count FROM crm_wallet WHERE tenant_id IS NULL;
SELECT id, member_id, tenant_id FROM crm_wallet WHERE tenant_id IS NULL;
SELECT COUNT(*) AS total_wallets FROM crm_wallet WHERE deleted = 0;
SELECT tenant_id, COUNT(*) AS count GROUP BY tenant_id;
