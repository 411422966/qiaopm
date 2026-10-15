package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ServiceMaterialDeductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 项目自动扣减服务实现
 *
 * 关键算法：
 * 1. 加载服务→耗材绑定规则
 * 2. 计算实际用量(标准量 × 服务次数 × (1+损耗率))
 * 3. FEFO排序批次(过期日早的优先)
 * 4. 循环扣减，使用乐观锁(version) 防止超扣
 * 5. 库存不足时查找替代耗材
 * 6. 全部过程写入扣减日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceMaterialDeductServiceImpl implements ServiceMaterialDeductService {

    private static final int MAX_RETRY = 3;
    private static final int BATCH_SIZE = 50;

    private final ServiceMaterialRuleMapper ruleMapper;
    private final ServiceMaterialDeductLogMapper deductLogMapper;
    private final StockShortageLogMapper shortageLogMapper;
    private final MaterialSubstituteMapper substituteMapper;
    private final StockInventoryMapper stockInventoryMapper;
    private final MaterialConsumptionLogMapper consumptionLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeductResult deductForService(DeductContext context) {
        DeductResult result = new DeductResult();
        result.transactionId = UUID.randomUUID().toString().replace("-", "");
        result.errorMessages = new ArrayList<>();

        // 1. 加载耗材绑定规则
        List<ServiceMaterialRule> rules = ruleMapper.findByServiceId(context.serviceId);
        if (rules == null || rules.isEmpty()) {
            log.info("服务[{}]无耗材绑定规则", context.serviceId);
            result.success = true;
            return result;
        }

        result.totalPlan = rules.size();

        // 2. 逐条扣减
        for (ServiceMaterialRule rule : rules) {
            try {
                boolean ok = deductOne(context, rule, result);
                if (ok) {
                    result.successCount++;
                } else {
                    result.failedCount++;
                }
            } catch (Exception e) {
                log.error("扣减失败: rule={}", rule.getId(), e);
                result.failedCount++;
                result.errorMessages.add("耗材[" + rule.getProductName() + "]扣减异常: " + e.getMessage());
            }
        }

        result.success = result.failedCount == 0;
        return result;
    }

    /**
     * 单条耗材扣减
     */
    private boolean deductOne(DeductContext context, ServiceMaterialRule rule, DeductResult result) {
        // 1. 计算实际扣减量
        BigDecimal wasteMultiplier = BigDecimal.ONE.add(
                rule.getWasteRate() == null ? BigDecimal.ZERO : rule.getWasteRate());
        BigDecimal plannedQty = rule.getStandardQuantity()
                .multiply(BigDecimal.valueOf(context.serviceQuantity))
                .multiply(wasteMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        // 2. FEFO选批次
        List<Map<String, Object>> batches = selectBatchesByFEFO(
                context.storeId, rule.getProductId(), plannedQty, rule.getFefoEnabled() != null && rule.getFefoEnabled() == 1);

        if (batches.isEmpty()) {
            // 无任何批次库存,记录短缺日志
            recordShortage(context, rule, plannedQty, BigDecimal.ZERO, null);
            // 尝试替代
            return trySubstitute(context, rule, plannedQty, result);
        }

        BigDecimal remaining = plannedQty;
        int retryCount = 0;
        boolean overallSuccess = false;

        for (Map<String, Object> batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            try {
                Long inventoryId = ((Number) batch.get("id")).longValue();
                Integer batchStock = ((Number) batch.get("quantity")).intValue();

                // 乐观锁扣减
                DeductOneResult r = doDeductWithOptimisticLock(
                        inventoryId, batch, remaining, retryCount, context, rule, plannedQty);

                if (r.success) {
                    remaining = remaining.subtract(r.deducted);
                    retryCount = r.retry;
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        overallSuccess = true;
                        break;
                    }
                } else {
                    retryCount = r.retry;
                    // 乐观锁失败,重新加载批次重试
                    if (retryCount >= MAX_RETRY) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("批次扣减异常: batch={}", batch.get("id"), e);
            }
        }

        // 仍有剩余未扣减 → 记录短缺 + 尝试替代
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            recordShortage(context, rule, plannedQty, plannedQty.subtract(remaining), null);
            boolean substituted = trySubstitute(context, rule, remaining, result);
            if (substituted) {
                result.substituteCount++;
            }
            return false;
        }

        return overallSuccess;
    }

    /**
     * 乐观锁单次扣减
     */
    private DeductOneResult doDeductWithOptimisticLock(Long inventoryId,
                                                        Map<String, Object> batch,
                                                        BigDecimal remaining,
                                                        int startRetry,
                                                        DeductContext context,
                                                        ServiceMaterialRule rule,
                                                        BigDecimal plannedQty) {
        DeductOneResult r = new DeductOneResult();
        r.retry = startRetry;

        Integer version = batch.get("version") == null ? 0 : ((Number) batch.get("version")).intValue();
        BigDecimal unitCost = batch.get("cost_price") == null ? BigDecimal.ZERO :
                new BigDecimal(batch.get("cost_price").toString());

        for (int attempt = startRetry; attempt < MAX_RETRY; attempt++) {
            r.retry = attempt;
            Integer currentStock = ((Number) batch.get("quantity")).intValue();
            BigDecimal deduct = remaining.min(BigDecimal.valueOf(currentStock));

            if (deduct.compareTo(BigDecimal.ZERO) <= 0) {
                r.success = true;
                r.deducted = BigDecimal.ZERO;
                return r;
            }

            // 乐观锁更新（基于version）
            int updated = stockInventoryMapper.deductWithOptimisticLock(
                    inventoryId, version, currentStock - deduct.intValue());

            if (updated > 0) {
                // 扣减成功,写日志
                writeDeductLog(context, rule, batch, deduct, unitCost, attempt, "success", null);
                writeConsumptionLog(context, rule, batch, deduct, unitCost, false);

                r.success = true;
                r.deducted = deduct;
                return r;
            } else {
                // 乐观锁失败,重试
                log.debug("乐观锁冲突重试: inventoryId={}, attempt={}", inventoryId, attempt + 1);
                // 重新加载
                Map<String, Object> refreshed = stockInventoryMapper.findInventoryById(inventoryId);
                if (refreshed == null) {
                    r.success = false;
                    return r;
                }
                batch.putAll(refreshed);
                version = refreshed.get("version") == null ? 0 : ((Number) refreshed.get("version")).intValue();
            }
        }

        r.success = false;
        return r;
    }

    private static class DeductOneResult {
        boolean success;
        BigDecimal deducted = BigDecimal.ZERO;
        int retry;
    }

    /**
     * FEFO 选批次
     */
    private List<Map<String, Object>> selectBatchesByFEFO(Long storeId, Long productId,
                                                           BigDecimal requiredQty, boolean fefoEnabled) {
        QueryWrapper<StockInventory> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("store_id", storeId)
                .eq("product_id", productId)
                .gt("quantity", 0);

        if (fefoEnabled) {
            // FEFO: 优先过期日早的
            wrapper.orderByAsc(true, "expiry_date", "production_date");
        } else {
            // FIFO: 先入库的先出
            wrapper.orderByAsc(true, "last_in_time");
        }

        List<StockInventory> list = stockInventoryMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (StockInventory inv : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", inv.getId());
            map.put("quantity", inv.getQuantity());
            map.put("version", inv.getVersion());
            map.put("batch_no", inv.getBatchNo());
            map.put("expiry_date", inv.getExpiryDate());
            map.put("cost_price", unitCostFromInventory(inv));
            result.add(map);
        }
        return result;
    }

    private BigDecimal unitCostFromInventory(StockInventory inv) {
        // 实际应从stock_product表查询cost_price，这里简化
        return BigDecimal.ZERO;
    }

    /**
     * 尝试使用替代耗材
     */
    private boolean trySubstitute(DeductContext context, ServiceMaterialRule rule,
                                   BigDecimal shortage, DeductResult result) {
        // 查找替代规则
        List<MaterialSubstitute> substitutes = substituteMapper.findByMainProduct(
                rule.getProductId(), TenantContextHolder.getTenantIdOrNull());
        if (substitutes == null || substitutes.isEmpty()) {
            return false;
        }

        for (MaterialSubstitute sub : substitutes) {
            if (sub.getStatus() != null && sub.getStatus() == 0) continue;

            // 按替代比例换算
            BigDecimal substituteQty = shortage.multiply(sub.getSubstituteRatio() == null ? BigDecimal.ONE : sub.getSubstituteRatio());

            List<Map<String, Object>> batches = selectBatchesByFEFO(
                    context.storeId, sub.getSubstituteProductId(), substituteQty, true);

            if (batches.isEmpty()) continue;

            // 尝试扣减替代耗材
            for (Map<String, Object> batch : batches) {
                Integer stock = ((Number) batch.get("quantity")).intValue();
                if (stock <= 0) continue;
                Integer version = batch.get("version") == null ? 0 : ((Number) batch.get("version")).intValue();
                BigDecimal deduct = substituteQty.min(BigDecimal.valueOf(stock));
                int updated = stockInventoryMapper.deductWithOptimisticLock(
                        ((Number) batch.get("id")).longValue(), version, stock - deduct.intValue());
                if (updated > 0) {
                    // 写扣减日志(标记替代)
                    ServiceMaterialDeductLog log = new ServiceMaterialDeductLog();
                    log.setOrderId(context.orderId);
                    log.setOrderNo(context.orderNo);
                    log.setOrderItemId(context.orderItemId);
                    log.setServiceId(context.serviceId);
                    log.setServiceName(context.serviceName);
                    log.setProductId(sub.getSubstituteProductId());
                    log.setInventoryId(((Number) batch.get("id")).longValue());
                    log.setBatchNo((String) batch.get("batch_no"));
                    log.setPlannedQuantity(shortage);
                    log.setActualQuantity(deduct);
                    log.setDeductStrategy("substitute");
                    log.setStatus("success");
                    log.setIsSubstitute(1);
                    log.setOriginalProductId(rule.getProductId());
                    log.setEmployeeId(context.employeeId);
                    log.setEmployeeName(context.employeeName);
                    log.setMemberId(context.memberId);
                    log.setStoreId(context.storeId);
                    log.setTransactionId(result.transactionId);
                    log.setDeductTime(LocalDateTime.now());
                    deductLogMapper.insert(log);

                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 写扣减日志
     */
    private void writeDeductLog(DeductContext context, ServiceMaterialRule rule,
                                 Map<String, Object> batch, BigDecimal deducted,
                                 BigDecimal unitCost, int retry, String status, String failure) {
        ServiceMaterialDeductLog log = new ServiceMaterialDeductLog();
        log.setOrderId(context.orderId);
        log.setOrderNo(context.orderNo);
        log.setOrderItemId(context.orderItemId);
        log.setServiceId(context.serviceId);
        log.setServiceName(context.serviceName);
        log.setProductId(rule.getProductId());
        log.setProductName(rule.getProductName());
        log.setInventoryId(((Number) batch.get("id")).longValue());
        log.setBatchNo((String) batch.get("batch_no"));
        log.setPlannedQuantity(rule.getStandardQuantity());
        log.setActualQuantity(deducted);
        log.setUnitCost(unitCost);
        log.setTotalCost(unitCost.multiply(deducted));
        log.setDeductStrategy("fefo");
        log.setBeforeStock(((Number) batch.get("quantity")).intValue());
        log.setAfterStock(((Number) batch.get("quantity")).intValue() - deducted.intValue());
        log.setStatus(status);
        log.setFailureReason(failure);
        log.setEmployeeId(context.employeeId);
        log.setEmployeeName(context.employeeName);
        log.setMemberId(context.memberId);
        log.setStoreId(context.storeId);
        log.setRetryCount(retry);
        log.setDeductTime(LocalDateTime.now());
        deductLogMapper.insert(log);
    }

    /**
     * 写原有消耗日志(兼容)
     */
    private void writeConsumptionLog(DeductContext context, ServiceMaterialRule rule,
                                      Map<String, Object> batch, BigDecimal deducted,
                                      BigDecimal unitCost, boolean isSubstitute) {
        MaterialConsumptionLog log = new MaterialConsumptionLog();
        log.setOrderId(context.orderId);
        log.setOrderNo(context.orderNo);
        log.setOrderItemId(context.orderItemId);
        log.setServiceId(context.serviceId);
        log.setServiceName(context.serviceName);
        log.setProductId(rule.getProductId());
        log.setProductName(rule.getProductName());
        log.setSpec(rule.getSpec());
        log.setQuantity(deducted);
        log.setUnitCost(unitCost);
        log.setTotalCost(unitCost.multiply(deducted));
        log.setIsSubstitute(isSubstitute ? 1 : 0);
        log.setEmployeeId(context.employeeId);
        log.setEmployeeName(context.employeeName);
        log.setMemberId(context.memberId);
        log.setStoreId(context.storeId);
        log.setConsumptionDate(LocalDate.now());
        consumptionLogMapper.insert(log);
    }

    /**
     * 记录库存不足
     */
    private void recordShortage(DeductContext context, ServiceMaterialRule rule,
                                BigDecimal required, BigDecimal currentStock, Long substituteId) {
        StockShortageLog shortage = new StockShortageLog();
        shortage.setOrderId(context.orderId);
        shortage.setOrderNo(context.orderNo);
        shortage.setServiceId(context.serviceId);
        shortage.setServiceName(context.serviceName);
        shortage.setProductId(rule.getProductId());
        shortage.setProductName(rule.getProductName());
        shortage.setRequiredQuantity(required);
        shortage.setCurrentStock(currentStock);
        shortage.setShortageQuantity(required.subtract(currentStock));
        shortage.setSubstituted(substituteId != null ? 1 : 0);
        shortage.setSubstituteProductId(substituteId);
        shortage.setAlertLevel(required.subtract(currentStock).compareTo(BigDecimal.valueOf(10)) > 0 ? "critical" : "warning");
        shortage.setStatus(substituteId != null ? "resolved" : "pending");
        shortage.setStoreId(context.storeId);
        shortageLogMapper.insert(shortage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackByOrderId(Long orderId, String reason) {
        // 查询该订单所有扣减记录,反向回滚
        List<ServiceMaterialDeductLog> logs = deductLogMapper.queryList(orderId, "success", null);
        for (ServiceMaterialDeductLog logEntry : logs) {
            try {
                stockInventoryMapper.rollbackDeduct(logEntry.getInventoryId(), logEntry.getActualQuantity().intValue());
            } catch (Exception e) {
                log.error("回滚失败: order={}, inventory={}", orderId, logEntry.getInventoryId(), e);
                return false;
            }
        }
        return true;
    }
}