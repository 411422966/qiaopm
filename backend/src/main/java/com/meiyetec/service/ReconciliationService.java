package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.PosPayment;
import com.meiyetec.entity.PosReconciliation;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.PosPaymentMapper;
import com.meiyetec.mapper.PosReconciliationMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R2.0-C T-C04 支付对账服务。
 *
 * <p>策略：把对账数据通道抽象为 {@link ChannelFetcher}，目前内置本地适配器（演示），实际项目
 * 接入微信/支付宝/银联时新增 fetcher 即可。跑对账后写入 pos_reconciliation，自动识别差异。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PosReconciliationMapper reconMapper;
    private final PosPaymentMapper paymentMapper;
    private final JdbcTemplate jdbc;

    public interface ChannelFetcher {
        String channel();
        List<Map<String, Object>> fetch(LocalDate date, Long tenantId);
    }

    /** 本地 fetcher：从 DB 模拟通道侧流水（仅用于演示与集成测试） */
    @RequiredArgsConstructor
    public static class LocalChannelFetcher implements ChannelFetcher {
        private final JdbcTemplate jdbc;
        private final String channelName;

        @Override
        public String channel() { return channelName; }

        @Override
        public List<Map<String, Object>> fetch(LocalDate date, Long tenantId) {
            // 演示用：从本地 pos_payment 拉取当日支付成功流水
            return jdbc.queryForList(
                    "SELECT id AS payment_id, payment_no, transaction_id, amount, method, order_id " +
                    "FROM pos_payment " +
                    "WHERE status = 1 AND DATE(pay_time) = ? AND method = ?",
                    date, channelName);
        }
    }

    public List<ChannelFetcher> defaultChannelFetchers() {
        return List.of(
                new LocalChannelFetcher(jdbc, "wechat"),
                new LocalChannelFetcher(jdbc, "alipay"),
                new LocalChannelFetcher(jdbc, "unionpay"),
                new LocalChannelFetcher(jdbc, "e_cny_soft"),
                new LocalChannelFetcher(jdbc, "e_cny_hard"),
                new LocalChannelFetcher(jdbc, "cash")
        );
    }

    /**
     * 跑一次对账，并把所有差异写入 pos_reconciliation。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> run(LocalDate date, Long tenantId, List<ChannelFetcher> fetchers) {
        if (tenantId == null) tenantId = TenantContextHolder.get().getTenantId();
        if (fetchers == null || fetchers.isEmpty()) fetchers = defaultChannelFetchers();

        // 清理本日已存在的 PENDING 差异（重跑）
        QueryWrapper<PosReconciliation> q = new QueryWrapper<>();
        q.eq("reconcile_date", date).eq("handle_status", "PENDING");
        if (tenantId != null) q.eq("tenant_id", tenantId);
        reconMapper.delete(q);

        int matched = 0, diff = 0;
        Map<String, Object> summary = new HashMap<>();

        for (ChannelFetcher fetcher : fetchers) {
            List<Map<String, Object>> channelRows = fetcher.fetch(date, tenantId);
            // 本地侧
            List<PosPayment> localPayments = paymentMapper.selectList(new LambdaQueryWrapper<PosPayment>()
                    .eq(PosPayment::getStatus, 1)
                    .apply("DATE(pay_time) = {0}", date)
                    .eq(PosPayment::getMethod, fetcher.channel()));
            Map<Long, PosPayment> localById = new HashMap<>();
            localPayments.forEach(p -> localById.put(p.getId(), p));

            Map<String, Map<String, Object>> channelByTxn = new HashMap<>();
            for (Map<String, Object> row : channelRows) {
                String txnId = (String) row.get("transaction_id");
                if (txnId != null) channelByTxn.put(txnId, row);
            }

            // 匹配本地 payment
            Set<String> matchedTxn = new HashSet<>();
            for (PosPayment payment : localPayments) {
                if (payment.getTransactionId() == null) {
                    // 本地没记录通道流水，差额定位不到通道
                    saveDiff(tenantId, date, fetcher.channel(), null, payment.getId(),
                            null, payment.getAmount(), payment.getAmount(),
                            "MISSING_CHANNEL", null);
                    diff++;
                    continue;
                }
                Map<String, Object> channelRow = channelByTxn.get(payment.getTransactionId());
                if (channelRow == null) {
                    saveDiff(tenantId, date, fetcher.channel(), payment.getTransactionId(),
                            payment.getId(), null, payment.getAmount(), payment.getAmount(),
                            "DIFF_LOCAL_MORE", "本地有通道无");
                    diff++;
                    continue;
                }
                BigDecimal channelAmount = toBigDecimal(channelRow.get("amount"));
                if (channelAmount.compareTo(payment.getAmount()) != 0) {
                    saveDiff(tenantId, date, fetcher.channel(), payment.getTransactionId(),
                            payment.getId(), channelAmount, payment.getAmount(),
                            channelAmount.subtract(payment.getAmount()),
                            "DIFF_AMOUNT", "通道金额=" + channelAmount + " 本地=" + payment.getAmount());
                    diff++;
                } else {
                    saveMatched(tenantId, date, fetcher.channel(), payment.getTransactionId(),
                            payment.getId(), payment.getAmount());
                    matched++;
                }
                matchedTxn.add(payment.getTransactionId());
            }
            // 通道多出的
            for (Map.Entry<String, Map<String, Object>> e : channelByTxn.entrySet()) {
                if (matchedTxn.contains(e.getKey())) continue;
                BigDecimal amount = toBigDecimal(e.getValue().get("amount"));
                saveDiff(tenantId, date, fetcher.channel(), e.getKey(), null, amount,
                        null, amount, "DIFF_CHANNEL_MORE", "通道有本地无");
                diff++;
            }
        }

        summary.put("date", date);
        summary.put("matched", matched);
        summary.put("diff", diff);
        summary.put("total", matched + diff);
        return summary;
    }

    private void saveMatched(Long tenantId, LocalDate date, String channel, String txnId,
                             Long paymentId, BigDecimal amount) {
        PosReconciliation r = new PosReconciliation();
        r.setTenantId(tenantId);
        r.setReconcileDate(date);
        r.setChannel(channel);
        r.setChannelTxnId(txnId);
        r.setPaymentId(paymentId);
        r.setChannelAmount(amount);
        r.setLocalAmount(amount);
        r.setDiffAmount(BigDecimal.ZERO);
        r.setStatus("MATCHED");
        r.setHandleStatus("PENDING");
        reconMapper.insert(r);
    }

    private void saveDiff(Long tenantId, LocalDate date, String channel, String txnId,
                          Long paymentId, BigDecimal channelAmount, BigDecimal localAmount,
                          BigDecimal diffAmount, String status, String note) {
        PosReconciliation r = new PosReconciliation();
        r.setTenantId(tenantId);
        r.setReconcileDate(date);
        r.setChannel(channel);
        r.setChannelTxnId(txnId);
        r.setPaymentId(paymentId);
        r.setChannelAmount(channelAmount);
        r.setLocalAmount(localAmount);
        r.setDiffAmount(diffAmount);
        r.setStatus(status);
        r.setHandleStatus("PENDING");
        r.setHandleNote(note);
        reconMapper.insert(r);
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    public PageResult<PosReconciliation> page(int pageNum, int pageSize, String status,
                                              String handleStatus, String dateFrom, String dateTo) {
        QueryWrapper<PosReconciliation> q = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) q.eq("status", status);
        if (handleStatus != null && !handleStatus.isEmpty()) q.eq("handle_status", handleStatus);
        if (dateFrom != null && !dateFrom.isEmpty()) q.ge("reconcile_date", dateFrom);
        if (dateTo != null && !dateTo.isEmpty()) q.le("reconcile_date", dateTo);
        q.orderByDesc("id");
        Page<PosReconciliation> p = reconMapper.selectPage(new Page<>(pageNum, pageSize), q);
        return PageResult.of(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public PosReconciliation handle(Long id, String action, String note, Long handleBy, String handleByName) {
        PosReconciliation r = reconMapper.selectById(id);
        if (r == null) throw new BusinessException(BizCode.NOT_FOUND, "对账单不存在");
        if ("HANDLED".equals(r.getHandleStatus())) {
            throw new BusinessException(BizCode.CONFLICT, "已处理的对账单不可重复处理");
        }
        r.setHandleStatus("HANDLED".equals(action) ? "HANDLED" : "IGNORED");
        r.setHandleNote(note);
        r.setHandleBy(handleBy);
        r.setHandleByName(handleByName);
        r.setHandleTime(LocalDateTime.now());
        reconMapper.updateById(r);
        return r;
    }
}
