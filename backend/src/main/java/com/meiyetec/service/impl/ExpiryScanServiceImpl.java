package com.meiyetec.service.impl;

import com.meiyetec.entity.ExpiryAlertLog;
import com.meiyetec.entity.ExpiryAlertRule;
import com.meiyetec.entity.StockInventory;
import com.meiyetec.entity.Store;
import com.meiyetec.mapper.ExpiryAlertLogMapper;
import com.meiyetec.mapper.ExpiryAlertRuleMapper;
import com.meiyetec.mapper.StockInventoryMapper;
import com.meiyetec.mapper.StoreMapper;
import com.meiyetec.service.ExpiryScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryScanServiceImpl implements ExpiryScanService {

    private final StockInventoryMapper stockInventoryMapper;
    private final ExpiryAlertRuleMapper expiryAlertRuleMapper;
    private final ExpiryAlertLogMapper expiryAlertLogMapper;
    private final StoreMapper storeMapper;

    /**
     * M07 定时扫描：每天 02:30 遍历所有有效门店执行效期扫描
     * <p>可通过 application.yml 配置：
     * <pre>
     *   stock:
     *     expiry:
     *       scan-enabled: true   # 关闭则停掉定时任务（手动 /scan/trigger）
     *       cron: "0 30 2 * * ?"
     * </pre>
     */
    @Scheduled(cron = "${stock.expiry.cron:0 30 2 * * ?}")
    public void scheduledScan() {
        log.info("[M07] 定时效期扫描开始");
        try {
            List<Store> stores = storeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Store>()
                            .eq(Store::getDeleted, 0));
            int totalAlerts = 0;
            for (Store s : stores) {
                try {
                    Map<String, Object> r = scan(s.getId());
                    if (r != null && r.get("alertCount") != null) {
                        totalAlerts += ((Number) r.get("alertCount")).intValue();
                    }
                } catch (Exception ex) {
                    log.warn("[M07] 门店 {} 扫描异常: {}", s.getId(), ex.getMessage());
                }
            }
            log.info("[M07] 定时效期扫描完成, 门店 {} 个, 告警 {} 条", stores.size(), totalAlerts);
        } catch (Exception e) {
            log.error("[M07] 定时扫描失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(Long storeId) {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        List<ExpiryAlertRule> rules = expiryAlertRuleMapper.findActiveRules();
        if (rules.isEmpty()) {
            result.put("scannedCount", 0);
            result.put("alertCount", 0);
            return result;
        }
        int maxDays = rules.stream()
                .mapToInt(ExpiryAlertRule::getWarningDays)
                .max().orElse(30);
        List<StockInventory> batches = stockInventoryMapper.findNearExpiry(storeId, maxDays);
        int alertCount = 0;
        for (StockInventory batch : batches) {
            try {
                Long productId = batch.getProductId();
                String category = batch.getCategory();
                ExpiryAlertRule matchedRule = matchRule(rules, productId, category);
                if (matchedRule == null) continue;
                LocalDate expiryDate = batch.getExpiryDate();
                int daysToExpiry = (int) ChronoUnit.DAYS.between(today, expiryDate);
                String level = determineLevel(daysToExpiry, matchedRule);
                ExpiryAlertLog alertLog = new ExpiryAlertLog();
                alertLog.setStoreId(batch.getStoreId());
                alertLog.setInventoryId(batch.getId());
                alertLog.setProductId(productId);
                alertLog.setProductName(batch.getProductName());
                alertLog.setBatchNo(batch.getBatchNo());
                alertLog.setQuantity(batch.getQuantity() == null ? 0 : batch.getQuantity());
                alertLog.setProductionDate(batch.getProductionDate());
                alertLog.setExpiryDate(expiryDate);
                alertLog.setDaysToExpiry(daysToExpiry);
                alertLog.setAlertLevel(level);
                alertLog.setAlertType(daysToExpiry < 0 ? "expired" : "near_expiry");
                alertLog.setAlertMessage(buildMessage(alertLog));
                alertLog.setStatus("pending");
                alertLog.setNotified(0);
                expiryAlertLogMapper.insert(alertLog);
                alertCount++;
            } catch (Exception e) {
                log.error("扫描批次异常: {}", batch, e);
            }
        }
        result.put("scannedCount", batches.size());
        result.put("alertCount", alertCount);
        result.put("scanTime", LocalDateTime.now());
        return result;
    }

    @Override
    public List<StockInventory> findNearExpiry(Long storeId, Integer withinDays) {
        return stockInventoryMapper.findNearExpiry(storeId, withinDays);
    }

    private ExpiryAlertRule matchRule(List<ExpiryAlertRule> rules, Long productId, String category) {
        for (ExpiryAlertRule rule : rules) {
            if (rule.getProductId() != null && rule.getProductId().equals(productId)) {
                return rule;
            }
        }
        for (ExpiryAlertRule rule : rules) {
            if (rule.getCategory() != null && rule.getCategory().equals(category)) {
                return rule;
            }
        }
        for (ExpiryAlertRule rule : rules) {
            if (rule.getCategory() == null && rule.getProductId() == null) {
                return rule;
            }
        }
        return null;
    }

    private String determineLevel(int daysToExpiry, ExpiryAlertRule rule) {
        if (daysToExpiry < 0) return "critical";
        if (daysToExpiry <= rule.getCriticalDays()) return "critical";
        if (daysToExpiry <= rule.getWarningDays()) return "warning";
        return "normal";
    }

    private String buildMessage(ExpiryAlertLog alertLog) {
        String prefix = "expired".equals(alertLog.getAlertType()) ? "已过期" : "即将过期";
        return String.format("%s商品【%s】批号【%s】数量【%d】有效期【%s】剩余【%d天】",
                prefix, alertLog.getProductName(), alertLog.getBatchNo(),
                alertLog.getQuantity(), alertLog.getExpiryDate(), alertLog.getDaysToExpiry());
    }
}

