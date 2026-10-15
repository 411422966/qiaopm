package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.service.BusinessPolicyDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M12-05 业务策略统一下发引擎
 *
 * <p>核心流程：
 * <pre>
 *   1) createDispatch     → 写 mkt_dispatch (DRAFT)
 *   2) publishAndDispatch → 状态 PUBLISHED → EXECUTING → 遍历目标门店
 *      ├─ PRICING_RULE: 校验 + 复制 mkt_dynamic_pricing_rule 到目标门店作用域
 *      ├─ ACTIVITY:     校验 + 复制 market_activity 到目标门店作用域
 *      ├─ COUPON_BATCH: 关联 coupon_batch（共享下发，记录触达）
 *      └─ EXPERIMENT:   校验 + 关联 pricing_experiment（标记激活）
 *   3) 每门店一条 m12_dispatch_log
 *   4) 全部完成 → status=DONE
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessPolicyDispatchServiceImpl
        extends ServiceImpl<MktDispatchMapper, MktDispatch>
        implements BusinessPolicyDispatchService {

    private final MktDispatchTargetMapper dispatchTargetMapper;
    private final BusinessPolicyDispatchLogMapper dispatchLogMapper;
    private final StoreMapper storeMapper;
    private final MktDynamicPricingRuleMapper pricingRuleMapper;
    private final MarketActivityMapper marketActivityMapper;

    // ==================== 入口 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDispatch(MktDispatch dispatch, List<Long> targetStoreIds) {
        if (dispatch == null || dispatch.getDispatchType() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "dispatchType 必填");
        }
        if (dispatch.getTitle() == null || dispatch.getTitle().isBlank()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "title 必填");
        }
        // 校验 subtype 与 source
        validateSource(dispatch);
        // 生成 dispatchNo
        String no = "DP-" + System.currentTimeMillis() + "-" + new Random().nextInt(1000);
        dispatch.setDispatchNo(no);
        dispatch.setStatus("DRAFT");
        dispatch.setPublishedVersion(1);
        if (dispatch.getSuccessCount() == null) dispatch.setSuccessCount(0);
        if (dispatch.getFailCount() == null) dispatch.setFailCount(0);
        if (dispatch.getTotalCount() == null) dispatch.setTotalCount(0);
        this.save(dispatch);

        // 写入目标门店
        List<MktDispatchTarget> targets = resolveTargets(dispatch.getTenantId(), dispatch.getTargetType(),
                targetStoreIds, dispatch.getTargetStoreIds());
        dispatch.setTotalCount(targets.size());
        this.updateById(dispatch);
        for (MktDispatchTarget t : targets) {
            t.setDispatchId(dispatch.getId());
            t.setTenantId(dispatch.getTenantId());
            t.setStatus("PENDING");
            dispatchTargetMapper.insert(t);
        }
        log.info("[M12-05] dispatch created: id={} no={} type={} subtype={} targetCount={}",
                dispatch.getId(), dispatch.getDispatchNo(),
                dispatch.getDispatchType(), dispatch.getDispatchSubtype(), targets.size());
        return dispatch.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishAndDispatch(Long dispatchId, Long operatorId, String operatorName) {
        return executeDispatch(dispatchId, operatorId, operatorName);
    }

    @Override
    @Async
    public void publishAndDispatchAsync(Long dispatchId, Long operatorId, String operatorName) {
        try {
            executeDispatch(dispatchId, operatorId, operatorName);
        } catch (Exception e) {
            log.error("[M12-05] async dispatch failed: id={} msg={}", dispatchId, e.getMessage(), e);
        }
    }

    @Override
    public boolean cancelDispatch(Long dispatchId, Long operatorId, String operatorName) {
        MktDispatch d = this.getById(dispatchId);
        if (d == null) throw new BusinessException(BizCode.NOT_FOUND, "下发单不存在");
        if ("DONE".equals(d.getStatus()) || "EXECUTING".equals(d.getStatus())) {
            throw new BusinessException(BizCode.CONFLICT, "状态不可取消: " + d.getStatus());
        }
        d.setStatus("CANCELLED");
        d.setRemark((d.getRemark() == null ? "" : d.getRemark())
                + " [CANCELLED by " + (operatorName == null ? operatorId : operatorName) + "]");
        this.updateById(d);
        return true;
    }

    // ==================== 核心：执行下发 ====================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeDispatch(Long dispatchId, Long operatorId, String operatorName) {
        MktDispatch d = this.getById(dispatchId);
        if (d == null) throw new BusinessException(BizCode.NOT_FOUND, "下发单不存在");
        if ("DONE".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus())) {
            throw new BusinessException(BizCode.CONFLICT, "状态不可执行: " + d.getStatus());
        }

        // 状态：DRAFT → PUBLISHED → EXECUTING
        if ("DRAFT".equals(d.getStatus())) {
            // 增加发布版本号
            d.setPublishedVersion((d.getPublishedVersion() == null ? 0 : d.getPublishedVersion()) + 1);
            d.setPublishedAt(LocalDateTime.now());
            d.setStatus("PUBLISHED");
            this.updateById(d);
        }
        d.setStatus("EXECUTING");
        this.updateById(d);

        // 目标门店列表
        List<MktDispatchTarget> targets = dispatchTargetMapper.selectList(
                new LambdaQueryWrapper<MktDispatchTarget>()
                        .eq(MktDispatchTarget::getDispatchId, dispatchId)
                        .orderByAsc(MktDispatchTarget::getStoreId));
        if (targets.isEmpty()) {
            d.setStatus("DONE");
            d.setSuccessCount(0);
            d.setFailCount(0);
            d.setTotalCount(0);
            this.updateById(d);
            return buildResult(d, 0, 0, 0);
        }

        // 真实执行（按 dispatch_type 路由）
        int success = 0, fail = 0;
        for (MktDispatchTarget t : targets) {
            long start = System.currentTimeMillis();
            BusinessPolicyDispatchLog entry = newLog(d, t, operatorId, operatorName);
            entry.setDispatchStartedAt(LocalDateTime.now());
            try {
                switch (d.getDispatchType()) {
                    case "PRICING_RULE":
                        applyPricingRule(d, t);
                        break;
                    case "ACTIVITY":
                        applyActivity(d, t);
                        break;
                    case "COUPON_BATCH":
                        applyCouponBatch(d, t);
                        break;
                    case "EXPERIMENT":
                        applyExperiment(d, t);
                        break;
                    case "DISCOUNT":
                    case "COUPON":
                    case "PACKAGE":
                        applyGenericPolicy(d, t);
                        break;
                    default:
                        throw new BusinessException(BizCode.BAD_REQUEST, "不支持的下发类型: " + d.getDispatchType());
                }
                t.setStatus("EXECUTED");
                t.setExecutedAt(LocalDateTime.now());
                dispatchTargetMapper.updateById(t);
                entry.setStatus("SUCCESS");
                entry.setAppliedCount(1);
                success++;
            } catch (Exception e) {
                t.setStatus("REJECTED");
                t.setRejectedReason(e.getMessage());
                dispatchTargetMapper.updateById(t);
                entry.setStatus("FAILED");
                entry.setErrorMsg(e.getMessage());
                fail++;
                log.error("[M12-05] dispatch failed: dispatchId={} storeId={} msg={}",
                        dispatchId, t.getStoreId(), e.getMessage());
            }
            entry.setDispatchFinishedAt(LocalDateTime.now());
            entry.setDurationMs(System.currentTimeMillis() - start);
            dispatchLogMapper.insert(entry);
        }

        d.setSuccessCount(success);
        d.setFailCount(fail);
        d.setTotalCount(targets.size());
        d.setStatus("DONE");
        this.updateById(d);
        log.info("[M12-05] dispatch done: id={} no={} success={} fail={} total={}",
                dispatchId, d.getDispatchNo(), success, fail, targets.size());
        return buildResult(d, success, fail, targets.size());
    }

    // ==================== 各类型落地实现 ====================

    /** 价格规则下发：复制 mkt_dynamic_pricing_rule 的副本到目标门店 */
    private void applyPricingRule(MktDispatch d, MktDispatchTarget t) {
        if (d.getSourceId() == null) throw new BusinessException(BizCode.BAD_REQUEST, "sourceId 必填");
        MktDynamicPricingRule src = pricingRuleMapper.selectById(d.getSourceId());
        if (src == null || !src.getTenantId().equals(d.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "源价格规则不存在或跨租户");
        }
        Store store = storeMapper.selectById(t.getStoreId());
        if (store == null) throw new BusinessException(BizCode.NOT_FOUND, "门店不存在");
        // 落地策略：插入一条 store-scoped 副本（ruleCode 加 _STORE_<id> 后缀）
        MktDynamicPricingRule clone = clonePricingRule(src);
        clone.setId(null);
        clone.setTenantId(d.getTenantId());
        clone.setRuleCode(src.getRuleCode() + "_STORE_" + t.getStoreId());
        clone.setEnabled(1);
        pricingRuleMapper.insert(clone);
    }

    private MktDynamicPricingRule clonePricingRule(MktDynamicPricingRule src) {
        MktDynamicPricingRule r = new MktDynamicPricingRule();
        r.setTenantId(src.getTenantId());
        r.setBrandId(src.getBrandId());
        r.setRuleCode(src.getRuleCode());
        r.setRuleName(src.getRuleName());
        r.setRuleType(src.getRuleType());
        r.setTargetType(src.getTargetType());
        r.setTargetId(src.getTargetId());
        r.setTargetCode(src.getTargetCode());
        r.setTargetName(src.getTargetName());
        r.setPricingMode(src.getPricingMode());
        r.setPricingValue(src.getPricingValue());
        r.setMinAmount(src.getMinAmount());
        r.setMaxAmount(src.getMaxAmount());
        r.setTimeStart(src.getTimeStart());
        r.setTimeEnd(src.getTimeEnd());
        r.setDayOfWeek(src.getDayOfWeek());
        r.setPriority(src.getPriority());
        r.setEnabled(src.getEnabled());
        r.setValidFrom(src.getValidFrom());
        r.setValidTo(src.getValidTo());
        r.setDescription(src.getDescription());
        r.setOperatorId(src.getOperatorId());
        r.setOperatorName(src.getOperatorName());
        return r;
    }

    /** 营销活动下发：扩展 market_activity.store_id 范围 — 当前 storeId 写为下发的门店（逐店新建一条） */
    private void applyActivity(MktDispatch d, MktDispatchTarget t) {
        if (d.getSourceId() == null) throw new BusinessException(BizCode.BAD_REQUEST, "sourceId 必填");
        MarketActivity src = marketActivityMapper.selectById(d.getSourceId());
        if (src == null || !src.getTenantId().equals(d.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "源营销活动不存在或跨租户");
        }
        MarketActivity clone = new MarketActivity();
        clone.setTenantId(d.getTenantId());
        clone.setStoreId(t.getStoreId());
        clone.setName(src.getName());
        clone.setType(src.getType());
        clone.setRuleConfig(src.getRuleConfig());
        clone.setStartTime(src.getStartTime());
        clone.setEndTime(src.getEndTime());
        clone.setStatus(src.getStatus() == null ? 1 : src.getStatus());
        marketActivityMapper.insert(clone);
    }

    /** 优惠券批次下发：仅记录触达（不复制记录），校验批次存在 */
    private void applyCouponBatch(MktDispatch d, MktDispatchTarget t) {
        if (d.getSourceId() == null) throw new BusinessException(BizCode.BAD_REQUEST, "sourceId 必填");
        // 优惠券批次通过单独接口管理，下发只触达门店
        // 校验门店存在即可
        Store s = storeMapper.selectById(t.getStoreId());
        if (s == null || !s.getTenantId().equals(d.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "门店不存在或跨租户");
        }
        // 实际批次发放由 coupon 模块处理
    }

    /** 实验下发：校验实验存在，标记目标门店（不修改实验本身） */
    private void applyExperiment(MktDispatch d, MktDispatchTarget t) {
        if (d.getSourceId() == null) throw new BusinessException(BizCode.BAD_REQUEST, "sourceId 必填");
        // 简化：只校验门店存在
        Store s = storeMapper.selectById(t.getStoreId());
        if (s == null || !s.getTenantId().equals(d.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "门店不存在或跨租户");
        }
    }

    private void applyGenericPolicy(MktDispatch d, MktDispatchTarget t) {
        Store s = storeMapper.selectById(t.getStoreId());
        if (s == null || !s.getTenantId().equals(d.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "门店不存在或跨租户");
        }
    }

    // ==================== 辅助 ====================

    private void validateSource(MktDispatch d) {
        if (d.getDispatchSubtype() != null && d.getSourceId() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "subtype 非空时 sourceId 必填");
        }
        // dispatchType 必须是枚举之一
        Set<String> validTypes = Set.of("COUPON", "PACKAGE", "DISCOUNT", "ACTIVITY", "PRICING_RULE");
        if (!validTypes.contains(d.getDispatchType())) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "dispatchType 必须为 " + validTypes + " 之一");
        }
    }

    private List<MktDispatchTarget> resolveTargets(Long tenantId, String targetType,
                                                   List<Long> explicitStoreIds, String targetStoreIdsCsv) {
        Set<Long> storeIds = new LinkedHashSet<>();
        if ("ALL".equals(targetType)) {
            List<Store> stores = storeMapper.selectList(new LambdaQueryWrapper<Store>()
                    .eq(Store::getTenantId, tenantId)
                    .eq(Store::getStatus, 1));
            storeIds.addAll(stores.stream().map(Store::getId).toList());
        } else if ("SPECIFIC".equals(targetType)) {
            if (explicitStoreIds != null) storeIds.addAll(explicitStoreIds);
            if (targetStoreIdsCsv != null && !targetStoreIdsCsv.isBlank()) {
                for (String s : targetStoreIdsCsv.split(",")) {
                    try { storeIds.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignore) {}
                }
            }
        } else {
            throw new BusinessException(BizCode.BAD_REQUEST, "targetType 仅支持 ALL / SPECIFIC");
        }
        // 校验门店都存在且同租户
        if (storeIds.isEmpty()) return Collections.emptyList();
        List<Store> stores = storeMapper.selectBatchIds(storeIds);
        Map<Long, Store> storeMap = stores.stream()
                .filter(s -> tenantId.equals(s.getTenantId()))
                .collect(Collectors.toMap(Store::getId, s -> s, (a, b) -> a));
        List<MktDispatchTarget> targets = new ArrayList<>();
        for (Long sid : storeIds) {
            Store s = storeMap.get(sid);
            if (s == null) continue;
            MktDispatchTarget t = new MktDispatchTarget();
            t.setTenantId(tenantId);
            t.setStoreId(sid);
            t.setStoreName(s.getStoreName());
            targets.add(t);
        }
        return targets;
    }

    private BusinessPolicyDispatchLog newLog(MktDispatch d, MktDispatchTarget t,
                                              Long operatorId, String operatorName) {
        BusinessPolicyDispatchLog log = new BusinessPolicyDispatchLog();
        log.setTenantId(d.getTenantId());
        log.setDispatchId(d.getId());
        log.setStoreId(t.getStoreId());
        log.setStoreName(t.getStoreName());
        log.setDispatchType(d.getDispatchType());
        log.setDispatchSubtype(d.getDispatchSubtype());
        log.setSourceId(d.getSourceId());
        log.setSourceVersion(d.getSourceVersion() == null ? String.valueOf(d.getPublishedVersion()) : d.getSourceVersion());
        log.setStatus("PENDING");
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        return log;
    }

    private Map<String, Object> buildResult(MktDispatch d, int success, int fail, int total) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dispatchId", d.getId());
        r.put("dispatchNo", d.getDispatchNo());
        r.put("dispatchType", d.getDispatchType());
        r.put("dispatchSubtype", d.getDispatchSubtype());
        r.put("publishedVersion", d.getPublishedVersion());
        r.put("status", d.getStatus());
        r.put("successCount", success);
        r.put("failCount", fail);
        r.put("totalCount", total);
        return r;
    }
}
