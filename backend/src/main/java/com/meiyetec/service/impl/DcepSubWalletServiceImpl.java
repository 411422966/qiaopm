package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DcepSubWallet;
import com.meiyetec.entity.DcepSubWalletLedger;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.DcepSubWalletLedgerMapper;
import com.meiyetec.mapper.DcepSubWalletMapper;
import com.meiyetec.service.DcepSubWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class DcepSubWalletServiceImpl extends ServiceImpl<DcepSubWalletMapper, DcepSubWallet>
        implements DcepSubWalletService {

    private final DcepSubWalletMapper subWalletMapper;
    private final DcepSubWalletLedgerMapper ledgerMapper;

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public List<DcepSubWallet> queryList(Long tenantId, Long storeId, Long parentWalletId,
                                          String walletType, String status) {
        return subWalletMapper.queryList(tenantId, storeId, parentWalletId, walletType, status);
    }

    @Override
    public DcepSubWallet findByCode(String code) {
        return subWalletMapper.findByCode(code);
    }

    @Override
    public Map<String, Object> getStats(Long id) {
        DcepSubWallet w = mustGet(id);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("id", w.getId());
        stats.put("subWalletCode", w.getSubWalletCode());
        stats.put("subWalletName", w.getSubWalletName());
        stats.put("walletType", w.getWalletType());
        stats.put("balance", w.getBalance());
        stats.put("totalIn", w.getTotalIn());
        stats.put("totalOut", w.getTotalOut());
        stats.put("frozenBalance", w.getFrozenBalance());
        stats.put("availableBalance", w.getBalance().subtract(w.getFrozenBalance()));
        stats.put("maxBalance", w.getMaxBalance());
        stats.put("status", w.getStatus());

        // 本月累计转入/转出
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        BigDecimal monthIn = ledgerMapper.sumAmount(id, "transfer_in", monthStart, monthEnd);
        BigDecimal monthOut = ledgerMapper.sumAmount(id, "transfer_out", monthStart, monthEnd);
        stats.put("monthIn", monthIn == null ? BigDecimal.ZERO : monthIn);
        stats.put("monthOut", monthOut == null ? BigDecimal.ZERO : monthOut);
        return stats;
    }

    // ============================================================
    // 创建
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepSubWallet createSubWallet(Long tenantId, Long storeId, Long parentWalletId,
                                          String parentWalletType, String subWalletName, String walletType,
                                          BigDecimal maxBalance, BigDecimal minBalance,
                                          BigDecimal transferInRatio, BigDecimal transferOutRatio,
                                          Integer autoSweepEnabled, BigDecimal sweepThreshold,
                                          Long sweepTargetId, String settlementMode, String tags) {
        if (!isValidWalletType(walletType)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "不支持的子钱包类型: " + walletType);
        }
        if (maxBalance != null && minBalance != null && maxBalance.compareTo(minBalance) < 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "maxBalance 必须 >= minBalance");
        }
        String code = "SW-" + walletType.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DcepSubWallet w = new DcepSubWallet();
        w.setTenantId(tenantId);
        w.setStoreId(storeId);
        w.setParentWalletId(parentWalletId);
        w.setParentWalletType(parentWalletType);
        w.setSubWalletCode(code);
        w.setSubWalletName(subWalletName);
        w.setWalletType(walletType);
        w.setBalance(BigDecimal.ZERO);
        w.setTotalIn(BigDecimal.ZERO);
        w.setTotalOut(BigDecimal.ZERO);
        w.setFrozenBalance(BigDecimal.ZERO);
        w.setMaxBalance(maxBalance);
        w.setMinBalance(minBalance == null ? BigDecimal.ZERO : minBalance);
        w.setTransferInRatio(transferInRatio);
        w.setTransferOutRatio(transferOutRatio);
        w.setAutoSweepEnabled(autoSweepEnabled == null ? 0 : autoSweepEnabled);
        w.setSweepThreshold(sweepThreshold);
        w.setSweepTargetId(sweepTargetId);
        w.setSettlementMode(settlementMode);
        w.setStatus("active");
        w.setPriority(0);
        w.setTags(tags);
        w.setActivatedTime(LocalDateTime.now());
        subWalletMapper.insert(w);
        log.info("Sub wallet created: code={} type={} name={} parent={}",
                code, walletType, subWalletName, parentWalletId);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepSubWallet updateConfig(Long id, BigDecimal maxBalance, BigDecimal minBalance,
                                      BigDecimal transferInRatio, BigDecimal transferOutRatio,
                                      Integer autoSweepEnabled, BigDecimal sweepThreshold,
                                      Long sweepTargetId, String settlementMode, String tags, String remark) {
        DcepSubWallet w = mustGet(id);
        if ("closed".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "已关闭的子钱包不可修改");
        }
        if (maxBalance != null && minBalance != null && maxBalance.compareTo(minBalance) < 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "maxBalance 必须 >= minBalance");
        }
        if (maxBalance != null) w.setMaxBalance(maxBalance);
        if (minBalance != null) w.setMinBalance(minBalance);
        if (transferInRatio != null) w.setTransferInRatio(transferInRatio);
        if (transferOutRatio != null) w.setTransferOutRatio(transferOutRatio);
        if (autoSweepEnabled != null) w.setAutoSweepEnabled(autoSweepEnabled);
        if (sweepThreshold != null) w.setSweepThreshold(sweepThreshold);
        if (sweepTargetId != null) w.setSweepTargetId(sweepTargetId);
        if (settlementMode != null) w.setSettlementMode(settlementMode);
        if (tags != null) w.setTags(tags);
        if (remark != null) w.setRemark(remark);
        subWalletMapper.updateById(w);
        return w;
    }

    // ============================================================
    // 生命周期
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepSubWallet freeze(Long id, String reason) {
        DcepSubWallet w = mustGet(id);
        if (!"active".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 active 可冻结，当前: " + w.getStatus());
        }
        w.setStatus("frozen");
        w.setRemark(reason == null ? "冻结" : "冻结: " + reason);
        subWalletMapper.updateById(w);
        log.warn("Sub wallet frozen: id={} reason={}", id, reason);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepSubWallet unfreeze(Long id) {
        DcepSubWallet w = mustGet(id);
        if (!"frozen".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 frozen 可解冻，当前: " + w.getStatus());
        }
        w.setStatus("active");
        w.setRemark("解冻成功");
        subWalletMapper.updateById(w);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepSubWallet close(Long id) {
        DcepSubWallet w = mustGet(id);
        if (!"active".equals(w.getStatus()) && !"frozen".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 active/frozen 可关闭，当前: " + w.getStatus());
        }
        BigDecimal available = w.getBalance().subtract(w.getFrozenBalance());
        if (available.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(BizCode.FORBIDDEN,
                    "可用余额必须为 0，当前: " + available);
        }
        w.setStatus("closed");
        w.setClosedTime(LocalDateTime.now());
        w.setRemark("已关闭");
        subWalletMapper.updateById(w);
        log.info("Sub wallet closed: id={} code={}", id, w.getSubWalletCode());
        return w;
    }

    // ============================================================
    // 账间转账（复式记账）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, DcepSubWalletLedger> transfer(Long fromId, Long toId, BigDecimal amount,
                                                      BigDecimal fee, String orderNo,
                                                      Long operatorId, String operatorName, String remark) {
        if (fromId.equals(toId)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "转出和转入不能为同一子钱包");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "转账金额必须 > 0");
        }

        DcepSubWallet from = mustGet(fromId);
        DcepSubWallet to = mustGet(toId);

        if (!"active".equals(from.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "转出子钱包非 active: " + from.getStatus());
        }
        if (!"active".equals(to.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "转入子钱包非 active: " + to.getStatus());
        }

        BigDecimal available = from.getBalance().subtract(from.getFrozenBalance());
        BigDecimal feeAmt = fee == null ? BigDecimal.ZERO : fee;
        BigDecimal totalDebit = amount.add(feeAmt);
        if (available.compareTo(totalDebit) < 0) {
            throw new BusinessException(BizCode.FORBIDDEN,
                    "可用余额不足：需要 " + totalDebit + "，可用 " + available);
        }

        // 扣出账方
        int rows = subWalletMapper.debitBalance(fromId, totalDebit);
        if (rows == 0) {
            throw new BusinessException(BizCode.FORBIDDEN, "扣款失败（余额可能已变动）");
        }

        // 转入账方（扣除转出手续费后净入）
        BigDecimal netIn = amount.subtract(feeAmt);
        subWalletMapper.creditBalance(toId, netIn);

        // 双方账本流水
        DcepSubWallet fromAfter = mustGet(fromId);
        DcepSubWallet toAfter = mustGet(toId);

        DcepSubWalletLedger fromLedger = buildLedger(from, "transfer_out",
                amount, fromAfter.getBalance(), fromAfter.getBalance().add(totalDebit),
                feeAmt, toId, to.getSubWalletName(), orderNo, operatorId, operatorName, remark);
        DcepSubWalletLedger toLedger = buildLedger(to, "transfer_in",
                netIn, toAfter.getBalance().subtract(netIn), toAfter.getBalance(),
                BigDecimal.ZERO, fromId, from.getSubWalletName(), orderNo, operatorId, operatorName, remark);

        ledgerMapper.insertLedger(fromLedger);
        ledgerMapper.insertLedger(toLedger);

        log.info("Sub wallet transfer: {}→{} amount={} fee={} orderNo={}",
                from.getSubWalletCode(), to.getSubWalletCode(), amount, feeAmt, orderNo);

        Map<String, DcepSubWalletLedger> result = new LinkedHashMap<>();
        result.put("fromLedger", fromLedger);
        result.put("toLedger", toLedger);
        return result;
    }

    // ============================================================
    // 账本流水
    // ============================================================

    @Override
    public List<DcepSubWalletLedger> queryLedger(Long subWalletId, String txType,
                                                  String startTime, String endTime, int limit) {
        LocalDateTime start = null, end = null;
        if (startTime != null && !startTime.isEmpty()) {
            start = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (endTime != null && !endTime.isEmpty()) {
            end = LocalDateTime.parse(endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (limit <= 0 || limit > 500) limit = 100;
        return ledgerMapper.queryBySubWallet(subWalletId, txType, start, end, limit);
    }

    // ============================================================
    // 自动归集（定时任务调用）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoSweep(Long parentWalletId) {
        List<DcepSubWallet> wallets = subWalletMapper.queryList(
                null, null, parentWalletId, null, "active");
        int count = 0;
        for (DcepSubWallet w : wallets) {
            if (w.getAutoSweepEnabled() == null || w.getAutoSweepEnabled() != 1) continue;
            if (w.getSweepThreshold() == null || w.getSweepTargetId() == null) continue;
            BigDecimal available = w.getBalance().subtract(w.getFrozenBalance());
            if (available.compareTo(w.getSweepThreshold()) >= 0) {
                try {
                    // 归集超过阈值部分至目标子钱包
                    BigDecimal excess = available.subtract(w.getSweepThreshold());
                    // 保留 minBalance
                    if (w.getMinBalance() != null) {
                        excess = excess.max(BigDecimal.ZERO);
                    }
                    if (excess.compareTo(BigDecimal.ZERO) > 0) {
                        transfer(w.getId(), w.getSweepTargetId(), excess,
                                null, null, null, "SYSTEM", "自动归集");
                        count++;
                    }
                } catch (Exception e) {
                    log.error("自动归集失败: subWallet={}", w.getId(), e);
                }
            }
        }
        log.info("Auto sweep completed: parent={} count={}", parentWalletId, count);
        return count;
    }

    // ============================================================
    // 工具
    // ============================================================

    private DcepSubWallet mustGet(Long id) {
        DcepSubWallet w = subWalletMapper.selectById(id);
        if (w == null) throw new BusinessException(BizCode.NOT_FOUND, "子钱包不存在: " + id);
        return w;
    }

    private boolean isValidWalletType(String type) {
        return type != null && (type.equals("store_fund") || type.equals("product_fund")
                || type.equals("project_fund") || type.equals("brand_share") || type.equals("settlement"));
    }

    private String nextLedgerNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "SWL-" + date + "-" + String.format("%08d", new Random().nextInt(99999999));
    }

    private DcepSubWalletLedger buildLedger(DcepSubWallet wallet, String txType,
                                             BigDecimal amount, BigDecimal balanceBefore,
                                             BigDecimal balanceAfter, BigDecimal fee,
                                             Long oppositeId, String oppositeName,
                                             String orderNo, Long operatorId,
                                             String operatorName, String remark) {
        DcepSubWalletLedger l = new DcepSubWalletLedger();
        l.setTenantId(wallet.getTenantId());
        l.setSubWalletId(wallet.getId());
        l.setLedgerNo(nextLedgerNo());
        l.setTxType(txType);
        l.setAmount(amount);
        l.setBalanceBefore(balanceBefore);
        l.setBalanceAfter(balanceAfter);
        l.setFee(fee == null ? BigDecimal.ZERO : fee);
        l.setOppositeWalletId(oppositeId);
        l.setOppositeWalletName(oppositeName);
        l.setOrderNo(orderNo);
        l.setOperatorId(operatorId);
        l.setOperatorName(operatorName);
        l.setRemark(remark);
        return l;
    }
}
