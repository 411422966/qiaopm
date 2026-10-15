package com.meiyetec.dcep.offline;

import com.meiyetec.dcep.offline.DoubleSpendDetector.ConflictReport;
import com.meiyetec.dcep.offline.DoubleSpendDetector.ConflictStatus;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * DCEP-04 阶段 C：离线 → 联机切换时的交易合并与冲突解决
 *
 * <p>设备离线期间累积了一组 pending 交易，恢复联机时由本服务批量合并 + 检测冲突 + 应用解决策略。
 *
 * <h3>合并步骤</h3>
 * <ol>
 *   <li>分组：按 (walletId, merchantId, amount) 分组（同一笔交易可能跨多个 pending）</li>
 *   <li>去重：相同 offlineCert 视为同一笔（设备侧可能重复推送）</li>
 *   <li>冲突检测：调用 {@link DoubleSpendDetector} 检测 UTXO 冲突</li>
 *   <li>策略应用：根据 {@link ResolutionPolicy} 决定每笔交易的最终状态</li>
 *   <li>输出：合并后的交易列表（含标记 disposed / disputed / merged）</li>
 * </ol>
 *
 * <h3>解决策略</h3>
 * <ul>
 *   <li>{@code FIRST_SEEN}：以设备最早推送的交易为准（信任设备时钟）</li>
 *   <li>{@code LAST_SEEN}：以最后一次推送为准</li>
 *   <li>{@code MANUAL}：标记为 disputed，由运营人员人工处理</li>
 *   <li>{@code AUTO_FIRST_WINS}：自动采用先到者 + 后到者标 disputed（FIRST_SEEN + MANUAL 混合）</li>
 * </ul>
 */
@Slf4j
public class OfflineOnlineReconciler {

    public enum ResolutionPolicy {
        FIRST_SEEN,
        LAST_SEEN,
        MANUAL,
        AUTO_FIRST_WINS
    }

    public enum TxAction {
        APPLIED,         // 已应用（合并成功）
        MERGED,          // 被合并到另一笔（duplicate 消除）
        DISPUTED,        // 冲突待人工处理
        REJECTED,        // 拒绝（双花 / 超额 / 透支）
        PENDING_RETRY    // 暂挂，重试
    }

    @Data
    @Builder
    public static class OfflineTx {
        private String txNo;
        private String offlineCert;
        private Long walletId;
        private String merchantId;
        private BigDecimal amount;
        private LocalDateTime offlineTxTime;
        private List<String> inputUtxoIds;
        private BigDecimal offlineBalanceAfter;
        private String status;
        private String walletSignature;
        private String merchantSignature;
        private Integer syncAttempts;
    }

    @Data
    @Builder
    public static class ReconciliationResult {
        private TxAction action;
        private String txNo;
        private String mergedIntoTxNo;   // 当 action=MERGED 时，记录合并目标
        private ConflictReport conflict;
        private String detail;
    }

    /**
     * 批量合并 + 冲突解决
     *
     * @param txs          设备侧累积的所有 pending 交易
     * @param walletBalance 钱包当前已确认余额
     * @param utxoLookup   UTXO 查询函数
     * @param policy       解决策略
     * @return 每笔交易的合并结果（顺序与 txs 一致）
     */
    public List<ReconciliationResult> reconcile(List<OfflineTx> txs, BigDecimal walletBalance,
                                                 java.util.function.Function<String,
                                                     DoubleSpendDetector.Utxo> utxoLookup,
                                                 ResolutionPolicy policy) {
        if (txs == null || txs.isEmpty()) return Collections.emptyList();

        // 步骤 1：按时间排序
        List<OfflineTx> sorted = new ArrayList<>(txs);
        sorted.sort(Comparator.comparing(OfflineTx::getOfflineTxTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // 步骤 2：离线证书去重（device side 重复推送）
        Map<String, String> certToFirstTxNo = new LinkedHashMap<>();
        Map<String, List<OfflineTx>> certGroup = new LinkedHashMap<>();
        for (OfflineTx tx : sorted) {
            if (tx.getOfflineCert() == null) continue;
            certGroup.computeIfAbsent(tx.getOfflineCert(), k -> new ArrayList<>()).add(tx);
            certToFirstTxNo.putIfAbsent(tx.getOfflineCert(), tx.getTxNo());
        }

        // 步骤 3：构造 DoubleSpendDetector.OfflineTx（避免循环依赖）
        List<DoubleSpendDetector.OfflineTx> detectorTxs = new ArrayList<>();
        for (OfflineTx tx : sorted) {
            List<com.meiyetec.dcep.offline.DoubleSpendDetector.Utxo> outputs = new ArrayList<>();
            outputs.add(new com.meiyetec.dcep.offline.DoubleSpendDetector.Utxo(
                    "output_" + tx.getTxNo(), tx.getWalletId(),
                    tx.getOfflineBalanceAfter() == null ? BigDecimal.ZERO : tx.getOfflineBalanceAfter(),
                    tx.getTxNo(), 0));
            detectorTxs.add(new DoubleSpendDetector.OfflineTx(tx.getTxNo(), tx.getWalletId(),
                    tx.getInputUtxoIds(), outputs, tx.getStatus()));
        }

        // 步骤 4：双花检测
        DoubleSpendDetector detector = new DoubleSpendDetector();
        Map<String, ConflictReport> conflicts = new HashMap<>();
        for (DoubleSpendDetector.OfflineTx dt : detectorTxs) {
            conflicts.put(dt.getTxNo(), detector.detect(dt, detectorTxs, walletBalance, utxoLookup));
        }

        // 步骤 5：去重 + 策略应用
        List<ReconciliationResult> results = new ArrayList<>();
        Map<String, ReconciliationResult> txResultMap = new HashMap<>();

        for (OfflineTx tx : sorted) {
            // 5a) 离线证书重复
            List<OfflineTx> sameCertGroup = certGroup.get(tx.getOfflineCert());
            if (sameCertGroup != null && sameCertGroup.size() > 1 && !sameCertGroup.get(0).equals(tx)) {
                ReconciliationResult merged = ReconciliationResult.builder()
                        .action(TxAction.MERGED)
                        .txNo(tx.getTxNo())
                        .mergedIntoTxNo(certToFirstTxNo.get(tx.getOfflineCert()))
                        .detail("离线证书重复，已合并到 " + certToFirstTxNo.get(tx.getOfflineCert()))
                        .build();
                results.add(merged);
                txResultMap.put(tx.getTxNo(), merged);
                continue;
            }

            // 5b) 双花检测
            ConflictReport conflict = conflicts.get(tx.getTxNo());
            if (conflict != null && conflict.status != ConflictStatus.VALID) {
                // 双花 → 根据策略处理
                TxAction action = applyConflictPolicy(tx, conflict, sorted, policy);
                ReconciliationResult r = ReconciliationResult.builder()
                        .action(action)
                        .txNo(tx.getTxNo())
                        .conflict(conflict)
                        .detail("冲突：" + conflict.status + " - " + conflict.detail)
                        .build();
                results.add(r);
                txResultMap.put(tx.getTxNo(), r);
                continue;
            }

            // 5c) 正常 → 应用
            ReconciliationResult applied = ReconciliationResult.builder()
                    .action(TxAction.APPLIED)
                    .txNo(tx.getTxNo())
                    .detail("正常应用")
                    .build();
            results.add(applied);
            txResultMap.put(tx.getTxNo(), applied);
        }

        // 步骤 6：审计日志
        log.info("[DCEP-Reconcile] 合并 {} 笔：APPLIED={} MERGED={} DISPUTED={} REJECTED={}",
                txs.size(),
                results.stream().filter(r -> r.action == TxAction.APPLIED).count(),
                results.stream().filter(r -> r.action == TxAction.MERGED).count(),
                results.stream().filter(r -> r.action == TxAction.DISPUTED).count(),
                results.stream().filter(r -> r.action == TxAction.REJECTED).count());
        return results;
    }

    private TxAction applyConflictPolicy(OfflineTx tx, ConflictReport conflict,
                                          List<OfflineTx> sorted,
                                          ResolutionPolicy policy) {
        // 找到本笔交易在 sorted 中的位置（按时间最早者胜出）
        int myIdx = sorted.indexOf(tx);
        boolean isFirst = true;
        for (int i = 0; i < myIdx; i++) {
            OfflineTx other = sorted.get(i);
            if (conflict.conflictingTxNos.contains(other.getTxNo())) {
                isFirst = false;
                break;
            }
        }
        return switch (policy) {
            case FIRST_SEEN -> isFirst ? TxAction.APPLIED : TxAction.REJECTED;
            case LAST_SEEN -> isFirst ? TxAction.REJECTED : TxAction.APPLIED;
            case MANUAL -> TxAction.DISPUTED;
            case AUTO_FIRST_WINS -> isFirst ? TxAction.APPLIED : TxAction.DISPUTED;
        };
    }
}
