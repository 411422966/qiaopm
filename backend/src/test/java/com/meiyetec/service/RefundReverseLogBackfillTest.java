package com.meiyetec.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-DB-001-T05 · pos_refund_reverse_log.ref_id 历史数据回填验证
 *
 * <p>覆盖：
 * <ol>
 *   <li>9 条有完整证据链的 PACKAGE reverse log ref_id 已从 NULL → crm_package_order.id</li>
 *   <li>10 条孤儿 PACKAGE reverse log ref_id 保持 NULL（不猜测）</li>
 *   <li>POINTS 224 条 ref_id 保持 NULL（不接受语义降级）</li>
 *   <li>STOCK 224 条 ref_id 保持 NULL</li>
 *   <li>COMMISSION 224 条 ref_id 保持 NULL</li>
 *   <li>pos_refund_reverse_log 总行数不变（691）</li>
 *   <li>crm_package_order.remaining_times 未被修改（业务不变性）</li>
 *   <li>pos_refund 总行数未变（业务不变性）</li>
 *   <li>pos_refund.total_refund_amount 未变（业务不变性）</li>
 *   <li>审计日志写入 crm_wallet_drift_log（fix_type=T05_BACKFILL_REVERSE_LOG）</li>
 * </ol>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefundReverseLogBackfillTest {

    @Autowired private JdbcTemplate jdbc;

    // 9 条有完整证据链的 reverse_log_id → 期望 ref_id（crm_package_order.id）
    private static final List<Long> T05_FILLED_RL_IDS = List.of(26L, 42L, 61L, 77L, 93L, 109L, 125L, 141L, 157L);
    private static final List<Long> T05_FILLED_REF_IDS  = List.of(6L,  7L,  8L,  9L,  10L, 11L,  12L,  13L,  14L);

    // 10 条孤儿 reverse_log_id（package_order_id 在 crm_package_order 中不存在，保持 NULL）
    private static final List<Long> T05_ORPHAN_RL_IDS = List.of(173L, 189L, 205L, 221L, 237L, 253L, 269L, 285L, 301L, 317L);

    @Test
    @Order(1)
    @DisplayName("T05-1: 9 条强证据链 PACKAGE reverse log ref_id 已回填（非 NULL）")
    void filledPackageRefIds() {
        for (int i = 0; i < T05_FILLED_RL_IDS.size(); i++) {
            Long rlId = T05_FILLED_RL_IDS.get(i);
            Long expectedRefId = T05_FILLED_REF_IDS.get(i);
            Long actualRefId = jdbc.queryForObject(
                "SELECT ref_id FROM pos_refund_reverse_log WHERE id = ?", Long.class, rlId);
            assertNotNull(actualRefId,
                "T05 rl_id=" + rlId + " ref_id 应不为 NULL");
            assertEquals(expectedRefId, actualRefId,
                "T05 rl_id=" + rlId + " ref_id 应 == crm_package_order.id=" + expectedRefId);
        }
    }

    @Test
    @Order(2)
    @DisplayName("T05-2: 10 条孤儿 PACKAGE reverse log ref_id 保持 NULL（不猜测）")
    void orphanPackageRefIdsStillNull() {
        for (Long rlId : T05_ORPHAN_RL_IDS) {
            Long refId = jdbc.queryForObject(
                "SELECT ref_id FROM pos_refund_reverse_log WHERE id = ?", Long.class, rlId);
            assertNull(refId,
                "T05 孤儿 rl_id=" + rlId + " ref_id 应保持 NULL（不猜测），实际=" + refId);
        }
        Integer orphanStillNull = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log " +
            "WHERE id IN (" + placeholders(T05_ORPHAN_RL_IDS.size()) + ") AND ref_id IS NULL",
            Integer.class, T05_ORPHAN_RL_IDS.toArray());
        assertEquals(10, orphanStillNull.intValue(),
            "10 条孤儿应全部保持 NULL");
    }

    @Test
    @Order(3)
    @DisplayName("T05-3: POINTS 224 条 ref_id 全部保持 NULL（不接受语义降级）")
    void pointsRefIdsNull() {
        Integer nullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type = 'POINTS'",
            Integer.class);
        Integer notNullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type = 'POINTS' AND ref_id IS NOT NULL",
            Integer.class);
        assertEquals(224, nullCount.intValue(),
            "POINTS reverse_log 应有 224 行，实际=" + nullCount);
        assertEquals(0, notNullCount.intValue(),
            "POINTS reverse_log ref_id 应全部为 NULL（不接受降级映射），实际非 NULL=" + notNullCount);
    }

    @Test
    @Order(4)
    @DisplayName("T05-4: STOCK 224 条 ref_id 全部保持 NULL")
    void stockRefIdsNull() {
        Integer nullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type = 'STOCK'",
            Integer.class);
        Integer notNullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type = 'STOCK' AND ref_id IS NOT NULL",
            Integer.class);
        assertEquals(224, nullCount.intValue(),
            "STOCK reverse_log 应有 224 行");
        assertEquals(0, notNullCount.intValue(),
            "STOCK reverse_log ref_id 应全部为 NULL");
    }

    @Test
    @Order(5)
    @DisplayName("T05-5: COMMISSION 224 条 ref_id 全部保持 NULL")
    void commissionRefIdsNull() {
        Integer nullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type = 'COMMISSION'",
            Integer.class);
        Integer notNullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE reverse_type = 'COMMISSION' AND ref_id IS NOT NULL",
            Integer.class);
        assertEquals(224, nullCount.intValue(),
            "COMMISSION reverse_log 应有 224 行");
        assertEquals(0, notNullCount.intValue(),
            "COMMISSION reverse_log ref_id 应全部为 NULL");
    }

    @Test
    @Order(6)
    @DisplayName("T05-6: 全表总行数未变（691 行）")
    void totalRowCountUnchanged() {
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log", Integer.class);
        assertEquals(691, total.intValue(),
            "reverse_log 总行数应为 691（T05 不删除、不新增），实际=" + total);
    }

    @Test
    @Order(7)
    @DisplayName("T05-7: 全表 ref_id 分布：9 非 NULL / 682 NULL")
    void overallRefIdDistribution() {
        Integer notNull = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NOT NULL",
            Integer.class);
        Integer isNull = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NULL",
            Integer.class);
        assertEquals(9, notNull.intValue(),
            "ref_id 非 NULL 应为 9，实际=" + notNull);
        assertEquals(682, isNull.intValue(),
            "ref_id IS NULL 应为 682（691-9），实际=" + isNull);
        assertEquals(691, notNull + isNull,
            "notNull + null 应 = 691");
    }

    @Test
    @Order(8)
    @DisplayName("T05-8: 业务不变性 — crm_package_order.remaining_times 未被修改")
    void packageRemainingTimesUnchanged() {
        // 预填充值（来自 pre-fix 快照）：id=6~14 的 remaining_times=2
        Integer notTwo = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_package_order " +
            "WHERE id BETWEEN 6 AND 14 AND remaining_times != 2",
            Integer.class);
        assertEquals(0, notTwo.intValue(),
            "crm_package_order.id=6~14 的 remaining_times 应保持 2（T05 不修改业务数据），异常行数=" + notTwo);
    }

    @Test
    @Order(9)
    @DisplayName("T05-9: 业务不变性 — pos_refund 总行数未变（270 行）")
    void refundRowCountUnchanged() {
        Integer refundCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_refund", Integer.class);
        assertEquals(270, refundCount.intValue(),
            "pos_refund 总行数应为 270，实际=" + refundCount);
    }

    @Test
    @Order(10)
    @DisplayName("T05-10: 业务不变性 — 退款金额总额未变（108000.00）")
    void refundAmountUnchanged() {
        BigDecimal total = jdbc.queryForObject(
            "SELECT SUM(refund_amount) FROM pos_refund", BigDecimal.class);
        assertEquals(0, new BigDecimal("108000.00").compareTo(total),
            "pos_refund.refund_amount 总和应为 108000.00（T05 不修改业务数据），实际=" + total);
    }

    @Test
    @Order(11)
    @DisplayName("T05-11: 审计日志写入 crm_wallet_drift_log（fix_type=T05_BACKFILL_REVERSE_LOG）")
    void auditLogWritten() {
        Integer auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_drift_log WHERE fix_type = 'T05_BACKFILL_REVERSE_LOG'",
            Integer.class);
        assertEquals(9, auditCount.intValue(),
            "T05 审计日志应有 9 行（每条回填一条），实际=" + auditCount);
    }

    @Test
    @Order(12)
    @DisplayName("T05-12: 审计日志 remark 包含 rl_id / refund_id / ref_id / pkg 信息")
    void auditLogContentCorrect() {
        Integer hasValidRemark = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_drift_log " +
            "WHERE fix_type = 'T05_BACKFILL_REVERSE_LOG' " +
            "  AND remark LIKE '%T05%' " +
            "  AND remark LIKE '%refund_id=%' " +
            "  AND remark LIKE '%ref_id=%'",
            Integer.class);
        assertEquals(9, hasValidRemark.intValue(),
            "9 条审计日志应包含 rl_id/refund_id/ref_id 标识，实际=" + hasValidRemark);
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }
}
