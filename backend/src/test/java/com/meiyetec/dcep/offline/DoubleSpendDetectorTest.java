package com.meiyetec.dcep.offline;

import com.meiyetec.dcep.offline.DoubleSpendDetector.ConflictReport;
import com.meiyetec.dcep.offline.DoubleSpendDetector.ConflictStatus;
import com.meiyetec.dcep.offline.DoubleSpendDetector.OfflineTx;
import com.meiyetec.dcep.offline.DoubleSpendDetector.Utxo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-04 双花检测算法单元测试
 */
@DisplayName("DCEP-04 双花检测 UTXO 模型")
class DoubleSpendDetectorTest {

    private final DoubleSpendDetector detector = new DoubleSpendDetector();

    /** UTXO 索引 */
    private Map<String, Utxo> utxoMap;
    private final java.util.function.Function<String, Utxo> lookup = id -> utxoMap.get(id);

    private Utxo utxo(String id, Long wallet, String amount) {
        return new Utxo(id, wallet, new BigDecimal(amount), "tx_" + id, 0);
    }

    private OfflineTx tx(String txNo, Long wallet, List<String> inputs, List<Utxo> outputs, String status) {
        return new OfflineTx(txNo, wallet, inputs, outputs, status);
    }

    // ============================================================
    // 1. 无冲突场景
    // ============================================================

    @Test
    @DisplayName("VALID：单笔消费，无冲突")
    void valid_singleTx() {
        Utxo u1 = utxo("u1", 1L, "1000");
        utxoMap = new HashMap<>();
        utxoMap.put("u1", u1);

        OfflineTx t = tx("tx1", 1L, List.of("u1"), List.of(utxo("u1_change", 1L, "900")), "pending");
        ConflictReport r = detector.detect(t, List.of(t), new BigDecimal("1000"), lookup);
        assertEquals(ConflictStatus.VALID, r.status, r.detail);
    }

    @Test
    @DisplayName("VALID：链式交易 A → B → C，无环无双花")
    void valid_chain() {
        utxoMap = new HashMap<>();
        utxoMap.put("uA", utxo("uA", 1L, "1000"));
        utxoMap.put("uB", utxo("uB", 1L, "600"));
        utxoMap.put("uC", utxo("uC", 1L, "400"));

        OfflineTx tA = tx("txA", 1L, List.of("uA"),
                List.of(utxo("uB", 1L, "600"), utxo("uB_change", 1L, "400")), "pending");
        OfflineTx tB = tx("txB", 1L, List.of("uB"),
                List.of(utxo("uC", 1L, "400"), utxo("uC_change", 1L, "200")), "pending");
        OfflineTx tC = tx("txC", 1L, List.of("uC"),
                List.of(utxo("uC_consumed", 1L, "400")), "pending");

        List<OfflineTx> all = List.of(tA, tB, tC);
        for (OfflineTx t : all) {
            ConflictReport r = detector.detect(t, all, new BigDecimal("1000"), lookup);
            assertEquals(ConflictStatus.VALID, r.status,
                    "TX=" + t.getTxNo() + " 应无冲突，实际=" + r);
        }
    }

    // ============================================================
    // 2. DOUBLE_SPEND
    // ============================================================

    @Test
    @DisplayName("DOUBLE_SPEND：同一 UTXO 被 2 笔交易引用")
    void doubleSpend_sameUtxo() {
        utxoMap = new HashMap<>();
        utxoMap.put("u_shared", utxo("u_shared", 1L, "500"));

        OfflineTx t1 = tx("tx1", 1L, List.of("u_shared"), List.of(utxo("u1_out", 1L, "500")), "pending");
        OfflineTx t2 = tx("tx2", 1L, List.of("u_shared"), List.of(utxo("u2_out", 1L, "500")), "pending");

        List<OfflineTx> all = List.of(t1, t2);
        ConflictReport r1 = detector.detect(t1, all, new BigDecimal("500"), lookup);
        ConflictReport r2 = detector.detect(t2, all, new BigDecimal("500"), lookup);
        assertEquals(ConflictStatus.DOUBLE_SPEND, r1.status);
        assertEquals(ConflictStatus.DOUBLE_SPEND, r2.status);
        assertTrue(r1.detail.contains("u_shared"));
        assertTrue(r2.detail.contains("u_shared"));
    }

    // ============================================================
    // 3. OVERSPEND
    // ============================================================

    @Test
    @DisplayName("OVERSPEND：output > input 即凭空造币")
    void overspend_outputsExceedInputs() {
        utxoMap = new HashMap<>();
        utxoMap.put("u1", utxo("u1", 1L, "100"));

        OfflineTx t = tx("tx1", 1L, List.of("u1"),
                List.of(utxo("u1_out", 1L, "500")), "pending");
        ConflictReport r = detector.detect(t, List.of(t), new BigDecimal("100"), lookup);
        assertEquals(ConflictStatus.OVERSPEND, r.status);
        assertTrue(r.detail.contains("outputs=500"));
        assertTrue(r.detail.contains("inputs=100"));
    }

    // ============================================================
    // 4. OVERDRAFT
    // ============================================================

    @Test
    @DisplayName("OVERDRAFT：sum(inputs) > walletBalance")
    void overdraft_inputsExceedBalance() {
        utxoMap = new HashMap<>();
        utxoMap.put("u1", utxo("u1", 1L, "600"));

        OfflineTx t = tx("tx1", 1L, List.of("u1"),
                List.of(utxo("u1_out", 1L, "500"), utxo("u1_change", 1L, "100")), "pending");
        ConflictReport r = detector.detect(t, List.of(t), new BigDecimal("500"),  // 余额只有 500
                lookup);
        assertEquals(ConflictStatus.OVERDRAFT, r.status);
        assertTrue(r.detail.contains("balance=500"));
    }

    // ============================================================
    // 5. CYCLE_DETECTED
    // ============================================================

    @Test
    @DisplayName("CYCLE_DETECTED：交易图存在环 A → B → A")
    void cycle_AB_A() {
        utxoMap = new HashMap<>();
        utxoMap.put("uA", utxo("uA", 1L, "100"));
        utxoMap.put("uB", utxo("uB", 1L, "100"));

        // tx1 消费 uA，产出 uB
        // tx2 消费 uB，产出 uA（成环）
        OfflineTx t1 = tx("tx1", 1L, List.of("uA"),
                List.of(utxo("uB", 1L, "100")), "pending");
        OfflineTx t2 = tx("tx2", 1L, List.of("uB"),
                List.of(utxo("uA", 1L, "100")), "pending");

        List<OfflineTx> all = List.of(t1, t2);
        ConflictReport r1 = detector.detect(t1, all, new BigDecimal("100"), lookup);
        ConflictReport r2 = detector.detect(t2, all, new BigDecimal("100"), lookup);
        // 至少一个应报 CYCLE_DETECTED（DFS 起点不同，结果可能不同）
        boolean anyCycle = r1.status == ConflictStatus.CYCLE_DETECTED
                || r2.status == ConflictStatus.CYCLE_DETECTED;
        assertTrue(anyCycle, "A→B→A 环至少应被一笔交易检测到");
    }

    @Test
    @DisplayName("CYCLE_DETECTED：3 节点环 A → B → C → A")
    void cycle_ABC_A() {
        utxoMap = new HashMap<>();
        utxoMap.put("uA", utxo("uA", 1L, "100"));
        utxoMap.put("uB", utxo("uB", 1L, "100"));
        utxoMap.put("uC", utxo("uC", 1L, "100"));

        OfflineTx tA = tx("txA", 1L, List.of("uA"), List.of(utxo("uB", 1L, "100")), "pending");
        OfflineTx tB = tx("txB", 1L, List.of("uB"), List.of(utxo("uC", 1L, "100")), "pending");
        OfflineTx tC = tx("txC", 1L, List.of("uC"), List.of(utxo("uA", 1L, "100")), "pending");

        List<OfflineTx> all = List.of(tA, tB, tC);
        boolean anyCycle = all.stream().anyMatch(t ->
                detector.detect(t, all, new BigDecimal("100"), lookup).status
                        == ConflictStatus.CYCLE_DETECTED);
        assertTrue(anyCycle);
    }

    // ============================================================
    // 6. 批量检测
    // ============================================================

    @Test
    @DisplayName("detectBatch：返回每笔交易的报告，顺序一致")
    void detectBatch() {
        utxoMap = new HashMap<>();
        utxoMap.put("u1", utxo("u1", 1L, "1000"));
        utxoMap.put("u_shared", utxo("u_shared", 1L, "500"));

        OfflineTx t1 = tx("tx1", 1L, List.of("u1"),
                List.of(utxo("u_shared", 1L, "500"), utxo("u1_change", 1L, "500")), "pending");
        OfflineTx t2 = tx("tx2", 1L, List.of("u_shared"),
                List.of(utxo("u2_out", 1L, "500")), "pending");
        OfflineTx t3 = tx("tx3", 1L, List.of("u_shared"),  // 双花
                List.of(utxo("u3_out", 1L, "500")), "pending");

        List<ConflictReport> reports = detector.detectBatch(
                List.of(t1, t2, t3), new BigDecimal("1000"), lookup);
        assertEquals(3, reports.size());
        assertEquals("tx1", reports.get(0).txNo);
        assertEquals("tx2", reports.get(1).txNo);
        assertEquals("tx3", reports.get(2).txNo);
        // tx3 应报 DOUBLE_SPEND
        assertEquals(ConflictStatus.DOUBLE_SPEND, reports.get(2).status);
    }

    // ============================================================
    // 7. 边界
    // ============================================================

    @Test
    @DisplayName("candidate=null：返回 VALID 空报告")
    void nullCandidate() {
        ConflictReport r = detector.detect(null, new ArrayList<>(), BigDecimal.ZERO, lookup);
        assertEquals(ConflictStatus.VALID, r.status);
    }

    @Test
    @DisplayName("candidate 无 input：视为空操作，VALID")
    void emptyInputs() {
        OfflineTx t = tx("tx1", 1L, List.of(), List.of(), "pending");
        ConflictReport r = detector.detect(t, List.of(t), BigDecimal.ZERO, lookup);
        assertEquals(ConflictStatus.VALID, r.status);
    }

    @Test
    @DisplayName("ConflictReport.toString 输出包含 txNo 和 status")
    void reportToString() {
        ConflictReport r = new ConflictReport(
                ConflictStatus.DOUBLE_SPEND, "tx1",
                List.of("tx2"), "shared u1");
        String s = r.toString();
        assertTrue(s.contains("tx1"));
        assertTrue(s.contains("DOUBLE_SPEND"));
    }

    @Test
    @DisplayName("ConflictReport.isClean() 仅 VALID 返回 true")
    void reportIsClean() {
        ConflictReport valid = new ConflictReport(ConflictStatus.VALID, "t", List.of(), "");
        ConflictReport conflict = new ConflictReport(ConflictStatus.DOUBLE_SPEND, "t", List.of(), "");
        assertTrue(valid.isClean());
        assertFalse(conflict.isClean());
    }
}
