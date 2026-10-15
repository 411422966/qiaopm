-- =============================================================================
-- P0-MONEY-002 · 钱包漂移对账 & 修复脚本
-- =============================================================================
-- 目的：
--   1. 检测 crm_wallet.balance 与 crm_member.balance 之间的漂移
--   2. 以 crm_wallet.balance（SSOT）为权威值，回填 crm_member.balance
--   3. 输出 WALLET_DRIFT_REPORT 数据（含漂移前后对比）
--
-- 用法：
--   1) 先跑 DRY-RUN：SELECT * FROM wallet_drift_check;
--   2) 确认无误后，跑修复：UPDATE crm_member m JOIN crm_wallet w ...;
--   3) 再跑一次验证漂移数 = 0
--
-- 兼容性：MySQL 8.0+
-- =============================================================================

USE meiyetec;

-- 备份当前漂移数据（写入日志表，若不存在则创建）
CREATE TABLE IF NOT EXISTS crm_wallet_drift_log (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    member_id       BIGINT NOT NULL,
    tenant_id       BIGINT,
    member_balance  DECIMAL(12,2) NOT NULL,
    wallet_balance  DECIMAL(12,2) NOT NULL,
    diff            DECIMAL(12,2) NOT NULL,
    fix_type        VARCHAR(32) NOT NULL COMMENT 'AUTO_FIX / MANUAL_REVIEW',
    fixed_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(255) DEFAULT 'P0-MONEY-002 batch 4 auto-fix',
    PRIMARY KEY (id),
    KEY idx_member (member_id),
    KEY idx_fixed_at (fixed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包漂移修复日志';

-- =============================================================================
-- Step 1: DRY-RUN 漂移检测（不写数据）
-- =============================================================================
DROP VIEW IF EXISTS v_wallet_drift;
CREATE VIEW v_wallet_drift AS
SELECT
    m.id AS member_id,
    m.tenant_id,
    m.name,
    m.phone,
    COALESCE(m.balance, 0) AS member_balance,
    COALESCE(w.balance, 0) AS wallet_balance,
    COALESCE(m.balance, 0) - COALESCE(w.balance, 0) AS diff,
    CASE
        WHEN w.id IS NULL THEN 'WALLET_MISSING'
        WHEN m.balance IS NULL THEN 'MEMBER_BALANCE_NULL'
        ELSE 'DRIFT'
    END AS drift_type
FROM crm_member m
LEFT JOIN crm_wallet w ON w.member_id = m.id AND w.deleted = 0
WHERE m.deleted = 0
  AND (m.id NOT IN (SELECT member_id FROM crm_wallet WHERE deleted = 0)
       OR COALESCE(m.balance, 0) != COALESCE(w.balance, 0));

SELECT
    drift_type,
    COUNT(*) AS cnt,
    SUM(ABS(diff)) AS total_diff_abs
FROM v_wallet_drift
GROUP BY drift_type;

SELECT
    'TOTAL' AS summary,
    COUNT(*) AS drift_count,
    SUM(ABS(diff)) AS total_diff_abs,
    MAX(ABS(diff)) AS max_diff,
    MIN(ABS(diff)) AS min_diff
FROM v_wallet_drift;

-- =============================================================================
-- Step 2: 备份漂移数据到日志表（修复前快照）
-- =============================================================================
INSERT INTO crm_wallet_drift_log (member_id, tenant_id, member_balance, wallet_balance, diff, fix_type)
SELECT
    member_id,
    tenant_id,
    member_balance,
    wallet_balance,
    diff,
    'PRE_FIX_BACKUP' AS fix_type
FROM v_wallet_drift;

SELECT COUNT(*) AS backup_count FROM crm_wallet_drift_log WHERE fix_type = 'PRE_FIX_BACKUP';

-- =============================================================================
-- Step 3: AUTO-FIX — 以 crm_wallet.balance（SSOT）为准同步 crm_member.balance
-- =============================================================================
-- 仅修复"两边都有钱包且余额不一致"的场景
-- 对于 WALLET_MISSING（无钱包记录）的场景不自动处理，避免影响业务

START TRANSACTION;

UPDATE crm_member m
INNER JOIN crm_wallet w ON w.member_id = m.id AND w.deleted = 0
SET m.balance = w.balance
WHERE m.deleted = 0
  AND (m.balance IS NULL OR m.balance != w.balance);

SELECT ROW_COUNT() AS fixed_rows;

COMMIT;

-- =============================================================================
-- Step 4: 验证漂移 = 0
-- =============================================================================
SELECT
    'POST_FIX_RECHECK' AS phase,
    COUNT(*) AS remaining_drift_count
FROM crm_member m
LEFT JOIN crm_wallet w ON w.member_id = m.id AND w.deleted = 0
WHERE m.deleted = 0
  AND (m.id NOT IN (SELECT member_id FROM crm_wallet WHERE deleted = 0)
       OR COALESCE(m.balance, 0) != COALESCE(w.balance, 0));

-- =============================================================================
-- Step 5: 写入 AUTO_FIX 修复日志
-- =============================================================================
INSERT INTO crm_wallet_drift_log (member_id, tenant_id, member_balance, wallet_balance, diff, fix_type, remark)
SELECT
    m.id,
    m.tenant_id,
    0 AS member_balance_pre,                   -- 已修复，原值在 PRE_FIX_BACKUP
    m.balance AS wallet_balance_post,
    0 AS diff,
    'AUTO_FIX' AS fix_type,
    CONCAT('SSOT=crm_wallet.balance, fixed at ', NOW())
FROM crm_member m
WHERE m.deleted = 0
  AND m.id IN (SELECT member_id FROM crm_wallet_drift_log WHERE fix_type = 'PRE_FIX_BACKUP');

-- =============================================================================
-- Step 6: 漂移报告（人读）
-- =============================================================================
SELECT
    m.id AS member_id,
    m.name,
    m.phone,
    pre.member_balance AS before_member_balance,
    pre.wallet_balance AS authoritative_wallet_balance,
    m.balance AS after_member_balance,
    pre.diff AS original_diff
FROM crm_member m
INNER JOIN crm_wallet_drift_log pre ON pre.member_id = m.id AND pre.fix_type = 'PRE_FIX_BACKUP'
WHERE m.deleted = 0
ORDER BY ABS(pre.diff) DESC;

-- =============================================================================
-- Step 7: 后续预防 — 注册每周漂移巡检
-- =============================================================================
-- （运维侧操作，不在 SQL 范围内）
-- 推荐加入 crontab：
--   0 3 * * 1  /usr/local/bin/mysql ... -e "SELECT COUNT(*) AS drift FROM v_wallet_drift;" | \
--                 [ "$drift" -gt 0 ] && alert "钱包漂移告警: $drift 个成员"
-- =============================================================================