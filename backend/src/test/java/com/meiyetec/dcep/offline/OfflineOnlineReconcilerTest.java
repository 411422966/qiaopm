package com.meiyetec.dcep.offline;

import com.meiyetec.dcep.offline.DoubleSpendDetector.Utxo;
import com.meiyetec.dcep.offline.OfflineOnlineReconciler.OfflineTx;
import com.meiyetec.dcep.offline.OfflineOnlineReconciler.ReconciliationResult;
import com.meiyetec.dcep.offline.OfflineOnlineReconciler.ResolutionPolicy;
import com.meiyetec.dcep.offline.OfflineOnlineReconciler.TxAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DCEP-04 stage-C OfflineOnlineReconciler")
class OfflineOnlineReconcilerTest {

    private final OfflineOnlineReconciler reconciler = new OfflineOnlineReconciler();

    private Map<String, Utxo> utxoMap;
    private final java.util.function.Function<String, Utxo> lookup = id -> utxoMap.get(id);

    private OfflineTx tx(String txNo, String cert, Long wallet, String merchant, String amount,
                          LocalDateTime time, List<String> inputs, String balanceAfter) {
        return OfflineTx.builder()
                .txNo(txNo)
                .offlineCert(cert)
                .walletId(wallet)
                .merchantId(merchant)
                .amount(new BigDecimal(amount))
                .offlineTxTime(time)
                .inputUtxoIds(inputs)
                .offlineBalanceAfter(new BigDecimal(balanceAfter))
                .status("pending")
                .walletSignature("ws-" + txNo)
                .merchantSignature("ms-" + txNo)
                .syncAttempts(0)
                .build();
    }

    @Test
    @DisplayName("MERGED: identical offlineCert merged into first tx")
    void merged_sameOfflineCert() {
        utxoMap = new HashMap<>();
        utxoMap.put("u1", new Utxo("u1", 1L, new BigDecimal("1000"), "origin", 0));

        LocalDateTime t1 = LocalDateTime.of(2026, 9, 4, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 4, 10, 1);

        OfflineTx tx1 = tx("tx1", "cert-A", 1L, "M001", "100", t1, List.of("u1"), "900");
        OfflineTx tx2 = tx("tx2", "cert-A", 1L, "M001", "100", t2, List.of("u1"), "900");

        List<ReconciliationResult> results = reconciler.reconcile(
                List.of(tx1, tx2), new BigDecimal("1000"), lookup,
                ResolutionPolicy.FIRST_SEEN);

        assertEquals(TxAction.APPLIED, results.get(0).getAction());
        assertEquals(TxAction.MERGED, results.get(1).getAction());
        assertEquals("tx1", results.get(1).getMergedIntoTxNo());
    }

    @Test
    @DisplayName("DOUBLE_SPEND + FIRST_SEEN: first APPLIED, second REJECTED")
    void firstSeen_doubleSpend() {
        utxoMap = new HashMap<>();
        utxoMap.put("u_shared", new Utxo("u_shared", 1L, new BigDecimal("500"), "origin", 0));

        LocalDateTime t1 = LocalDateTime.of(2026, 9, 4, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 4, 10, 5);

        OfflineTx tx1 = tx("tx1", "cert-1", 1L, "M001", "300", t1, List.of("u_shared"), "200");
        OfflineTx tx2 = tx("tx2", "cert-2", 1L, "M002", "300", t2, List.of("u_shared"), "200");

        List<ReconciliationResult> results = reconciler.reconcile(
                List.of(tx1, tx2), new BigDecimal("500"), lookup, ResolutionPolicy.FIRST_SEEN);
        assertEquals(TxAction.APPLIED, results.get(0).getAction());
        assertEquals(TxAction.REJECTED, results.get(1).getAction());
    }

    @Test
    @DisplayName("DOUBLE_SPEND + LAST_SEEN: first REJECTED, second APPLIED")
    void lastSeen_doubleSpend() {
        utxoMap = new HashMap<>();
        utxoMap.put("u_shared", new Utxo("u_shared", 1L, new BigDecimal("500"), "origin", 0));

        LocalDateTime t1 = LocalDateTime.of(2026, 9, 4, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 4, 10, 5);

        OfflineTx tx1 = tx("tx1", "cert-1", 1L, "M001", "300", t1, List.of("u_shared"), "200");
        OfflineTx tx2 = tx("tx2", "cert-2", 1L, "M002", "300", t2, List.of("u_shared"), "200");

        List<ReconciliationResult> results = reconciler.reconcile(
                List.of(tx1, tx2), new BigDecimal("500"), lookup, ResolutionPolicy.LAST_SEEN);
        assertEquals(TxAction.REJECTED, results.get(0).getAction());
        assertEquals(TxAction.APPLIED, results.get(1).getAction());
    }

    @Test
    @DisplayName("DOUBLE_SPEND + AUTO_FIRST_WINS: first APPLIED, second DISPUTED")
    void autoFirstWins_doubleSpend() {
        utxoMap = new HashMap<>();
        utxoMap.put("u_shared", new Utxo("u_shared", 1L, new BigDecimal("500"), "origin", 0));

        LocalDateTime t1 = LocalDateTime.of(2026, 9, 4, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 4, 10, 5);

        OfflineTx tx1 = tx("tx1", "cert-1", 1L, "M001", "300", t1, List.of("u_shared"), "200");
        OfflineTx tx2 = tx("tx2", "cert-2", 1L, "M002", "300", t2, List.of("u_shared"), "200");

        List<ReconciliationResult> results = reconciler.reconcile(
                List.of(tx1, tx2), new BigDecimal("500"), lookup, ResolutionPolicy.AUTO_FIRST_WINS);
        assertEquals(TxAction.APPLIED, results.get(0).getAction());
        assertEquals(TxAction.DISPUTED, results.get(1).getAction());
    }

    @Test
    @DisplayName("No conflict: 3 independent txs all APPLIED")
    void allApplied_noConflict() {
        utxoMap = new HashMap<>();
        utxoMap.put("uA", new Utxo("uA", 1L, new BigDecimal("1000"), "origin", 0));
        utxoMap.put("uB", new Utxo("uB", 1L, new BigDecimal("500"), "tx1", 0));
        utxoMap.put("uC", new Utxo("uC", 1L, new BigDecimal("300"), "tx2", 0));

        LocalDateTime t1 = LocalDateTime.of(2026, 9, 4, 10, 0);
        OfflineTx tx1 = tx("tx1", "cert-1", 1L, "M001", "500", t1, List.of("uA"), "500");
        OfflineTx tx2 = tx("tx2", "cert-2", 1L, "M002", "200", t1.plusMinutes(1), List.of("uB"), "300");
        OfflineTx tx3 = tx("tx3", "cert-3", 1L, "M003", "100", t1.plusMinutes(2), List.of("uC"), "200");

        List<ReconciliationResult> results = reconciler.reconcile(
                List.of(tx1, tx2, tx3), new BigDecimal("1000"), lookup, ResolutionPolicy.FIRST_SEEN);
        assertEquals(3, results.size());
        for (ReconciliationResult r : results) {
            assertEquals(TxAction.APPLIED, r.getAction());
        }
    }

    @Test
    @DisplayName("Empty input returns empty list")
    void empty() {
        List<ReconciliationResult> results = reconciler.reconcile(
                new ArrayList<>(), BigDecimal.ZERO, lookup, ResolutionPolicy.FIRST_SEEN);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Null input returns empty list (no exception)")
    void nullInput() {
        List<ReconciliationResult> results = reconciler.reconcile(
                null, BigDecimal.ZERO, lookup, ResolutionPolicy.FIRST_SEEN);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Mixed scenarios: 1 normal + 2 double-spend + 1 duplicate cert")
    void mixedScenarios() {
        utxoMap = new HashMap<>();
        utxoMap.put("uA", new Utxo("uA", 1L, new BigDecimal("1000"), "origin", 0));
        utxoMap.put("uB", new Utxo("uB", 1L, new BigDecimal("500"), "tx2", 0));
        utxoMap.put("u_shared", new Utxo("u_shared", 1L, new BigDecimal("500"), "origin", 0));

        LocalDateTime base = LocalDateTime.of(2026, 9, 4, 10, 0);
        OfflineTx tx1 = tx("tx1", "cert-1", 1L, "M001", "100", base, List.of("uA"), "900");
        OfflineTx tx2 = tx("tx2", "cert-2", 1L, "M002", "200", base.plusMinutes(1), List.of("uB"), "300");
        OfflineTx tx3 = tx("tx3", "cert-3", 1L, "M003", "300", base.plusMinutes(2), List.of("u_shared"), "200");
        OfflineTx tx4 = tx("tx4", "cert-4", 1L, "M004", "300", base.plusMinutes(3), List.of("u_shared"), "200");
        OfflineTx tx5 = tx("tx5", "cert-1", 1L, "M001", "100", base.plusMinutes(4), List.of("uA"), "900");

        List<ReconciliationResult> results = reconciler.reconcile(
                List.of(tx1, tx2, tx3, tx4, tx5), new BigDecimal("1500"), lookup,
                ResolutionPolicy.FIRST_SEEN);
        assertEquals(5, results.size());
        assertEquals(TxAction.APPLIED, results.get(0).getAction());
        assertEquals(TxAction.APPLIED, results.get(1).getAction());
        assertEquals(TxAction.APPLIED, results.get(2).getAction());
        assertEquals(TxAction.REJECTED, results.get(3).getAction());
        assertEquals(TxAction.MERGED, results.get(4).getAction());
    }

    @Test
    @DisplayName("ResolutionPolicy.values() contains all 4 policies")
    void policyCount() {
        assertEquals(4, ResolutionPolicy.values().length);
        assertNotNull(ResolutionPolicy.valueOf("FIRST_SEEN"));
        assertNotNull(ResolutionPolicy.valueOf("LAST_SEEN"));
        assertNotNull(ResolutionPolicy.valueOf("MANUAL"));
        assertNotNull(ResolutionPolicy.valueOf("AUTO_FIRST_WINS"));
    }

    @Test
    @DisplayName("TxAction.values() contains 5 actions")
    void txActionCount() {
        assertEquals(5, TxAction.values().length);
        assertNotNull(TxAction.valueOf("APPLIED"));
        assertNotNull(TxAction.valueOf("MERGED"));
        assertNotNull(TxAction.valueOf("DISPUTED"));
        assertNotNull(TxAction.valueOf("REJECTED"));
        assertNotNull(TxAction.valueOf("PENDING_RETRY"));
    }
}
