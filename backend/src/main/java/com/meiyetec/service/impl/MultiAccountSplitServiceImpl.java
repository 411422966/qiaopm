package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.service.MultiAccountSplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * M02-04 多账户分账实现
 *
 * <p>核心语义：
 * <ol>
 *   <li>订单支付成功（OrderPaidEvent AFTER_COMMIT）时触发分账记录（幂等）</li>
 *   <li>分账记录 = m02_split_record（订单维度） + m02_split_record_detail（每方明细）</li>
 *   <li>不操作资金划拨 — 实际资金由「分账执行」接口或定时任务调 DcepSubWalletService.transfer</li>
 *   <li>分账执行后更新 detail.ledger_id（关联账本流水）</li>
 * </ol>
 *
 * <p>规则匹配优先级：
 * <pre>
 *   1. rule_scope=SERVICE 且 service_id 精确匹配
 *   2. rule_scope=ALL 且 store_id 精确匹配
 *   3. rule_scope=ALL 且 store_id IS NULL（租户全局）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAccountSplitServiceImpl
        extends ServiceImpl<MultiAccountSplitRecordMapper, MultiAccountSplitRecord>
        implements MultiAccountSplitService {

    private final MultiAccountSplitRuleMapper ruleMapper;
    private final MultiAccountSplitRecordDetailMapper detailMapper;
    private final DcepSubWalletMapper subWalletMapper;
    private final DcepSubWalletLedgerMapper ledgerMapper;

    @Autowired
    private DcepSubWalletServiceImpl subWalletService;

    // ==================== 入口 ====================

    @Override
    @Async
    public void splitOrderAsync(PosOrder order) {
        try {
            doSplitRecordOnly(order, null, "SYSTEM");
        } catch (Exception e) {
            log.error("[M02-04] async split failed: orderId={} msg={}", order.getId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> splitOrderSync(PosOrder order, Long operatorId, String operatorName) {
        return doSplitRecordOnly(order, operatorId, operatorName);
    }

    // ==================== 核心：仅记录分账，不操作资金划拨 ====================

    private Map<String, Object> doSplitRecordOnly(PosOrder order, Long operatorId, String operatorName) {
        if (order == null || order.getId() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "订单不能为空");
        }
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new BusinessException(BizCode.BAD_REQUEST, "订单未支付，不触发分账");
        }
        BigDecimal orderAmount = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "订单实付金额必须 > 0");
        }
        Long tenantId = order.getTenantId();
        Long storeId = order.getStoreId();

        // 幂等：已成功/部分成功的不重复
        MultiAccountSplitRecord existing = this.getOne(new LambdaQueryWrapper<MultiAccountSplitRecord>()
                .eq(MultiAccountSplitRecord::getTenantId, tenantId)
                .eq(MultiAccountSplitRecord::getOrderId, order.getId())
                .last("LIMIT 1"));
        if (existing != null && existing.getStatus() != null
                && (existing.getStatus() == 2 || existing.getStatus() == 4)) {
            log.info("[M02-04] skip duplicate: orderId={} status={}", order.getId(), existing.getStatus());
            return buildResult(existing, Collections.emptyList());
        }

        // === 匹配规则 ===
        MultiAccountSplitRule rule = pickRule(tenantId, storeId, null);
        if (rule == null) {
            String msg = "未找到匹配的分账规则";
            log.warn("[M02-04] {}: tenantId={} storeId={}", msg, tenantId, storeId);
            return recordFailed(order, operatorId, operatorName, msg);
        }

        // === 计算金额 ===
        BigDecimal platformFee = orderAmount.multiply(nz(rule.getPlatformFeeRatio()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal splitTotal = orderAmount.subtract(platformFee);

        // === 写分账记录 ===
        MultiAccountSplitRecord record = new MultiAccountSplitRecord();
        record.setTenantId(tenantId);
        record.setStoreId(storeId);
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setRuleId(rule.getId());
        record.setRuleName(rule.getRuleName());
        record.setOrderAmount(orderAmount);
        record.setFeeAmount(platformFee);
        record.setSplitTotal(splitTotal);
        record.setStatus(1); // PENDING
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setSplitTime(LocalDateTime.now());
        this.save(record);

        // === 写分账明细（按比例 HALF_UP，最后一行兜底）===
        List<ReceiverSpec> specs = buildSpecs(rule, splitTotal);
        List<MultiAccountSplitRecordDetail> details = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            ReceiverSpec s = specs.get(i);
            boolean last = (i == specs.size() - 1);
            BigDecimal amount = last
                    ? splitTotal.subtract(allocatedSoFar(specs, i, splitTotal))
                    : splitTotal.multiply(nz(s.ratio))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            MultiAccountSplitRecordDetail d = new MultiAccountSplitRecordDetail();
            d.setTenantId(tenantId);
            d.setSplitRecordId(record.getId());
            d.setReceiverRole(s.role);
            d.setReceiverType(s.walletType);
            d.setWalletId(s.walletId);
            d.setWalletCode(s.walletCode);
            d.setWalletName(s.walletName);
            d.setRefId(s.refId);
            d.setRefName(s.refName);
            d.setRatio(nz(s.ratio));
            d.setAmount(amount);
            d.setActualAmount(BigDecimal.ZERO);
            d.setStatus(1); // SUCCESS（记录即成功）
            detailMapper.insert(d);
            details.add(d);
        }
        // 平台抽佣单独行
        if (platformFee.compareTo(BigDecimal.ZERO) > 0) {
            MultiAccountSplitRecordDetail fd = buildPlatformFeeRow(tenantId, record.getId(), platformFee);
            detailMapper.insert(fd);
            details.add(fd);
        }

        record.setStatus(2); // SUCCESS
        this.updateById(record);
        log.info("[M02-04] split recorded: orderId={} ruleId={} amount={} fee={} status=SUCCESS",
                order.getId(), rule.getId(), orderAmount, platformFee);
        return buildResult(record, details);
    }

    // ==================== 分账执行（操作资金划拨）====================

    /**
     * 执行分账：将记录中的待分账金额划转到各方子钱包
     * <p>幂等：已执行过（detail 有 ledger_id）的不重复划拨
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeSplit(Long recordId, Long operatorId, String operatorName) {
        MultiAccountSplitRecord record = this.getById(recordId);
        if (record == null) throw new BusinessException(BizCode.NOT_FOUND, "分账记录不存在: " + recordId);
        if (record.getStatus() == null || record.getStatus() == 2) {
            // 已 SUCCESS → 查明细中无 ledger 的重新执行
        }
        if (record.getStatus() == null || record.getStatus() == 3) {
            throw new BusinessException(BizCode.BAD_REQUEST, "分账状态为 FAILED，不能执行");
        }

        List<MultiAccountSplitRecordDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<MultiAccountSplitRecordDetail>()
                        .eq(MultiAccountSplitRecordDetail::getSplitRecordId, recordId)
                        .eq(MultiAccountSplitRecordDetail::getDeleted, 0));
        if (details.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "无分账明细");
        }

        // source wallet：取租户/门店的「主结算子钱包」作为资金池
        Long sourceWalletId = resolveMainSettlementWallet(record.getTenantId(), record.getStoreId());
        if (sourceWalletId == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "未找到主结算子钱包（biz_dcep_sub_wallet.settlement）");
        }

        int successCount = 0, failCount = 0;
        for (MultiAccountSplitRecordDetail d : details) {
            if ("PLATFORM".equals(d.getReceiverRole())) continue; // 平台抽佣不执行划拨
            if (d.getWalletId() == null) {
                d.setStatus(2);
                d.setErrorMsg("子钱包ID为空");
                detailMapper.updateById(d);
                failCount++;
                continue;
            }
            if (d.getLedgerId() != null) {
                successCount++; // 已划拨过
                continue;
            }
            if (d.getActualAmount() == null || d.getActualAmount().compareTo(BigDecimal.ZERO) <= 0) {
                successCount++; // 金额为0视为成功
                continue;
            }
            try {
                Map<String, DcepSubWalletLedger> ledgers = subWalletService.transfer(
                        sourceWalletId, d.getWalletId(), d.getActualAmount(), BigDecimal.ZERO,
                        record.getOrderNo(),
                        operatorId == null ? 0L : operatorId,
                        operatorName == null ? "SYSTEM" : operatorName,
                        "M02-04 分账 " + d.getReceiverRole());
                DcepSubWalletLedger toLedger = ledgers.get("toLedger");
                if (toLedger != null) {
                    d.setLedgerId(toLedger.getId());
                    d.setActualAmount(d.getAmount()); // 以划拨额为实收
                    d.setStatus(1);
                    detailMapper.updateById(d);
                    successCount++;
                } else {
                    throw new BusinessException(BizCode.SYSTEM_ERROR, "transfer 无 toLedger");
                }
            } catch (Exception e) {
                d.setStatus(2);
                d.setErrorMsg(e.getMessage());
                d.setActualAmount(BigDecimal.ZERO);
                detailMapper.updateById(d);
                failCount++;
                log.error("[M02-04] execute transfer failed: detailId={} msg={}", d.getId(), e.getMessage());
            }
        }

        int finalStatus = failCount == 0 ? 2 : (successCount == 0 ? 3 : 4);
        record.setStatus(finalStatus);
        this.updateById(record);
        log.info("[M02-04] execute done: recordId={} status={} success={} fail={}",
                recordId, finalStatus, successCount, failCount);
        return buildResult(record, details);
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> recordFailed(PosOrder order, Long operatorId, String operatorName, String msg) {
        MultiAccountSplitRecord r = new MultiAccountSplitRecord();
        r.setTenantId(order.getTenantId());
        r.setStoreId(order.getStoreId());
        r.setOrderId(order.getId());
        r.setOrderNo(order.getOrderNo());
        r.setOrderAmount(order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount());
        r.setFeeAmount(BigDecimal.ZERO);
        r.setSplitTotal(BigDecimal.ZERO);
        r.setStatus(3); // FAILED
        r.setErrorMsg(msg);
        r.setOperatorId(operatorId);
        r.setOperatorName(operatorName);
        r.setSplitTime(LocalDateTime.now());
        this.save(r);
        return buildResult(r, Collections.emptyList());
    }

    private MultiAccountSplitRule pickRule(Long tenantId, Long storeId, Long serviceId) {
        // 1) service 精确
        if (serviceId != null) {
            List<MultiAccountSplitRule> candidates = ruleMapper.matchCandidates(tenantId, storeId, serviceId);
            MultiAccountSplitRule hit = candidates.stream()
                    .filter(r -> r.getServiceId() != null && r.getServiceId().equals(serviceId))
                    .findFirst().orElse(null);
            if (hit != null) return hit;
        }
        // 2) store-level ALL
        List<MultiAccountSplitRule> storeCandidates = ruleMapper.matchCandidates(tenantId, storeId, null);
        MultiAccountSplitRule hit = storeCandidates.stream()
                .filter(r -> "ALL".equals(r.getRuleScope()) && storeId != null && storeId.equals(r.getStoreId()))
                .findFirst().orElse(null);
        if (hit != null) return hit;
        // 3) tenant-level ALL
        return storeCandidates.stream()
                .filter(r -> "ALL".equals(r.getRuleScope()) && r.getStoreId() == null)
                .findFirst().orElse(null);
    }

    private List<ReceiverSpec> buildSpecs(MultiAccountSplitRule rule, BigDecimal splitTotal) {
        List<ReceiverSpec> specs = new ArrayList<>();
        if (nz(rule.getProviderRatio()).compareTo(BigDecimal.ZERO) > 0 && rule.getProviderWalletId() != null) {
            DcepSubWallet w = subWalletMapper.selectById(rule.getProviderWalletId());
            specs.add(spec("PROVIDER", rule.getProviderWalletId(), w, rule.getProviderRatio(), null, null));
        }
        if (nz(rule.getBranchRatio()).compareTo(BigDecimal.ZERO) > 0 && rule.getBranchWalletId() != null) {
            DcepSubWallet w = subWalletMapper.selectById(rule.getBranchWalletId());
            specs.add(spec("BRANCH", rule.getBranchWalletId(), w, rule.getBranchRatio(), null, null));
        }
        if (nz(rule.getEmployeeRatio()).compareTo(BigDecimal.ZERO) > 0 && rule.getEmployeeWalletId() != null) {
            DcepSubWallet w = subWalletMapper.selectById(rule.getEmployeeWalletId());
            specs.add(spec("EMPLOYEE", rule.getEmployeeWalletId(), w, rule.getEmployeeRatio(), null, null));
        }
        return specs;
    }

    private ReceiverSpec spec(String role, Long walletId, DcepSubWallet w, BigDecimal ratio, Long refId, String refName) {
        ReceiverSpec s = new ReceiverSpec();
        s.role = role;
        s.walletId = walletId;
        s.ratio = ratio;
        s.refId = refId;
        s.refName = refName;
        if (w != null) {
            s.walletType = w.getWalletType();
            s.walletCode = w.getSubWalletCode();
            s.walletName = w.getSubWalletName();
        }
        return s;
    }

    private MultiAccountSplitRecordDetail buildPlatformFeeRow(Long tenantId, Long recordId, BigDecimal feeAmount) {
        DcepSubWallet settle = subWalletMapper.queryList(tenantId, null, null, "settlement", "active")
                .stream().findFirst().orElse(null);
        MultiAccountSplitRecordDetail d = new MultiAccountSplitRecordDetail();
        d.setTenantId(tenantId);
        d.setSplitRecordId(recordId);
        d.setReceiverRole("PLATFORM");
        d.setReceiverType("settlement");
        d.setWalletId(settle == null ? null : settle.getId());
        d.setWalletCode(settle == null ? null : settle.getSubWalletCode());
        d.setWalletName(settle == null ? "平台抽佣（未配置账户）" : settle.getSubWalletName());
        d.setRatio(BigDecimal.ZERO);
        d.setAmount(feeAmount);
        d.setActualAmount(BigDecimal.ZERO);
        d.setStatus(1);
        return d;
    }

    private BigDecimal allocatedSoFar(List<ReceiverSpec> specs, int upTo, BigDecimal splitTotal) {
        // spec.ratio 本身是百分比数字（如 60 表示 60%）
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < upTo; i++) {
            allocated = allocated.add(
                    splitTotal.multiply(nz(specs.get(i).ratio))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return allocated;
    }

    private Long resolveMainSettlementWallet(Long tenantId, Long storeId) {
        // 优先：store-level settlement；兜底：tenant-level settlement
        List<DcepSubWallet> list = subWalletMapper.queryList(tenantId, storeId, null, "settlement", "active");
        if (!list.isEmpty()) return list.get(0).getId();
        list = subWalletMapper.queryList(tenantId, null, null, "settlement", "active");
        return list.isEmpty() ? null : list.get(0).getId();
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Map<String, Object> buildResult(MultiAccountSplitRecord record,
                                            List<MultiAccountSplitRecordDetail> details) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("record", record);
        r.put("details", details);
        return r;
    }

    private static class ReceiverSpec {
        String role;
        Long walletId;
        String walletType;
        String walletCode;
        String walletName;
        BigDecimal ratio;
        Long refId;
        String refName;
    }
}
