package com.meiyetec.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.BundleSubItem;
import com.meiyetec.entity.PackageItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * M04 项目组合 — bundle 子项目工具 Service
 *
 * <p>负责：
 * <ul>
 *   <li>解析 crm_package.bundle_config JSON → BundleSubItem 列表</li>
 *   <li>校验子项目（次数/单价/ServiceId 非空）</li>
 *   <li>根据子项目计算 totalTimes / price / bundleItemCount</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageBundleService {

    private final ObjectMapper objectMapper;

    /**
     * 反序列化 bundle_config 字符串
     */
    public List<BundleSubItem> parseBundle(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BundleSubItem.class));
        } catch (JsonProcessingException e) {
            log.warn("[M04] 解析 bundle_config 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 序列化 BundleSubItem 列表
     */
    public String serializeBundle(List<BundleSubItem> items) {
        if (items == null || items.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("[M04] 序列化 bundle_config 失败", e);
            return null;
        }
    }

    /**
     * 子项目整体校验；任一不合法抛运行时异常
     */
    public void validateBundle(List<BundleSubItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("组合套餐至少需要 1 个子项目");
        }
        for (int i = 0; i < items.size(); i++) {
            BundleSubItem it = items.get(i);
            if (it.getServiceId() == null) {
                throw new IllegalArgumentException("子项目 #" + (i + 1) + " serviceId 不能为空");
            }
            if (it.getTimes() == null || it.getTimes() <= 0) {
                throw new IllegalArgumentException("子项目 #" + (i + 1) + " times 必须 > 0");
            }
        }
    }

    /**
     * 根据子项目计算 totalTimes（所有子项目 times 之和）
     */
    public int computeTotalTimes(List<BundleSubItem> items) {
        if (items == null || items.isEmpty()) return 0;
        return items.stream().mapToInt(it -> it.getTimes() == null ? 0 : it.getTimes()).sum();
    }

    /**
     * 根据子项目计算 bundleItemCount（子项目条目数）
     */
    public int computeItemCount(List<BundleSubItem> items) {
        return items == null ? 0 : items.size();
    }

    /**
     * 根据子项目原始单价之和计算 costPrice（若套餐已有 price，则保持原样不变）
     * 仅当套餐 price 为空时计算
     */
    public BigDecimal computeCostPriceFromBundle(List<BundleSubItem> items) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (BundleSubItem it : items) {
            if (it.getUnitPrice() != null) {
                total = total.add(it.getUnitPrice().multiply(
                        BigDecimal.valueOf(it.getTimes() == null ? 1 : it.getTimes())));
            }
        }
        return total;
    }

    /**
     * 用子项目填充 crm_package.bundle_type / bundle_config / bundle_item_count / totalTimes
     */
    public void applyBundleToPackage(PackageItem pkg, List<BundleSubItem> items) {
        boolean isBundle = items != null && !items.isEmpty();
        pkg.setBundleType(isBundle ? 1 : 0);
        pkg.setBundleConfig(serializeBundle(items));
        pkg.setBundleItemCount(computeItemCount(items));
        if (isBundle) {
            // 组合套餐自动汇总 totalTimes
            pkg.setTotalTimes(computeTotalTimes(items));
        }
    }
}
