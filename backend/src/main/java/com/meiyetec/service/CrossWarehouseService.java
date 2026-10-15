package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.3：跨仓预警
 *
 * <p>在 WmsService 已有基础上补：
 * <ul>
 *   <li>跨仓库存视图（按 SKU 看所有仓库分布）</li>
 *   <li>不平衡预警（一仓低 + 另一仓高 → 建议调拨）</li>
 *   <li>智能调拨建议（基于 BALANCE-V1 算法）</li>
 *   <li>跨仓过期预警（90/60/30 分级）</li>
 *   <li>仓库级阈值管理</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/wms/cross")
@RequiredArgsConstructor
public class CrossWarehouseService {

    private final WmsWarehouseThresholdMapper thresholdMapper;
    private final WmsCrossWarehouseAlertMapper alertMapper;
    private final WmsTransferSuggestionMapper suggestionMapper;
    private final WmsExpiryAlertMapper expiryMapper;
    private final WmsWarehouseMapper warehouseMapper;
    private final WmsStockTransferMapper transferMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 跨仓库存视图（按 SKU 看所有仓库分布）
    // ====================================================================

    @GetMapping("/inventory/view")
    public Result<Map<String, Object>> crossWarehouseInventoryView(
            @RequestParam(required = false) Long productId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT i.product_id, p.product_code, p.product_name, p.unit, p.min_stock, p.max_stock, " +
            "       i.warehouse_id, w.warehouse_code, w.warehouse_name, w.warehouse_type, " +
            "       SUM(i.quantity) AS total_qty " +
            "FROM stock_inventory i " +
            "JOIN stock_product p ON i.product_id = p.id " +
            "JOIN wms_warehouse w ON i.warehouse_id = w.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND w.deleted = 0 " +
            "  AND i.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (productId != null) { sql.append("AND i.product_id = ? "); args.add(productId); }
        sql.append("GROUP BY i.product_id, i.warehouse_id " +
                   "ORDER BY i.product_id, i.warehouse_id");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 按 productId 聚合
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Long pid = ((Number) r.get("product_id")).longValue();
            Map<String, Object> product = grouped.computeIfAbsent(pid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", pid);
                m.put("productCode", r.get("product_code"));
                m.put("productName", r.get("product_name"));
                m.put("unit", r.get("unit"));
                m.put("minStock", r.get("min_stock"));
                m.put("maxStock", r.get("max_stock"));
                m.put("warehouses", new ArrayList<>());
                m.put("totalQty", 0);
                return m;
            });
            Map<String, Object> wh = new LinkedHashMap<>();
            wh.put("warehouseId", r.get("warehouse_id"));
            wh.put("warehouseCode", r.get("warehouse_code"));
            wh.put("warehouseName", r.get("warehouse_name"));
            wh.put("warehouseType", r.get("warehouse_type"));
            int qty = ((Number) r.get("total_qty")).intValue();
            wh.put("quantity", qty);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> warehouses = (List<Map<String, Object>>) product.get("warehouses");
            warehouses.add(wh);
            product.put("totalQty", ((Number) product.get("totalQty")).intValue() + qty);
        }
        return Result.success(Map.of("products", new ArrayList<>(grouped.values())));
    }

    // ====================================================================
    // 2. 跨仓不平衡预警（核心算法）
    // ====================================================================

    /**
     * 跨仓不平衡扫描：发现一仓低 + 一仓高的 SKU → 生成调拨建议
     */
    @PostMapping("/scan/imbalance")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> scanImbalance(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();

        // 清空旧的待处理预警
        alertMapper.delete(new LambdaQueryWrapper<WmsCrossWarehouseAlert>()
                .eq(WmsCrossWarehouseAlert::getStatus, 1)
                .eq(WmsCrossWarehouseAlert::getTenantId, tenantId));

        // 取所有仓库的 SKU 库存
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT i.product_id, p.product_code, p.product_name, p.min_stock, p.max_stock, " +
            "       i.warehouse_id, w.warehouse_name, " +
            "       SUM(i.quantity) AS qty " +
            "FROM stock_inventory i " +
            "JOIN stock_product p ON i.product_id = p.id " +
            "JOIN wms_warehouse w ON i.warehouse_id = w.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND w.deleted = 0 " +
            "  AND i.tenant_id = ? " +
            "GROUP BY i.product_id, i.warehouse_id",
            tenantId);

        // 按 SKU 分组
        Map<Long, List<Map<String, Object>>> byProduct = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> productInfo = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long pid = ((Number) r.get("product_id")).longValue();
            byProduct.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
            productInfo.putIfAbsent(pid, Map.of(
                    "code", r.get("product_code"),
                    "name", r.get("product_name"),
                    "min", r.get("min_stock"),
                    "max", r.get("max_stock")));
        }

        int imbalanceCount = 0;
        List<WmsTransferSuggestion> suggestions = new ArrayList<>();

        for (Map.Entry<Long, List<Map<String, Object>>> e : byProduct.entrySet()) {
            Long pid = e.getKey();
            List<Map<String, Object>> whs = e.getValue();
            if (whs.size() < 2) continue;   // 单仓无法跨仓调拨

            // 排序：低 → 高
            whs.sort(Comparator.comparingInt(r -> ((Number) r.get("qty")).intValue()));

            Map<String, Object> lowest = whs.get(0);
            Map<String, Object> highest = whs.get(whs.size() - 1);

            int lowQty = ((Number) lowest.get("qty")).intValue();
            int highQty = ((Number) highest.get("qty")).intValue();
            int minStock = ((Number) lowest.get("min_stock")).intValue();
            int maxStock = ((Number) highest.get("max_stock")).intValue();

            // 触发条件：低仓 < min 且高仓 > min × 2
            if (lowQty < minStock && highQty > minStock * 2) {
                Long lowWhId = ((Number) lowest.get("warehouse_id")).longValue();
                Long highWhId = ((Number) highest.get("warehouse_id")).longValue();
                int deficit = minStock - lowQty;
                int surplus = highQty - Math.max(maxStock, minStock * 2);
                int suggestedQty = Math.min(deficit, surplus / 2);   // 不全调走，保留 1/2 安全

                if (suggestedQty <= 0) continue;

                String severity = suggestedQty >= deficit * 0.8 ? "HIGH" : "MEDIUM";

                // 生成预警
                WmsCrossWarehouseAlert a = new WmsCrossWarehouseAlert();
                a.setTenantId(tenantId);
                a.setProductId(pid);
                a.setProductName((String) productInfo.get(pid).get("name"));
                a.setFromWarehouseId(highWhId);
                a.setFromWarehouseName((String) highest.get("warehouse_name"));
                a.setFromQuantity(highQty);
                a.setToWarehouseId(lowWhId);
                a.setToWarehouseName((String) lowest.get("warehouse_name"));
                a.setToQuantity(lowQty);
                a.setAlertType("IMBALANCE");
                a.setSeverity(severity);
                a.setRecommendedQty(suggestedQty);
                a.setAlertReason(String.format(
                        "%s 仓库库存 %d 低于安全库存 %d，%s 仓库库存 %d 超过 %d，建议调拨 %d",
                        lowest.get("warehouse_name"), lowQty, minStock,
                        highest.get("warehouse_name"), highQty, minStock * 2, suggestedQty));
                a.setSuggestedAction(String.format(
                        "生成调拨单：从 %s → %s，数量 %d，备注：跨仓不平衡自动调拨",
                        highest.get("warehouse_name"), lowest.get("warehouse_name"), suggestedQty));
                a.setStatus(1);
                alertMapper.insert(a);
                imbalanceCount++;

                // 生成调拨建议
                WmsTransferSuggestion s = new WmsTransferSuggestion();
                s.setTenantId(tenantId);
                s.setProductId(pid);
                s.setProductName((String) productInfo.get(pid).get("name"));
                s.setFromWarehouseId(highWhId);
                s.setFromWarehouseName((String) highest.get("warehouse_name"));
                s.setToWarehouseId(lowWhId);
                s.setToWarehouseName((String) lowest.get("warehouse_name"));
                s.setSuggestedQty(suggestedQty);
                s.setPriority(severity);
                s.setReason(a.getAlertReason());
                s.setAlgorithmVersion("BALANCE-V1");
                s.setAccepted(0);
                suggestionMapper.insert(s);
                suggestions.add(s);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scannedProducts", byProduct.size());
        data.put("imbalanceCount", imbalanceCount);
        data.put("suggestionsGenerated", suggestions.size());
        data.put("algorithm", "BALANCE-V1");
        return Result.success("扫描完成", data);
    }

    // ====================================================================
    // 3. 智能调拨建议（基于阈值）
    // ====================================================================

    @PostMapping("/suggestion/run")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> generateSuggestions(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 复用 scanImbalance 逻辑
        return scanImbalance(body);
    }

    @GetMapping("/suggestion/page")
    public Result<List<WmsTransferSuggestion>> suggestionPage(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer accepted,
            @RequestParam(defaultValue = "50") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<WmsTransferSuggestion> qw = new LambdaQueryWrapper<>();
        qw.eq(WmsTransferSuggestion::getTenantId, tenantId);
        if (productId != null) qw.eq(WmsTransferSuggestion::getProductId, productId);
        if (accepted != null) qw.eq(WmsTransferSuggestion::getAccepted, accepted);
        qw.orderByDesc(WmsTransferSuggestion::getId).last("LIMIT " + limit);
        return Result.success(suggestionMapper.selectList(qw));
    }

    /**
     * 采纳建议 → 生成调拨单
     */
    @PostMapping("/suggestion/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> acceptSuggestion(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        WmsTransferSuggestion s = suggestionMapper.selectById(id);
        if (s == null) return Result.error(BizCode.NOT_FOUND, "建议不存在");
        if (s.getAccepted() != null && s.getAccepted() == 1)
            return Result.error(BizCode.CONFLICT, "建议已被采纳");

        // 生成调拨单
        WmsStockTransfer t = new WmsStockTransfer();
        t.setTenantId(tenantId);
        t.setProductId(s.getProductId());
        t.setFromWarehouseId(s.getFromWarehouseId());
        t.setToWarehouseId(s.getToWarehouseId());
        t.setQuantity(s.getSuggestedQty());
        t.setTransferNo("TR" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        t.setStatus("PENDING");
        t.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        t.setRemark("采纳智能调拨建议 #" + id);
        transferMapper.insert(t);

        // 标记已采纳
        s.setAccepted(1);
        s.setAcceptedAt(LocalDateTime.now());
        s.setAcceptedBy((String) body.getOrDefault("operatorName", "system"));
        s.setResultTransferId(t.getId());
        suggestionMapper.updateById(s);

        // 关闭预警
        WmsCrossWarehouseAlert a = alertMapper.selectOne(new LambdaQueryWrapper<WmsCrossWarehouseAlert>()
                .eq(WmsCrossWarehouseAlert::getFromWarehouseId, s.getFromWarehouseId())
                .eq(WmsCrossWarehouseAlert::getToWarehouseId, s.getToWarehouseId())
                .eq(WmsCrossWarehouseAlert::getProductId, s.getProductId())
                .eq(WmsCrossWarehouseAlert::getStatus, 1)
                .last("LIMIT 1"));
        if (a != null) {
            a.setStatus(2);
            a.setRelatedTransferId(t.getId());
            a.setHandledAt(LocalDateTime.now());
            a.setHandleRemark("已采纳建议生成调拨单 #" + t.getId());
            alertMapper.updateById(a);
        }

        return Result.success("已采纳，调拨单已生成", Map.of(
                "transferId", t.getId(),
                "transferNo", t.getTransferNo(),
                "quantity", t.getQuantity()));
    }

    // ====================================================================
    // 4. 跨仓过期预警
    // ====================================================================

    @PostMapping("/expiry/scan")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> scanExpiry(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();

        // 清空旧预警
        expiryMapper.delete(new LambdaQueryWrapper<WmsExpiryAlert>()
                .eq(WmsExpiryAlert::getTenantId, tenantId));

        // 扫描库存批次（有过期日期的）
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT i.id AS inv_id, i.warehouse_id, i.product_id, i.batch_no, i.quantity, i.expiry_date, " +
            "       p.product_code, p.product_name, " +
            "       w.warehouse_name " +
            "FROM stock_inventory i " +
            "JOIN stock_product p ON i.product_id = p.id " +
            "JOIN wms_warehouse w ON i.warehouse_id = w.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND w.deleted = 0 " +
            "  AND i.tenant_id = ? AND i.expiry_date IS NOT NULL AND i.quantity > 0",
            tenantId);

        int expired = 0, expiring30 = 0, expiring60 = 0, expiring90 = 0;
        for (Map<String, Object> r : rows) {
            LocalDate exp = LocalDate.parse(r.get("expiry_date").toString());
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, exp);
            String level;
            if (days < 0) {
                level = "EXPIRED"; expired++;
            } else if (days <= 30) {
                level = "EXPIRING_30"; expiring30++;
            } else if (days <= 60) {
                level = "EXPIRING_60"; expiring60++;
            } else if (days <= 90) {
                level = "EXPIRING_90"; expiring90++;
            } else {
                continue;       // 90 天以上不预警
            }

            WmsExpiryAlert e = new WmsExpiryAlert();
            e.setTenantId(tenantId);
            e.setWarehouseId(((Number) r.get("warehouse_id")).longValue());
            e.setWarehouseName((String) r.get("warehouse_name"));
            e.setProductId(((Number) r.get("product_id")).longValue());
            e.setProductName((String) r.get("product_name"));
            e.setBatchNo((String) r.get("batch_no"));
            e.setQuantity(((Number) r.get("quantity")).intValue());
            e.setExpiryDate(exp);
            e.setDaysRemaining((int) days);
            e.setAlertLevel(level);
            expiryMapper.insert(e);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expired", expired);
        data.put("expiring30", expiring30);
        data.put("expiring60", expiring60);
        data.put("expiring90", expiring90);
        data.put("total", expired + expiring30 + expiring60 + expiring90);
        return Result.success("扫描完成", data);
    }

    @GetMapping("/expiry/page")
    public Result<List<Map<String, Object>>> expiryPage(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(defaultValue = "100") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT warehouse_name, product_name, batch_no, quantity, expiry_date, days_remaining, alert_level " +
            "FROM wms_expiry_alert WHERE tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (alertLevel != null) { sql.append("AND alert_level = ? "); args.add(alertLevel); }
        sql.append("ORDER BY days_remaining ASC LIMIT ?");
        args.add(limit);
        return Result.success(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    // ====================================================================
    // 5. 仓库级阈值管理
    // ====================================================================

    @PostMapping("/threshold")
    public Result<WmsWarehouseThreshold> setThreshold(@RequestBody WmsWarehouseThreshold t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        t.setTenantId(tenantId);
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getMaxStock() == null) t.setMaxStock(999999);
        if (t.getMinStock() == null) t.setMinStock(0);
        if (t.getSafetyDays() == null) t.setSafetyDays(7);

        // 存在则更新
        WmsWarehouseThreshold exist = thresholdMapper.selectOne(
                new LambdaQueryWrapper<WmsWarehouseThreshold>()
                        .eq(WmsWarehouseThreshold::getTenantId, tenantId)
                        .eq(WmsWarehouseThreshold::getWarehouseId, t.getWarehouseId())
                        .eq(WmsWarehouseThreshold::getProductId, t.getProductId()));
        if (exist != null) {
            exist.setMinStock(t.getMinStock());
            exist.setMaxStock(t.getMaxStock());
            exist.setSafetyDays(t.getSafetyDays());
            exist.setEnabled(t.getEnabled());
            thresholdMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        thresholdMapper.insert(t);
        return Result.success("已创建", t);
    }

    @GetMapping("/threshold/list")
    public Result<List<WmsWarehouseThreshold>> listThresholds(
            @RequestParam(required = false) Long warehouseId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<WmsWarehouseThreshold> qw = new LambdaQueryWrapper<>();
        qw.eq(WmsWarehouseThreshold::getTenantId, tenantId);
        if (warehouseId != null) qw.eq(WmsWarehouseThreshold::getWarehouseId, warehouseId);
        return Result.success(thresholdMapper.selectList(qw));
    }

    // ====================================================================
    // 6. 预警处理
    // ====================================================================

    @GetMapping("/alert/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> alertPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String alertType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, product_id, product_name, from_warehouse_name, from_quantity, " +
            "       to_warehouse_name, to_quantity, alert_type, severity, recommended_qty, " +
            "       alert_reason, suggested_action, status, related_transfer_id, create_time " +
            "FROM wms_cross_warehouse_alert WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (severity != null) { sql.append("AND severity = ? "); args.add(severity); }
        if (alertType != null) { sql.append("AND alert_type = ? "); args.add(alertType); }
        sql.append("ORDER BY FIELD(severity, 'HIGH', 'MEDIUM', 'LOW'), id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM wms_cross_warehouse_alert WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>(args.subList(0, args.size() - 2));
        if (status != null) csql.append("AND status = ? ");
        if (severity != null) csql.append("AND severity = ? ");
        if (alertType != null) csql.append("AND alert_type = ? ");
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(records);
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/alert/{id}/ignore")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> ignoreAlert(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        WmsCrossWarehouseAlert a = alertMapper.selectById(id);
        if (a == null) return Result.error(BizCode.NOT_FOUND, "预警不存在");
        a.setStatus(3);
        a.setHandledAt(LocalDateTime.now());
        a.setHandleRemark((String) body.getOrDefault("reason", "忽略"));
        alertMapper.updateById(a);
        return Result.success("已忽略", null);
    }

    // ====================================================================
    // 7. 概览
    // ====================================================================

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 待处理预警
        for (String sev : new String[]{"HIGH", "MEDIUM", "LOW"}) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wms_cross_warehouse_alert " +
                "WHERE deleted=0 AND tenant_id=? AND status=1 AND severity=?",
                Long.class, tenantId, sev);
            data.put(sev.toLowerCase() + "Alerts", cnt == null ? 0 : cnt);
        }

        // 仓库数量
        Long warehouseCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_warehouse WHERE deleted=0 AND tenant_id=?",
            Long.class, tenantId);
        data.put("warehouseCount", warehouseCount == null ? 0 : warehouseCount);

        // 待调拨建议
        Long pendingSugg = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_transfer_suggestion " +
            "WHERE deleted=0 AND tenant_id=? AND accepted=0",
            Long.class, tenantId);
        data.put("pendingSuggestions", pendingSugg == null ? 0 : pendingSugg);

        // 过期预警
        for (String level : new String[]{"EXPIRED", "EXPIRING_30", "EXPIRING_60", "EXPIRING_90"}) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wms_expiry_alert WHERE tenant_id=? AND alert_level=?",
                Long.class, tenantId, level);
            data.put(level.toLowerCase() + "Count", cnt == null ? 0 : cnt);
        }
        return Result.success(data);
    }
}