package com.meiyetec.dcep.offline;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * DCEP-04：离线交易双花检测算法（UTXO 模型）
 *
 * <p>数字人民币离线交易基于双层运营架构：
 * <ul>
 *   <li>中国人民银行 → 商业银行（运营机构）→ 用户</li>
 *   <li>离线交易发生在用户与商户终端之间，无需联网</li>
 *   <li>联机同步时由运营机构做最终清算与双花检测</li>
 * </ul>
 *
 * <p>本实现提供<u>链下预校验</u>，在交易同步到运营机构前先做一次本地双花检测，
 * 避免明显的双花/超额支付打到央行清算。
 *
 * <h3>UTXO 模型</h3>
 * <ul>
 *   <li>每个钱包的余额 = 未花费 UTXO 金额之和</li>
 *   <li>每笔交易 = 一组 input UTXO + 一组 output UTXO</li>
 *   <li>双花检测 = 同一 input UTXO 被 2+ 笔未确认交易引用</li>
 *   <li>超额检测 = sum(input) - sum(output) &lt; 0（即花费超过消费金额）</li>
 *   <li>透支检测 = sum(input) &gt; 钱包余额</li>
 *   <li>循环检测 = 交易图存在环（A → B → A）</li>
 * </ul>
 *
 * <h3>状态枚举</h3>
 * <pre>
 *   VALID              无双花
 *   DOUBLE_SPEND       同一 UTXO 被 ≥2 笔 pending 交易引用
 *   OVERSPEND          sum(input) &gt; sum(output)，即凭空创造币
 *   OVERDRAFT          sum(input) &gt; 钱包余额
 *   CYCLE_DETECTED     交易图存在循环（A → B → C → A）
 *   INVALID_SIGNATURE  签名验证失败（off-chain 预校验不在本类职责）
 * </pre>
 */
@Slf4j
public class DoubleSpendDetector {

    /**
     * 离线 UTXO（不可变的 input/output 单元）
     */
    public static class Utxo {
        private final String utxoId;          // 全局唯一 UTXO ID（txHash:index 派生）
        private final Long walletId;          // 所属钱包
        private final BigDecimal amount;
        private final String txNo;            // 创建该 UTXO 的交易号
        private final int outputIndex;        // 在所属交易中的输出索引
        private final LocalDateTime createdAt;

        public Utxo(String utxoId, Long walletId, BigDecimal amount, String txNo, int outputIndex) {
            this.utxoId = Objects.requireNonNull(utxoId, "utxoId 不能为空");
            this.walletId = Objects.requireNonNull(walletId, "walletId 不能为空");
            this.amount = Objects.requireNonNull(amount, "amount 不能为空");
            this.txNo = txNo;
            this.outputIndex = outputIndex;
            this.createdAt = LocalDateTime.now();
        }

        public String getUtxoId() { return utxoId; }
        public Long getWalletId() { return walletId; }
        public BigDecimal getAmount() { return amount; }
        public String getTxNo() { return txNo; }
        public int getOutputIndex() { return outputIndex; }
        public LocalDateTime getCreatedAt() { return createdAt; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Utxo)) return false;
            Utxo u = (Utxo) o;
            return Objects.equals(utxoId, u.utxoId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(utxoId);
        }
    }

    /**
     * 离线交易
     */
    public static class OfflineTx {
        private final String txNo;
        private final Long walletId;
        private final List<String> inputUtxoIds;   // 消费的 UTXO
        private final List<Utxo> outputs;          // 新创建的 UTXO
        private final String status;                // pending/synced/failed/disputed

        public OfflineTx(String txNo, Long walletId,
                         List<String> inputUtxoIds, List<Utxo> outputs, String status) {
            this.txNo = txNo;
            this.walletId = walletId;
            this.inputUtxoIds = inputUtxoIds == null ? new ArrayList<>() : new ArrayList<>(inputUtxoIds);
            this.outputs = outputs == null ? new ArrayList<>() : new ArrayList<>(outputs);
            this.status = status;
        }

        public String getTxNo() { return txNo; }
        public Long getWalletId() { return walletId; }
        public List<String> getInputUtxoIds() { return inputUtxoIds; }
        public List<Utxo> getOutputs() { return outputs; }
        public String getStatus() { return status; }

        public BigDecimal getInputTotal() {
            return inputUtxoIds.isEmpty() ? BigDecimal.ZERO : null; // 由外部查询
        }
    }

    /**
     * 检测状态
     */
    public enum ConflictStatus {
        VALID,
        DOUBLE_SPEND,
        OVERSPEND,
        OVERDRAFT,
        CYCLE_DETECTED
    }

    /**
     * 检测结果
     */
    public static class ConflictReport {
        public final ConflictStatus status;
        public final String txNo;
        public final List<String> conflictingTxNos;  // 与之冲突的其他交易号
        public final String detail;

        public ConflictReport(ConflictStatus status, String txNo,
                              List<String> conflictingTxNos, String detail) {
            this.status = status;
            this.txNo = txNo;
            this.conflictingTxNos = conflictingTxNos;
            this.detail = detail;
        }

        public boolean isClean() {
            return status == ConflictStatus.VALID;
        }

        @Override
        public String toString() {
            return "ConflictReport{txNo=" + txNo
                    + ", status=" + status
                    + ", conflicting=" + conflictingTxNos
                    + ", detail='" + detail + "'}";
        }
    }

    /**
     * 检测单个交易是否冲突
     *
     * @param candidate      待检测的离线交易
     * @param allPendingTxs  同一钱包下所有 pending 状态的交易（含 candidate）
     * @param walletBalance  钱包当前已确认余额（含已同步的 UTXO 之和）
     * @param utxoLookup     UTXO 查询函数：输入 utxoId → Utxo（null 表示 UTXO 不存在）
     */
    public ConflictReport detect(OfflineTx candidate,
                                  Collection<OfflineTx> allPendingTxs,
                                  BigDecimal walletBalance,
                                  java.util.function.Function<String, Utxo> utxoLookup) {
        if (candidate == null) {
            return new ConflictReport(ConflictStatus.VALID, "null", List.of(), "candidate 为空");
        }

        // 1. 双花检测：相同 inputUtxo 被 ≥2 笔交易引用
        Map<String, List<String>> utxoToTxNos = new HashMap<>();
        for (OfflineTx tx : allPendingTxs) {
            for (String utxoId : tx.getInputUtxoIds()) {
                utxoToTxNos.computeIfAbsent(utxoId, k -> new ArrayList<>()).add(tx.getTxNo());
            }
        }
        for (String inputId : candidate.getInputUtxoIds()) {
            List<String> txs = utxoToTxNos.getOrDefault(inputId, List.of());
            if (txs.size() > 1) {
                List<String> others = new ArrayList<>(txs);
                others.remove(candidate.getTxNo());
                return new ConflictReport(ConflictStatus.DOUBLE_SPEND, candidate.getTxNo(), others,
                        "UTXO " + inputId + " 被 " + txs.size() + " 笔交易引用（含 candidate）");
            }
        }

        // 2. 超额检测：sum(outputs) > sum(inputs) 即凭空创造币
        BigDecimal inputSum = BigDecimal.ZERO;
        for (String inputId : candidate.getInputUtxoIds()) {
            Utxo u = utxoLookup.apply(inputId);
            if (u == null) continue;
            inputSum = inputSum.add(u.getAmount());
        }
        BigDecimal outputSum = BigDecimal.ZERO;
        for (Utxo out : candidate.getOutputs()) {
            outputSum = outputSum.add(out.getAmount());
        }
        if (outputSum.compareTo(inputSum) > 0) {
            return new ConflictReport(ConflictStatus.OVERSPEND, candidate.getTxNo(), List.of(),
                    "outputs=" + outputSum + " > inputs=" + inputSum);
        }

        // 3. 透支检测：sum(inputs) > walletBalance
        if (inputSum.compareTo(walletBalance) > 0) {
            return new ConflictReport(ConflictStatus.OVERDRAFT, candidate.getTxNo(), List.of(),
                    "inputs=" + inputSum + " > balance=" + walletBalance);
        }

        // 4. 循环检测：交易图是否有环（DFS）
        // 构造：txNo → set of next txNo（通过 inputUtxoId 反查 prev txNo）
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, String> utxoToProducer = new HashMap<>();
        for (OfflineTx tx : allPendingTxs) {
            for (Utxo out : tx.getOutputs()) {
                utxoToProducer.put(out.getUtxoId(), tx.getTxNo());
            }
        }
        for (OfflineTx tx : allPendingTxs) {
            Set<String> nexts = new HashSet<>();
            for (String inputId : tx.getInputUtxoIds()) {
                String producer = utxoToProducer.get(inputId);
                if (producer != null && !producer.equals(tx.getTxNo())) {
                    nexts.add(producer);
                }
            }
            graph.put(tx.getTxNo(), nexts);
        }
        List<String> cycle = findCycle(graph, candidate.getTxNo());
        if (cycle != null) {
            return new ConflictReport(ConflictStatus.CYCLE_DETECTED, candidate.getTxNo(), cycle,
                    "交易图存在环：" + String.join(" → ", cycle));
        }

        return new ConflictReport(ConflictStatus.VALID, candidate.getTxNo(), List.of(), "无冲突");
    }

    /**
     * 批量检测：对一批交易逐个检测，输出冲突报告
     */
    public List<ConflictReport> detectBatch(List<OfflineTx> pendingTxs,
                                              BigDecimal walletBalance,
                                              java.util.function.Function<String, Utxo> utxoLookup) {
        List<ConflictReport> reports = new ArrayList<>();
        for (OfflineTx tx : pendingTxs) {
            reports.add(detect(tx, pendingTxs, walletBalance, utxoLookup));
        }
        return reports;
    }

    /**
     * DFS 查找环，返回环上的交易号列表
     */
    private List<String> findCycle(Map<String, Set<String>> graph, String start) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        List<String> stack = new ArrayList<>();

        if (dfs(graph, start, visited, inStack, stack)) {
            // 截取环部分
            int cycleStart = stack.indexOf(start);
            if (cycleStart >= 0) {
                return new ArrayList<>(stack.subList(cycleStart, stack.size()));
            }
        }
        return null;
    }

    private boolean dfs(Map<String, Set<String>> graph, String node,
                        Set<String> visited, Set<String> inStack, List<String> stack) {
        visited.add(node);
        inStack.add(node);
        stack.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(next)) {
                if (dfs(graph, next, visited, inStack, stack)) return true;
            } else if (inStack.contains(next)) {
                // 找到环
                stack.add(next);
                return true;
            }
        }
        inStack.remove(node);
        stack.remove(stack.size() - 1);
        return false;
    }
}
