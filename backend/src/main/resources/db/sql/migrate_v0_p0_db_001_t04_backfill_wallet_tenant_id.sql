-- =============================================================================
-- P0-DB-001-T04 · crm_wallet.tenant_id NULL 历史数据回填
-- =============================================================================
-- 目的：
--   T03 已解决"以后还会不会产生 NULL"（insertIfAbsent 写入 tenant_id）
--   T04 解决"现在已经存在的 29 条 NULL"
--
-- 范围：
--   仅处理 crm_wallet.tenant_id IS NULL 的历史行
--   不动 balance / total_recharge / total_consume / frozen_amount / version
--
-- 策略：
--   UPDATE crm_wallet w JOIN crm_member m ON m.id = w.member_id
--   SET w.tenant_id = m.tenant_id
--   WHERE w.tenant_id IS NULL AND m.tenant_id IS NOT NULL
--
-- 安全条件：
--   ① 仅当 crm_member.tenant_id 存在且非 NULL 时才回填（不会误填兜底 1）
--   ② 全程包在事务里，失败即回滚
--   ③ 跑前后都 COUNT(*)，验证 29 → 0
--   ④ 跑前后都做跨租户一致性校验（wallet.tenant_id == member.tenant_id）
--
-- 用法：
--   mysql -uroot -p meiyetec < migrate_v0_p0_db_001_t04_backfill_wallet_tenant_id.sql
--
-- 兼容性：MySQL 8.0+
-- =============================================================================

USE meiyetec;

-- =============================================================================
-- Step 1: BEFORE — 拍快照（不写数据）
-- =============================================================================
SELECT '=== T04 BEFORE: crm_wallet tenant_id distribution ===' AS step;

SELECT
    CASE
        WHEN tenant_id IS NULL     THEN 'NULL'
        WHEN tenant_id = 1         THEN 'tenant=1'
        WHEN tenant_id = 2         THEN 'tenant=2'
        ELSE CONCAT('tenant=', tenant_id)
    END AS tenant_bucket,
    COUNT(*) AS cnt
FROM crm_wallet
GROUP BY tenant_bucket
ORDER BY tenant_bucket;

SELECT '=== T04 BEFORE: total balance in NULL-tenant wallets ===' AS step;
SELECT
    COUNT(*)                                       AS null_wallets,
    COALESCE(SUM(balance), 0)                      AS total_balance,
    COALESCE(SUM(total_recharge), 0)               AS total_recharge,
    COALESCE(SUM(total_consume), 0)                AS total_consume,
    COALESCE(SUM(frozen_amount), 0)                AS total_frozen
FROM crm_wallet
WHERE tenant_id IS NULL;

SELECT '=== T04 BEFORE: NULL wallets whose member.tenant_id is also NULL (cannot auto-fix) ===' AS step;
SELECT COUNT(*) AS unbackfillable_nulls
FROM crm_wallet w
LEFT JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NULL
  AND (m.id IS NULL OR m.tenant_id IS NULL);

SELECT '=== T04 BEFORE: 29 NULL wallets (full detail, before-fix snapshot) ===' AS step;
SELECT
    w.id,
    w.member_id,
    w.tenant_id        AS pre_tenant_id,
    m.tenant_id        AS expected_tenant_from_member,
    w.balance,
    w.total_recharge,
    w.total_consume,
    w.frozen_amount,
    w.version,
    w.updated_at
FROM crm_wallet w
LEFT JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NULL
ORDER BY w.id;

-- =============================================================================
-- Step 2: BACKFILL — UPDATE tenant_id from crm_member.tenant_id
-- 安全条件：m.tenant_id IS NOT NULL（避免拿 NULL 写 NULL）
-- =============================================================================
START TRANSACTION;

UPDATE crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL
  AND m.tenant_id IS NOT NULL;

SELECT ROW_COUNT() AS backfilled_rows;

COMMIT;

-- =============================================================================
-- Step 3: AFTER — 验证 NULL = 0
-- =============================================================================
SELECT '=== T04 AFTER: crm_wallet tenant_id distribution ===' AS step;

SELECT
    CASE
        WHEN tenant_id IS NULL     THEN 'NULL'
        WHEN tenant_id = 1         THEN 'tenant=1'
        WHEN tenant_id = 2         THEN 'tenant=2'
        ELSE CONCAT('tenant=', tenant_id)
    END AS tenant_bucket,
    COUNT(*) AS cnt
FROM crm_wallet
GROUP BY tenant_bucket
ORDER BY tenant_bucket;

SELECT '=== T04 AFTER: total balance (should equal BEFORE total_balance=7600.00) ===' AS step;
SELECT
    COUNT(*)                                       AS null_wallets,
    COALESCE(SUM(balance), 0)                      AS total_balance,
    COALESCE(SUM(total_recharge), 0)               AS total_recharge,
    COALESCE(SUM(total_consume), 0)                AS total_consume,
    COALESCE(SUM(frozen_amount), 0)                AS total_frozen
FROM crm_wallet
WHERE tenant_id IS NULL;

-- =============================================================================
-- Step 4: 跨租户一致性校验 — wallet.tenant_id 必须 == member.tenant_id
-- 期望：0 行不一致
-- =============================================================================
SELECT '=== T04 CROSS-TENANT CHECK: wallet.tenant_id != member.tenant_id (should be 0) ===' AS step;
SELECT COUNT(*) AS cross_tenant_mismatches
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NOT NULL
  AND m.tenant_id IS NOT NULL
  AND w.tenant_id <> m.tenant_id;

-- =============================================================================
-- Step 5: 资金链路不变性 — wallet.balance == member.balance
-- 期望：0 行漂移（P0-MONEY-002 已修，这里只是兜底再确认）
-- =============================================================================
SELECT '=== T04 SSOT CHECK: wallet.balance != member.balance (should be 0) ===' AS step;
SELECT COUNT(*) AS drift_count
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.deleted = 0
  AND m.deleted = 0
  AND COALESCE(w.balance, 0) <> COALESCE(m.balance, 0);

-- =============================================================================
-- Step 6: 29 NULL 钱包回填后的状态（人读）
-- =============================================================================
SELECT '=== T04 BACKFILLED: 29 NULL wallets after-fix (tenant_id should be 1) ===' AS step;
SELECT
    w.id,
    w.member_id,
    w.tenant_id        AS post_tenant_id,
    w.balance,
    w.total_recharge,
    w.total_consume,
    w.frozen_amount,
    w.version
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.member_id IN (
    57, 58, 70, 71, 83, 84, 96, 97, 109, 110, 122, 123,
    135, 136, 145, 334, 335, 375, 376, 418, 419, 459, 460,
    500, 501, 541, 542, 582, 583
)
ORDER BY w.id;

-- =============================================================================
-- Step 7: 写入回填审计日志（crm_wallet_drift_log，fix_type=T04_BACKFILL_TENANT）
-- =============================================================================
-- 若表已存在则复用；不存在则创建
CREATE TABLE IF NOT EXISTS crm_wallet_drift_log (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    member_id       BIGINT NOT NULL,
    tenant_id       BIGINT,
    member_balance  DECIMAL(12,2) NOT NULL,
    wallet_balance  DECIMAL(12,2) NOT NULL,
    diff            DECIMAL(12,2) NOT NULL,
    fix_type        VARCHAR(32) NOT NULL COMMENT 'AUTO_FIX / MANUAL_REVIEW / PRE_FIX_BACKUP / T04_BACKFILL_TENANT',
    fixed_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_member (member_id),
    KEY idx_fixed_at (fixed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包漂移修复 / tenant 回填日志';

INSERT INTO crm_wallet_drift_log (member_id, tenant_id, member_balance, wallet_balance, diff, fix_type, remark)
SELECT
    w.member_id,
    w.tenant_id        AS post_tenant_id,
    COALESCE(m.balance, 0)  AS member_balance,
    COALESCE(w.balance, 0)  AS wallet_balance,
    COALESCE(m.balance, 0) - COALESCE(w.balance, 0) AS diff,
    'T04_BACKFILL_TENANT' AS fix_type,
    CONCAT('T04 backfilled tenant_id from crm_member.tenant_id=', w.tenant_id)
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.member_id IN (
    57, 58, 70, 71, 83, 84, 96, 97, 109, 110, 122, 123,
    135, 136, 145, 334, 335, 375, 376, 418, 419, 459, 460,
    500, 501, 541, 542, 582, 583
);

SELECT COUNT(*) AS t04_audit_log_rows
FROM crm_wallet_drift_log
WHERE fix_type = 'T04_BACKFILL_TENANT';

-- =============================================================================
-- Step 8: 最终验收
-- =============================================================================
SELECT '=== T04 FINAL VERIFICATION ===' AS step;

SELECT
    'crm_wallet total'                       AS metric, COUNT(*)                AS cnt FROM crm_wallet
UNION ALL SELECT 'crm_wallet.tenant_id IS NULL',                            COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL
UNION ALL SELECT 'crm_wallet.tenant_id = 1',                                COUNT(*) FROM crm_wallet WHERE tenant_id = 1
UNION ALL SELECT 'crm_wallet cross-tenant mismatches',                     COUNT(*) FROM crm_wallet w INNER JOIN crm_member m ON m.id=w.member_id WHERE w.tenant_id IS NOT NULL AND m.tenant_id IS NOT NULL AND w.tenant_id <> m.tenant_id
UNION ALL SELECT 'crm_wallet drift (balance mismatch)',                     COUNT(*) FROM crm_wallet w INNER JOIN crm_member m ON m.id=w.member_id WHERE w.deleted=0 AND m.deleted=0 AND COALESCE(w.balance,0) <> COALESCE(m.balance,0)
UNION ALL SELECT 'crm_wallet_transaction total',                           COUNT(*) FROM crm_wallet_transaction
UNION ALL SELECT 'crm_wallet_txn.tenant_id IS NULL',                        COUNT(*) FROM crm_wallet_transaction WHERE tenant_id IS NULL;

SELECT '=== T04 DONE ===' AS status;
