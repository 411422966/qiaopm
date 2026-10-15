package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.MaterialSubstitute;
import com.meiyetec.entity.StockProduct;
import com.meiyetec.entity.StyleLibrary;
import com.meiyetec.mapper.MaterialSubstituteMapper;
import com.meiyetec.mapper.StockInventoryMapper;
import com.meiyetec.mapper.StockProductMapper;
import com.meiyetec.mapper.StyleLibraryMapper;
import com.meiyetec.service.MaterialSubstituteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialSubstituteServiceImpl extends ServiceImpl<MaterialSubstituteMapper, MaterialSubstitute>
        implements MaterialSubstituteService {

    private final MaterialSubstituteMapper materialSubstituteMapper;
    private final StyleLibraryMapper styleLibraryMapper;
    private final StockInventoryMapper stockInventoryMapper;
    private final StockProductMapper stockProductMapper;
    private final JdbcTemplate jdbc;

    @Override
    public List<MaterialSubstitute> findByMainProduct(Long mainProductId, Long tenantId) {
        return materialSubstituteMapper.findByMainProduct(mainProductId, tenantId);
    }

    @Override
    public List<MaterialSubstitute> findAllWithDetails(Long tenantId) {
        return materialSubstituteMapper.findAllWithDetails(tenantId);
    }

    @Override
    public List<Map<String, Object>> getMaterialsForStyle(Long styleId, Long tenantId) {
        List<Map<String, Object>> resolved = new ArrayList<>();

        // 1. 查款式
        StyleLibrary style = styleLibraryMapper.selectById(styleId);
        if (style == null) {
            log.warn("[MaterialSubstitute] 款式不存在 styleId={}", styleId);
            return resolved;
        }

        // 2. 解析 requiredTools JSON → 工具名称列表
        List<String> toolNames = parseToolNames(style.getRequiredTools());
        if (toolNames.isEmpty()) {
            log.debug("[MaterialSubstitute] 款式 {} requiredTools 为空", styleId);
            return resolved;
        }

        // 3. 按工具名称模糊匹配 stock_product
        for (String toolName : toolNames) {
            List<StockProduct> matched = findProductsByName(toolName, tenantId);
            if (matched.isEmpty()) {
                // 找不到对应产品，记录但不过失
                Map<String, Object> unknown = new LinkedHashMap<>();
                unknown.put("productId", null);
                unknown.put("productName", toolName + "（未匹配到库存商品）");
                unknown.put("quantity", 1);
                unknown.put("unit", "—");
                unknown.put("stockQty", 0);
                unknown.put("isSubstitute", false);
                unknown.put("substituteReason", "库存商品未匹配");
                unknown.put("originalTool", toolName);
                resolved.add(unknown);
                continue;
            }

            // 取第一个匹配结果（实际生产建议精确匹配 tool → product 关联表）
            StockProduct product = matched.get(0);
            int stockQty = getProductStock(product.getId(), tenantId, style.getStoreId());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId", product.getId());
            entry.put("productName", product.getProductName());
            entry.put("productCode", product.getProductCode());
            entry.put("unit", product.getUnit() != null ? product.getUnit() : "瓶");
            entry.put("stockQty", stockQty);
            entry.put("isSubstitute", false);
            entry.put("originalTool", toolName);

            // 4. 检查库存是否足够（threshold = stockThreshold，缺省=1）
            if (stockQty < 1) {
                List<MaterialSubstitute> subs = findByMainProduct(product.getId(), tenantId);
                Optional<MaterialSubstitute> available = subs.stream()
                        .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                        .findFirst();
                if (available.isPresent()) {
                    MaterialSubstitute sub = available.get();
                    entry.put("isSubstitute", true);
                    entry.put("substituteProductId", sub.getSubstituteProductId());
                    entry.put("substituteProductName", sub.getSubstituteProductName());
                    entry.put("substituteRatio", sub.getSubstituteRatio());
                    entry.put("substituteReason",
                            "主耗材「" + product.getProductName() + "」库存不足(" + stockQty + ")，已启用替代方案");
                } else {
                    entry.put("substituteReason", "库存不足且无替代方案，请及时补货");
                }
            }

            entry.put("quantity", 1);
            resolved.add(entry);
        }

        return resolved;
    }

    /** 解析 requiredTools JSON（支持 ["甲油胶","封层"] 或 "甲油胶,封层" 两种格式） */
    private List<String> parseToolNames(String requiredTools) {
        if (requiredTools == null || requiredTools.isBlank()) return Collections.emptyList();
        requiredTools = requiredTools.trim();
        if (requiredTools.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper JSON =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                List<String> arr = JSON.readValue(requiredTools, List.class);
                return arr.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
            } catch (Exception e) {
                log.warn("[MaterialSubstitute] requiredTools JSON 解析失败: {}", requiredTools);
            }
        }
        return Arrays.stream(requiredTools.split("[,，]"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** 按产品名称模糊匹配（用于 requiredTools → stock_product 映射） */
    private List<StockProduct> findProductsByName(String toolName, Long tenantId) {
        LambdaQueryWrapper<StockProduct> q = new LambdaQueryWrapper<>();
        q.eq(StockProduct::getTenantId, tenantId)
         .and(w -> w.like(StockProduct::getProductName, toolName)
                      .or()
                      .like(StockProduct::getProductCode, toolName))
         .eq(StockProduct::getDeleted, 0)
         .last("LIMIT 1");
        return stockProductMapper.selectList(q);
    }

    /** 查某产品的总库存（sum of stock_inventory） */
    private int getProductStock(Long productId, Long tenantId, Long storeId) {
        try {
            String sql;
            List<Object> args;
            if (storeId != null) {
                sql = "SELECT IFNULL(SUM(quantity), 0) FROM stock_inventory " +
                      "WHERE product_id = ? AND store_id = ? AND deleted = 0";
                args = List.of(productId, storeId);
            } else {
                sql = "SELECT IFNULL(SUM(quantity), 0) FROM stock_inventory " +
                      "WHERE product_id = ? AND deleted = 0";
                args = List.of(productId);
            }
            Long qty = jdbc.queryForObject(sql, Long.class, args.toArray());
            return qty != null ? qty.intValue() : 0;
        } catch (Exception e) {
            log.debug("[MaterialSubstitute] 查库存失败 productId={}: {}", productId, e.getMessage());
            return 0;
        }
    }
}
