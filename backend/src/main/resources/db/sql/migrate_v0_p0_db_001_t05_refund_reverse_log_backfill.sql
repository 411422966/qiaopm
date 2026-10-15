-- =============================================================================
-- P0-DB-001-T05 · pos_refund_reverse_log.ref_id 历史数据回填
-- =============================================================================
-- 目的：
--   对 691 条历史 reverse log 中有 100% 完整证据链的记录回填 ref_id。
--
-- D3 决策（严格遵守）：
--   PACKAGE：仅回填 9 条存在完整、唯一、可验证证据链的记录。
--            映射：pos_refund → pos_order → pos_order_item.package_order_id → crm_package_order.id
--            10 条孤儿（package_order_id=15~24 在 crm_package_order 中不存在）保持 NULL。
--   POINTS：  不接受语义降级（拒绝以 market_point_record.id 代替原始 ref_id），保持 NULL。
--   STOCK：   原始 stock_record.type=4 无可匹配记录，保持 NULL。
--   COMMISSION：无实际 commission_record 对应，保持 NULL。
--
-- 约束（不可违背）：
--   ① 不猜测孤儿 package_order_id；② 不创建伪造 crm_package_order；
--   ③ 不修改退款金额/订单/套餐次数/积分/库存/提成等业务数据；
--   ④ 不删除任何历史 reverse_log。
--
-- 验收标准：
--   - 9 条 PACKAGE reverse log ref_id 从 NULL → crm_package_order.id（6~14）
--   - 10 条 PACKAGE reverse log ref_id 保持 NULL（孤儿）
--   - POINTS 224 条：ref_id 保持 NULL
--   - STOCK 224 条：ref_id 保持 NULL
--   - COMMISSION 224 条：ref_id 保持 NULL
--   - 总 ref_id IS NULL：691 - 9 = 682
--
-- 兼容性：MySQL 8.0+
-- =============================================================================

USE meiyetec;

-- =============================================================================
-- Step 1: PRE-FIX 快照（不写数据）
-- =============================================================================
SELECT '=== T05 PRE-FIX: ref_id 分布 ===' AS step;

SELECT
    CASE
        WHEN ref_id IS NOT NULL  THEN 'NOT_NULL'
        WHEN reverse_type = 'PACKAGE' AND ref_id IS NULL THEN 'PACKAGE_NULL'
        WHEN reverse_type = 'POINTS'   AND ref_id IS NULL THEN 'POINTS_NULL'
        WHEN reverse_type = 'STOCK'    AND ref_id IS NULL THEN 'STOCK_NULL'
        WHEN reverse_type = 'COMMISSION' AND ref_id IS NULL THEN 'COMMISSION_NULL'
        ELSE 'OTHER'
    END AS bucket,
    COUNT(*) AS cnt
FROM pos_refund_reverse_log
GROUP BY bucket
ORDER BY bucket;

SELECT '=== T05 PRE-FIX: 9 条待回填 PACKAGE reverse log ===' AS step;
SELECT
    rl.id AS rl_id,
    rl.refund_id,
    rl.reverse_type,
    rl.ref_id         AS pre_ref_id,
    r.order_id,
    oi.package_order_id  AS candidate_ref_id,
    cpo.id               AS validated_cpo_id,
    CASE WHEN cpo.id = oi.package_order_id THEN 'READY' ELSE 'ORPHAN' END AS mapping_status,
    rl.payload,
    cpo.order_no        AS pkg_order_no,
    cpo.member_id
FROM pos_refund_reverse_log rl
INNER JOIN pos_refund r ON r.id = rl.refund_id
INNER JOIN pos_order o ON o.id = r.order_id
INNER JOIN pos_order_item oi ON oi.order_id = o.id AND oi.package_order_id IS NOT NULL
INNER JOIN crm_package_order cpo ON cpo.id = oi.package_order_id
WHERE rl.reverse_type = 'PACKAGE'
ORDER BY rl.id;

SELECT '=== T05 PRE-FIX: 10 条 PACKAGE 孤儿（package_order_id 在 crm_package_order 中不存在，保持 NULL）===' AS step;
SELECT
    rl.id AS rl_id,
    rl.refund_id,
    rl.reverse_type,
    rl.ref_id         AS pre_ref_id,
    r.order_id,
    oi.package_order_id  AS orphan_package_order_id,
    rl.payload
FROM pos_refund_reverse_log rl
INNER JOIN pos_refund r ON r.id = rl.refund_id
INNER JOIN pos_order o ON o.id = r.order_id
INNER JOIN pos_order_item oi ON oi.order_id = o.id AND oi.package_order_id IS NOT NULL
LEFT JOIN crm_package_order cpo ON cpo.id = oi.package_order_id
WHERE rl.reverse_type = 'PACKAGE'
  AND cpo.id IS NULL
ORDER BY rl.id;

SELECT '=== T05 PRE-FIX: 其他 reverse_type 确认 100% 为 NULL ===' AS step;
SELECT reverse_type, COUNT(*) AS total, SUM(CASE WHEN ref_id IS NOT NULL THEN 1 ELSE 0 END) AS not_null
FROM pos_refund_reverse_log
WHERE reverse_type IN ('POINTS', 'STOCK', 'COMMISSION')
GROUP BY reverse_type;

-- =============================================================================
-- Step 2: BACKFILL — 仅回填 9 条有完整证据链的 PACKAGE reverse log
-- 安全条件：cpo.id IS NOT NULL（仅当 crm_package_order 记录存在时才回填）
-- =============================================================================
START TRANSACTION;

UPDATE pos_refund_reverse_log rl
INNER JOIN pos_refund r ON r.id = rl.refund_id
INNER JOIN pos_order o ON o.id = r.order_id
INNER JOIN pos_order_item oi ON oi.order_id = o.id AND oi.package_order_id IS NOT NULL
INNER JOIN crm_package_order cpo ON cpo.id = oi.package_order_id
SET rl.ref_id = cpo.id
WHERE rl.reverse_type = 'PACKAGE'
  AND rl.ref_id IS NULL
  AND cpo.id IS NOT NULL;

SELECT ROW_COUNT() AS backfilled_rows;

COMMIT;

-- =============================================================================
-- Step 3: POST-FIX 验证
-- =============================================================================
SELECT '=== T05 POST-FIX: ref_id 分布 ===' AS step;

SELECT
    CASE
        WHEN ref_id IS NOT NULL  THEN 'NOT_NULL'
        WHEN reverse_type = 'PACKAGE' AND ref_id IS NULL THEN 'PACKAGE_NULL'
        WHEN reverse_type = 'POINTS'   AND ref_id IS NULL THEN 'POINTS_NULL'
        WHEN reverse_type = 'STOCK'    AND ref_id IS NULL THEN 'STOCK_NULL'
        WHEN reverse_type = 'COMMISSION' AND ref_id IS NULL THEN 'COMMISSION_NULL'
        ELSE 'OTHER'
    END AS bucket,
    COUNT(*) AS cnt
FROM pos_refund_reverse_log
GROUP BY bucket
ORDER BY bucket;

SELECT '=== T05 POST-FIX: 9 条已回填确认 ===' AS step;
SELECT
    rl.id AS rl_id,
    rl.refund_id,
    rl.reverse_type,
    rl.ref_id          AS post_ref_id,
    rl.payload,
    r.order_id,
    oi.package_order_id  AS order_item_pkg_id,
    cpo.order_no        AS pkg_order_no
FROM pos_refund_reverse_log rl
INNER JOIN pos_refund r ON r.id = rl.refund_id
INNER JOIN pos_order o ON o.id = r.order_id
INNER JOIN pos_order_item oi ON oi.order_id = o.id AND oi.package_order_id IS NOT NULL
INNER JOIN crm_package_order cpo ON cpo.id = oi.package_order_id
WHERE rl.reverse_type = 'PACKAGE'
  AND rl.ref_id IS NOT NULL
ORDER BY rl.id;

SELECT '=== T05 POST-FIX: 10 条 PACKAGE 孤儿确认仍为 NULL ===' AS step;
SELECT COUNT(*) AS orphan_still_null
FROM pos_refund_reverse_log rl
INNER JOIN pos_refund r ON r.id = rl.refund_id
INNER JOIN pos_order o ON o.id = r.order_id
INNER JOIN pos_order_item oi ON oi.order_id = o.id AND oi.package_order_id IS NOT NULL
LEFT JOIN crm_package_order cpo ON cpo.id = oi.package_order_id
WHERE rl.reverse_type = 'PACKAGE'
  AND cpo.id IS NULL
  AND rl.ref_id IS NULL;

-- =============================================================================
-- Step 4: 业务数据不变性验证
-- =============================================================================
SELECT '=== T05 INVARIANT: 退款金额不变 ===' AS step;
SELECT 'pos_refund.refund_amount total' AS metric, SUM(refund_amount) AS val FROM pos_refund;
SELECT 'pos_refund total rows' AS metric, COUNT(*) AS val FROM pos_refund;

SELECT '=== T05 INVARIANT: crm_package_order.remaining_times 不变 ===' AS step;
SELECT id, order_no, remaining_times, total_times, status, update_time
FROM crm_package_order
WHERE id BETWEEN 6 AND 14
ORDER BY id;

SELECT '=== T05 INVARIANT: pos_refund_reverse_log 总行数不变 ===' AS step;
SELECT COUNT(*) AS total_rows,
       SUM(CASE WHEN ref_id IS NOT NULL THEN 1 ELSE 0 END) AS ref_id_filled,
       SUM(CASE WHEN ref_id IS NULL THEN 1 ELSE 0 END)    AS ref_id_null
FROM pos_refund_reverse_log;

-- =============================================================================
-- Step 5: 审计日志（写入 crm_wallet_drift_log，与 T04 共用同一表）
-- =============================================================================
INSERT INTO crm_wallet_drift_log (member_id, tenant_id, member_balance, wallet_balance, diff, fix_type, remark)
SELECT
    COALESCE(r.member_id, 0)   AS member_id,
    COALESCE(rl.tenant_id, 0)  AS tenant_id,
    0.00  AS member_balance,
    0.00  AS wallet_balance,
    0.00  AS diff,
    'T05_BACKFILL_REVERSE_LOG'  AS fix_type,
    CONCAT('T05 PACKAGE ref_id backfill: rl.id=', rl.id,
           ', refund_id=', rl.refund_id,
           ', ref_id=', cpo.id,
           ', pkg=', cpo.order_no)
FROM pos_refund_reverse_log rl
INNER JOIN pos_refund r ON r.id = rl.refund_id
INNER JOIN pos_order o ON o.id = r.order_id
INNER JOIN pos_order_item oi ON oi.order_id = o.id AND oi.package_order_id IS NOT NULL
INNER JOIN crm_package_order cpo ON cpo.id = oi.package_order_id
WHERE rl.reverse_type = 'PACKAGE'
  AND rl.ref_id IS NOT NULL;

SELECT COUNT(*) AS t05_audit_rows
FROM crm_wallet_drift_log
WHERE fix_type = 'T05_BACKFILL_REVERSE_LOG';

-- =============================================================================
-- Step 6: 最终验收
-- =============================================================================
SELECT '=== T05 FINAL VERIFICATION ===' AS step;

SELECT
    'pos_refund_reverse_log total'                       AS metric, COUNT(*) AS val FROM pos_refund_reverse_log
UNION ALL SELECT 'ref_id NOT NULL (expect 9)',                    COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NOT NULL
UNION ALL SELECT 'ref_id IS NULL (expect 682)',                   COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NULL
UNION ALL SELECT 'PACKAGE ref_id NOT NULL (expect 9)',           COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='PACKAGE' AND ref_id IS NOT NULL
UNION ALL SELECT 'PACKAGE ref_id IS NULL (expect 10)',           COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='PACKAGE' AND ref_id IS NULL
UNION ALL SELECT 'POINTS ref_id IS NULL (expect 224)',           COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='POINTS'
UNION ALL SELECT 'STOCK ref_id IS NULL (expect 224)',            COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='STOCK'
UNION ALL SELECT 'COMMISSION ref_id IS NULL (expect 224)',      COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type='COMMISSION'
UNION ALL SELECT 'crm_package_order total (expect 14)',           COUNT(*) FROM crm_package_order
UNION ALL SELECT 'pos_refund total rows',                         COUNT(*) FROM pos_refund;

SELECT '=== T05 DONE ===' AS status;
