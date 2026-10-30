package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.service.CrossStoreInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * M12-02 跨店库存统一调度实现
 *
 * <p>在 WmsService 已有基础上深化：
 * <ul>
 *   <li>跨店聚合视图（按 SKU 看全门店分布）</li>
 *   <li>门店间调拨完整工作流（创建→审批→发货→收货→取消）</li>
 *   <li>智能调度算法（BALANCE-V2）：基于日均销量 + 安全库存</li>
 *   <li>调度仪表盘：调度成功率 / 平均耗时 / 在途库存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossStoreInventoryServiceImpl implements CrossStoreInventoryService {

    private final WmsStockTransferMapper transferMapper;
    private final WmsTransferSuggestionMapper suggestionMapper;
    private final WmsWarehouseMapper warehouseMapper;
    private final WmsCrossWarehouseAlertMapper alertMapper;
    private final StockProductMapper productMapper;
    private final StockInventoryMapper inventoryMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ============================================================
    // 1. 跨店聚合视图
    // ============================================================

    @Override
    public Map<String, Object> aggregateInventory(Long tenantId, Long productId, String keyword,
                                                  String alertOnly, Integer limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT i.product_id, p.product_code, p.product_name, p.unit, p.category, " +
            "       p.min_stock, p.max_stock, p.cost_price, p.sale_price, " +
            "       i.store_id, s.store_name, i.warehouse_id, w.warehouse_name, " +
            "       SUM(i.quantity) AS store_qty " +
            "FROM stock_inventory i " +
            "JOIN stock_product p ON i.product_id = p.id " +
            "LEFT JOIN pos_store s ON i.store_id = s.id " +
            "LEFT JOIN wms_warehouse w ON i.warehouse_id = w.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 " +
            "  AND p.status = 1 " +
            "  AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(tenantId);
        if (productId != null) {
            sql.append("AND i.product_id = ? ");
            args.add(productId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (p.product_code LIKE ? OR p.product_name LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append("GROUP BY i.product_id, i.store_id, i.warehouse_id " +
                   "ORDER BY i.product_id, i.store_id LIMIT ?");
        args.add(limit * 10);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Long pid = ((Number) r.get("product_id")).longValue();
            int minStock = r.get("min_stock") == null ? 0 : ((Number) r.get("min_stock")).intValue();
            int maxStock = r.get("max_stock") == null ? 999999 : ((Number) r.get("max_stock")).intValue();
            int storeQty = ((Number) r.get("store_qty")).intValue();

            Map<String, Object> product = grouped.computeIfAbsent(pid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", pid);
                m.put("productCode", r.get("product_code"));
                m.put("productName", r.get("product_name"));
                m.put("unit", r.get("unit"));
                m.put("category", r.get("category"));
                m.put("minStock", minStock);
                m.put("maxStock", maxStock);
                m.put("stores", new ArrayList<>());
                m.put("totalQty", 0);
                m.put("storeCount", 0);
                m.put("alertStores", 0);
                m.put("overloadStores", 0);
                m.put("balance", "OK");
                return m;
            });

            Map<String, Object> store = new LinkedHashMap<>();
            store.put("storeId", r.get("store_id"));
            store.put("storeName", r.get("store_name"));
            store.put("warehouseId", r.get("warehouse_id"));
            store.put("warehouseName", r.get("warehouse_name"));
            store.put("quantity", storeQty);
            String storeAlert;
            if (storeQty <= 0) storeAlert = "OUT";
            else if (storeQty < minStock) storeAlert = "LOW";
            else if (storeQty > maxStock) storeAlert = "OVER";
            else storeAlert = "OK";
            store.put("alert", storeAlert);
            store.put("minStock", minStock);
            store.put("maxStock", maxStock);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stores = (List<Map<String, Object>>) product.get("stores");
            stores.add(store);

            product.put("totalQty", ((Number) product.get("totalQty")).intValue() + storeQty);
            product.put("storeCount", ((Integer) product.get("storeCount")) + 1);
            if ("OUT".equals(storeAlert) || "LOW".equals(storeAlert)) {
                product.put("alertStores", ((Integer) product.get("alertStores")) + 1);
            }
            if ("OVER".equals(storeAlert)) {
                product.put("overloadStores", ((Integer) product.get("overloadStores")) + 1);
            }
        }

        List<Map<String, Object>> products = new ArrayList<>(grouped.values());
        for (Map<String, Object> p : products) {
            int totalQty = ((Number) p.get("totalQty")).intValue();
            int storeCount = ((Integer) p.get("storeCount"));
            int minStock = ((Number) p.get("minStock")).intValue();
            int alertStores = ((Integer) p.get("alertStores"));
            int overloadStores = ((Integer) p.get("overloadStores"));

            String balance;
            if (alertStores > 0 && overloadStores > 0) balance = "IMBALANCE";
            else if (alertStores > 0) balance = "SHORTAGE";
            else if (overloadStores > 0) balance = "OVERLOAD";
            else balance = "OK";
            p.put("balance", balance);
            p.put("safetyTotal", storeCount * minStock);
            p.put("safetyGap", storeCount * minStock - totalQty);
        }

        if ("true".equalsIgnoreCase(alertOnly) || "1".equals(alertOnly)) {
            products.removeIf(p -> "OK".equals(p.get("balance")));
        }

        if (products.size() > limit) {
            products = products.subList(0, limit);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("products", products);
        data.put("total", products.size());
        return data;
    }

    // ============================================================
    // 2. 智能调度：BALANCE-V2 算法
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanSchedule(Long tenantId, Map<String, Object> body) {
        suggestionMapper.delete(new LambdaQueryWrapper<WmsTransferSuggestion>()
                .eq(WmsTransferSuggestion::getAccepted, 0)
                .eq(WmsTransferSuggestion::getTenantId, tenantId));

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT i.product_id, p.product_code, p.product_name, " +
            "       p.min_stock, p.max_stock, " +
            "       i.store_id, s.store_name, " +
            "       SUM(i.quantity) AS qty " +
            "FROM stock_inventory i " +
            "JOIN stock_product p ON i.product_id = p.id " +
            "LEFT JOIN pos_store s ON i.store_id = s.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND p.status = 1 " +
            "  AND i.tenant_id = ? AND p.tenant_id = ? " +
            "  AND i.store_id IS NOT NULL " +
            "GROUP BY i.product_id, i.store_id",
            tenantId, tenantId);

        Map<Long, List<Map<String, Object>>> byProduct = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Long pid = ((Number) r.get("product_id")).longValue();
            byProduct.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
        }

        int suggestionCount = 0;
        int imbalanceCount = 0;

        for (Map.Entry<Long, List<Map<String, Object>>> e : byProduct.entrySet()) {
            List<Map<String, Object>> stores = e.getValue();
            if (stores.size() < 2) continue;

            List<Map<String, Object>> lowStores = new ArrayList<>();
            List<Map<String, Object>> highStores = new ArrayList<>();
            for (Map<String, Object> s : stores) {
                int qty = ((Number) s.get("qty")).intValue();
                int min = s.get("min_stock") == null ? 0 : ((Number) s.get("min_stock")).intValue();
                int max = s.get("max_stock") == null ? 999999 : ((Number) s.get("max_stock")).intValue();

                if (qty < min) lowStores.add(s);
                else if (qty > max * 1.5) highStores.add(s);
            }

            if (lowStores.isEmpty() || highStores.isEmpty()) continue;
            imbalanceCount++;

            lowStores.sort(Comparator.comparingInt(s ->
                    ((Number) s.get("min_stock")).intValue() - ((Number) s.get("qty")).intValue()));
            highStores.sort(Comparator.comparingInt(s ->
                    ((Number) s.get("qty")).intValue() * -1));

            int lowIdx = 0, highIdx = 0;
            while (lowIdx < lowStores.size() && highIdx < highStores.size()) {
                Map<String, Object> low = lowStores.get(lowIdx);
                Map<String, Object> high = highStores.get(highIdx);

                int lowQty = ((Number) low.get("qty")).intValue();
                int minLow = ((Number) low.get("min_stock")).intValue();
                int highQty = ((Number) high.get("qty")).intValue();
                int maxHigh = ((Number) high.get("max_stock")).intValue();

                int deficit = (int) Math.ceil(minLow * 1.5) - lowQty;
                int surplus = highQty - (int) Math.ceil(maxHigh * 1.2);
                int suggestedQty = Math.min(deficit, surplus);

                if (suggestedQty <= 0) {
                    if (surplus <= 0) highIdx++;
                    else lowIdx++;
                    continue;
                }

                WmsTransferSuggestion s = new WmsTransferSuggestion();
                s.setTenantId(tenantId);
                s.setProductId(e.getKey());
                s.setProductName((String) low.get("product_name"));
                Long fromWhId = findOrInferWarehouseId(((Number) high.get("store_id")).longValue());
                Long toWhId = findOrInferWarehouseId(((Number) low.get("store_id")).longValue());
                s.setFromWarehouseId(fromWhId);
                s.setFromWarehouseName((String) high.get("store_name"));
                s.setToWarehouseId(toWhId);
                s.setToWarehouseName((String) low.get("store_name"));
                s.setSuggestedQty(suggestedQty);
                String priority = deficit >= minLow ? "HIGH"
                        : deficit >= minLow * 0.5 ? "MEDIUM" : "LOW";
                s.setPriority(priority);
                s.setReason(String.format(
                        "%s 库存 %d 低于安全 %d；%s 余量 %d。调拨 %d 可平衡全店库存",
                        low.get("store_name"), lowQty, minLow,
                        high.get("store_name"), highQty, suggestedQty));
                s.setAlgorithmVersion("BALANCE-V2");
                s.setAccepted(0);
                suggestionMapper.insert(s);
                suggestionCount++;

                low.put("qty", lowQty + suggestedQty);
                high.put("qty", highQty - suggestedQty);

                int newLowQty = lowQty + suggestedQty;
                if (newLowQty >= Math.ceil(minLow * 1.5)) lowIdx++;
                int newHighQty = highQty - suggestedQty;
                if (newHighQty <= Math.ceil(maxHigh * 1.2)) highIdx++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scannedProducts", byProduct.size());
        data.put("imbalanceProducts", imbalanceCount);
        data.put("suggestionsGenerated", suggestionCount);
        data.put("algorithm", "BALANCE-V2");
        return data;
    }

    // ============================================================
    // 3. 采纳建议
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> acceptSuggestion(Long tenantId, Long suggestionId, Map<String, Object> body) {
        WmsTransferSuggestion s = suggestionMapper.selectById(suggestionId);
        if (s == null) throw new BusinessException(BizCode.NOT_FOUND, "建议不存在");
        if (s.getAccepted() != null && s.getAccepted() == 1)
            throw new BusinessException(BizCode.CONFLICT, "建议已被采纳");

        Integer available = jdbc.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM stock_inventory " +
            "WHERE deleted=0 AND tenant_id=? AND warehouse_id=? AND product_id=?",
            Integer.class, tenantId, s.getFromWarehouseId(), s.getProductId());
        if (available == null || available < s.getSuggestedQty()) {
            throw new BusinessException(BizCode.STOCK_NOT_ENOUGH,
                    "源仓库存不足（可用 " + (available == null ? 0 : available)
                            + "，需 " + s.getSuggestedQty() + "）");
        }

        WmsStockTransfer t = new WmsStockTransfer();
        t.setTenantId(tenantId);
        t.setProductId(s.getProductId());
        t.setProductName(s.getProductName());
        t.setFromWarehouseId(s.getFromWarehouseId());
        t.setToWarehouseId(s.getToWarehouseId());
        t.setFromStoreId(parseStoreIdFromWarehouse(s.getFromWarehouseId()));
        t.setToStoreId(parseStoreIdFromWarehouse(s.getToWarehouseId()));
        t.setQuantity(s.getSuggestedQty());
        t.setTransferNo(generateTransferNo());
        t.setStatus("PENDING");
        t.setOperatorName(body != null && body.get("operatorName") != null
                ? (String) body.get("operatorName") : "system");
        t.setRemark("采纳智能调度建议 #" + suggestionId + "（" + s.getAlgorithmVersion() + "）");
        transferMapper.insert(t);

        s.setAccepted(1);
        s.setAcceptedAt(LocalDateTime.now());
        s.setAcceptedBy((String) body.getOrDefault("operatorName", "system"));
        s.setResultTransferId(t.getId());
        suggestionMapper.updateById(s);

        WmsCrossWarehouseAlert alert = alertMapper.selectOne(
                new LambdaQueryWrapper<WmsCrossWarehouseAlert>()
                .eq(WmsCrossWarehouseAlert::getFromWarehouseId, s.getFromWarehouseId())
                .eq(WmsCrossWarehouseAlert::getToWarehouseId, s.getToWarehouseId())
                .eq(WmsCrossWarehouseAlert::getProductId, s.getProductId())
                .eq(WmsCrossWarehouseAlert::getStatus, 1)
                .last("LIMIT 1"));
        if (alert != null) {
            alert.setStatus(2);
            alert.setRelatedTransferId(t.getId());
            alert.setHandledAt(LocalDateTime.now());
            alert.setHandleRemark("已采纳建议 #" + suggestionId);
            alertMapper.updateById(alert);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transferId", t.getId());
        data.put("transferNo", t.getTransferNo());
        data.put("quantity", t.getQuantity());
        return data;
    }

    // ============================================================
    // 4. 建议分页
    // ============================================================

    @Override
    public Map<String, Object> suggestionPage(Long tenantId, int pageNum, int pageSize,
                                              Integer accepted, String priority) {
        LambdaQueryWrapper<WmsTransferSuggestion> qw = new LambdaQueryWrapper<>();
        qw.eq(WmsTransferSuggestion::getTenantId, tenantId);
        if (accepted != null) qw.eq(WmsTransferSuggestion::getAccepted, accepted);
        if (priority != null && !priority.isEmpty()) qw.eq(WmsTransferSuggestion::getPriority, priority);
        qw.orderByAsc(WmsTransferSuggestion::getPriority)
          .orderByDesc(WmsTransferSuggestion::getId);
        long total = suggestionMapper.selectCount(qw);
        List<WmsTransferSuggestion> records = suggestionMapper.selectList(qw);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("total", total);
        page.put("records", records);
        page.put("current", pageNum);
        page.put("size", pageSize);
        return page;
    }

    // ============================================================
    // 5. 创建调拨单
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockTransfer createTransfer(Long tenantId, WmsStockTransfer t) {
        if (t.getProductId() == null) throw new BusinessException(BizCode.BAD_REQUEST, "productId 必填");
        if (t.getFromWarehouseId() == null || t.getToWarehouseId() == null)
            throw new BusinessException(BizCode.BAD_REQUEST, "源/目标仓库必填");
        if (t.getQuantity() == null || t.getQuantity() <= 0)
            throw new BusinessException(BizCode.BAD_REQUEST, "调拨数量必须 > 0");
        if (t.getFromWarehouseId().equals(t.getToWarehouseId()))
            throw new BusinessException(BizCode.BAD_REQUEST, "源/目标仓库不能相同");

        Integer available = jdbc.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM stock_inventory " +
            "WHERE deleted=0 AND tenant_id=? AND warehouse_id=? AND product_id=?",
            Integer.class, tenantId, t.getFromWarehouseId(), t.getProductId());
        if (available == null || available < t.getQuantity()) {
            throw new BusinessException(BizCode.STOCK_NOT_ENOUGH,
                    "源仓库存不足（可用 " + (available == null ? 0 : available)
                            + "，需 " + t.getQuantity() + "）");
        }

        t.setTenantId(tenantId);
        if (t.getTransferNo() == null || t.getTransferNo().isEmpty()) {
            t.setTransferNo(generateTransferNo());
        }
        t.setStatus("PENDING");
        if (t.getOperatorName() == null) t.setOperatorName("admin");
        transferMapper.insert(t);
        return t;
    }

    // ============================================================
    // 6. 调拨单分页
    // ============================================================

    @Override
    public Map<String, Object> transferPage(Long tenantId, int pageNum, int pageSize,
                                           String status, String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT t.*, p.product_code, " +
            "       fw.warehouse_name AS from_warehouse_name, fw.warehouse_type AS from_warehouse_type, " +
            "       tw.warehouse_name AS to_warehouse_name, tw.warehouse_type AS to_warehouse_type, " +
            "       fs.store_name AS from_store_name, ts.store_name AS to_store_name " +
            "FROM wms_stock_transfer t " +
            "LEFT JOIN stock_product p ON t.product_id = p.id " +
            "LEFT JOIN wms_warehouse fw ON t.from_warehouse_id = fw.id " +
            "LEFT JOIN wms_warehouse tw ON t.to_warehouse_id = tw.id " +
            "LEFT JOIN pos_store fs ON t.from_store_id = fs.id " +
            "LEFT JOIN pos_store ts ON t.to_store_id = ts.id " +
            "WHERE t.deleted = 0 AND t.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null && !status.isEmpty()) {
            sql.append("AND t.status = ? ");
            args.add(status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (t.transfer_no LIKE ? OR t.product_name LIKE ? OR p.product_code LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY t.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize);
        args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM wms_stock_transfer t WHERE t.deleted=0 AND t.tenant_id=? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (status != null && !status.isEmpty()) { csql.append("AND t.status=? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (t.transfer_no LIKE ? OR t.product_name LIKE ?) ");
            cargs.add("%" + keyword + "%");
            cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", records);
        page.put("total", total);
        page.put("current", pageNum);
        page.put("size", pageSize);
        return page;
    }

    // ============================================================
    // 7. 调拨单详情
    // ============================================================

    @Override
    public Map<String, Object> transferDetail(Long tenantId, Long id) {
        String sql = "SELECT t.*, p.product_code, p.unit, p.min_stock, p.max_stock, " +
                     "       fw.warehouse_name AS from_warehouse_name, fw.warehouse_type AS from_warehouse_type, fw.location AS from_location, " +
                     "       tw.warehouse_name AS to_warehouse_name, tw.warehouse_type AS to_warehouse_type, tw.location AS to_location, " +
                     "       fs.store_name AS from_store_name, " +
                     "       ts.store_name AS to_store_name " +
                     "FROM wms_stock_transfer t " +
                     "LEFT JOIN stock_product p ON t.product_id = p.id " +
                     "LEFT JOIN wms_warehouse fw ON t.from_warehouse_id = fw.id " +
                     "LEFT JOIN wms_warehouse tw ON t.to_warehouse_id = tw.id " +
                     "LEFT JOIN pos_store fs ON t.from_store_id = fs.id " +
                     "LEFT JOIN pos_store ts ON t.to_store_id = ts.id " +
                     "WHERE t.deleted = 0 AND t.tenant_id = ? AND t.id = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, id);
        if (rows.isEmpty()) throw new BusinessException(BizCode.NOT_FOUND, "调拨单不存在");
        return rows.get(0);
    }

    // ============================================================
    // 8. 审批
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockTransfer approveTransfer(Long tenantId, Long id, Map<String, Object> body) {
        WmsStockTransfer t = transferMapper.selectById(id);
        if (t == null || !tenantId.equals(t.getTenantId()))
            throw new BusinessException(BizCode.NOT_FOUND, "调拨单不存在");
        if (!"PENDING".equals(t.getStatus()))
            throw new BusinessException(BizCode.CONFLICT, "仅 PENDING 状态可审批");

        t.setStatus("APPROVED");
        if (body != null && body.get("remark") != null) {
            t.setRemark((t.getRemark() == null ? "" : t.getRemark() + " | ")
                    + "[审批]" + body.get("remark"));
        }
        transferMapper.updateById(t);
        return t;
    }

    // ============================================================
    // 9. 发货
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockTransfer shipTransfer(Long tenantId, Long id) {
        WmsStockTransfer t = transferMapper.selectById(id);
        if (t == null || !tenantId.equals(t.getTenantId()))
            throw new BusinessException(BizCode.NOT_FOUND, "调拨单不存在");
        if (!"APPROVED".equals(t.getStatus()))
            throw new BusinessException(BizCode.CONFLICT, "仅 APPROVED 状态可发货（当前：" + t.getStatus() + "）");

        int out = jdbc.update(
            "UPDATE stock_inventory SET quantity = quantity - ?, version = version + 1, " +
            "       last_out_time = NOW() " +
            "WHERE warehouse_id = ? AND product_id = ? AND batch_no <=> ? " +
            "  AND deleted = 0 AND quantity >= ? LIMIT 1",
            t.getQuantity(), t.getFromWarehouseId(), t.getProductId(),
            t.getBatchNo(), t.getQuantity());
        if (out == 0) {
            throw new BusinessException(BizCode.STOCK_NOT_ENOUGH, "源仓库存不足或并发冲突");
        }

        jdbc.update(
            "INSERT INTO stock_record (tenant_id, store_id, product_id, batch_no, type, " +
            "       quantity, before_qty, after_qty, operator_name, remark, record_time) " +
            "SELECT ?, from_store_id, product_id, batch_no, 7, " +
            "       -quantity, 0, 0, ?, ?, NOW() FROM wms_stock_transfer WHERE id = ?",
            tenantId, t.getOperatorName(),
            "调拨发货 " + t.getTransferNo(), id);

        t.setStatus("SHIPPED");
        t.setShippedAt(LocalDateTime.now());
        transferMapper.updateById(t);
        return t;
    }

    // ============================================================
    // 10. 收货
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockTransfer receiveTransfer(Long tenantId, Long id, Map<String, Object> body) {
        WmsStockTransfer t = transferMapper.selectById(id);
        if (t == null || !tenantId.equals(t.getTenantId()))
            throw new BusinessException(BizCode.NOT_FOUND, "调拨单不存在");
        if (!"SHIPPED".equals(t.getStatus()))
            throw new BusinessException(BizCode.CONFLICT, "仅 SHIPPED 状态可收货");

        jdbc.update(
            "INSERT INTO stock_inventory (tenant_id, store_id, warehouse_id, product_id, " +
            "       batch_no, quantity, version, last_in_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), " +
            "       version = version + 1, last_in_time = NOW()",
            tenantId, t.getToStoreId(), t.getToWarehouseId(),
            t.getProductId(), t.getBatchNo(), t.getQuantity());

        jdbc.update(
            "INSERT INTO stock_record (tenant_id, store_id, product_id, batch_no, type, " +
            "       quantity, before_qty, after_qty, operator_name, remark, record_time) " +
            "SELECT ?, to_store_id, product_id, batch_no, 1, " +
            "       quantity, 0, 0, ?, ?, NOW() FROM wms_stock_transfer WHERE id = ?",
            tenantId, body != null && body.get("operatorName") != null
                    ? (String) body.get("operatorName") : t.getOperatorName(),
            "调拨收货 " + t.getTransferNo(), id);

        t.setStatus("RECEIVED");
        t.setReceivedAt(LocalDateTime.now());
        transferMapper.updateById(t);
        return t;
    }

    // ============================================================
    // 11. 取消调拨
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmsStockTransfer cancelTransfer(Long tenantId, Long id, Map<String, Object> body) {
        WmsStockTransfer t = transferMapper.selectById(id);
        if (t == null || !tenantId.equals(t.getTenantId()))
            throw new BusinessException(BizCode.NOT_FOUND, "调拨单不存在");
        if ("RECEIVED".equals(t.getStatus()))
            throw new BusinessException(BizCode.CONFLICT, "已收货的调拨单不能取消");
        if ("CANCELLED".equals(t.getStatus()))
            throw new BusinessException(BizCode.CONFLICT, "调拨单已取消");
        if ("SHIPPED".equals(t.getStatus()))
            throw new BusinessException(BizCode.CONFLICT, "已发货的调拨单不能直接取消");

        t.setStatus("CANCELLED");
        if (body != null && body.get("reason") != null) {
            t.setRemark((t.getRemark() == null ? "" : t.getRemark() + " | ")
                    + "[取消]" + body.get("reason"));
        }
        transferMapper.updateById(t);

        WmsTransferSuggestion sg = suggestionMapper.selectOne(
                new LambdaQueryWrapper<WmsTransferSuggestion>()
                .eq(WmsTransferSuggestion::getResultTransferId, id)
                .last("LIMIT 1"));
        if (sg != null && sg.getAccepted() != null && sg.getAccepted() == 1) {
            sg.setAccepted(0);
            sg.setAcceptedAt(null);
            sg.setAcceptedBy(null);
            sg.setResultTransferId(null);
            suggestionMapper.updateById(sg);
        }
        return t;
    }

    // ============================================================
    // 12. 调度仪表盘
    // ============================================================

    @Override
    public Map<String, Object> dashboard(Long tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();

        for (String st : new String[]{"PENDING", "APPROVED", "SHIPPED", "RECEIVED", "CANCELLED"}) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wms_stock_transfer WHERE deleted=0 AND tenant_id=? AND status=?",
                Long.class, tenantId, st);
            data.put(st.toLowerCase() + "Count", cnt == null ? 0 : cnt);
        }

        Long inTransit = jdbc.queryForObject(
            "SELECT COALESCE(SUM(quantity),0) FROM wms_stock_transfer " +
            "WHERE deleted=0 AND tenant_id=? AND status IN ('APPROVED','SHIPPED')",
            Long.class, tenantId);
        data.put("inTransitQty", inTransit == null ? 0 : inTransit);

        Long todayDone = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_stock_transfer " +
            "WHERE deleted=0 AND tenant_id=? AND status='RECEIVED' AND DATE(received_at)=CURDATE()",
            Long.class, tenantId);
        data.put("todayDoneCount", todayDone == null ? 0 : todayDone);

        Long pendingSugg = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_transfer_suggestion " +
            "WHERE deleted=0 AND tenant_id=? AND accepted=0",
            Long.class, tenantId);
        data.put("pendingSuggestions", pendingSugg == null ? 0 : pendingSugg);

        for (String p : new String[]{"HIGH", "MEDIUM", "LOW"}) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wms_transfer_suggestion " +
                "WHERE deleted=0 AND tenant_id=? AND accepted=0 AND priority=?",
                Long.class, tenantId, p);
            data.put(p.toLowerCase() + "PriorityCount", cnt == null ? 0 : cnt);
        }

        Map<String, Object> successRate = jdbc.queryForMap(
            "SELECT " +
            "  SUM(CASE WHEN status='RECEIVED' THEN 1 ELSE 0 END) AS success, " +
            "  SUM(CASE WHEN status='CANCELLED' THEN 1 ELSE 0 END) AS fail, " +
            "  COUNT(*) AS total " +
            "FROM wms_stock_transfer WHERE deleted=0 AND tenant_id=? AND status IN ('RECEIVED','CANCELLED')",
            tenantId);
        long succ = toLongValue(successRate.get("success"));
        long fail = toLongValue(successRate.get("fail"));
        long total = toLongValue(successRate.get("total"));
        double rate = total == 0 ? 0 : Math.round(succ * 1000.0 / total) / 10.0;
        data.put("successRate", rate);

        Double avgHours = jdbc.queryForObject(
            "SELECT AVG(TIMESTAMPDIFF(MINUTE, shipped_at, received_at)) / 60.0 " +
            "FROM wms_stock_transfer " +
            "WHERE deleted=0 AND tenant_id=? AND status='RECEIVED' " +
            "  AND shipped_at IS NOT NULL AND received_at IS NOT NULL",
            Double.class, tenantId);
        data.put("avgTransferHours", avgHours == null ? 0 : Math.round(avgHours * 10) / 10.0);

        for (String sev : new String[]{"HIGH", "MEDIUM", "LOW"}) {
            Long cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wms_cross_warehouse_alert " +
                "WHERE deleted=0 AND tenant_id=? AND status=1 AND severity=?",
                Long.class, tenantId, sev);
            data.put(sev.toLowerCase() + "Alerts", cnt == null ? 0 : cnt);
        }

        List<Map<String, Object>> recent = jdbc.queryForList(
            "SELECT t.id, t.transfer_no, t.product_name, t.quantity, t.status, t.create_time, t.update_time, " +
            "       fw.warehouse_name AS from_warehouse_name, tw.warehouse_name AS to_warehouse_name " +
            "FROM wms_stock_transfer t " +
            "LEFT JOIN wms_warehouse fw ON t.from_warehouse_id = fw.id " +
            "LEFT JOIN wms_warehouse tw ON t.to_warehouse_id = tw.id " +
            "WHERE t.deleted=0 AND t.tenant_id=? " +
            "ORDER BY t.update_time DESC LIMIT 10",
            tenantId);
        data.put("recentTransfers", recent);

        return data;
    }

    // ============================================================
    // 13. 门店下拉
    // ============================================================

    @Override
    public Object storeList(Long tenantId) {
        return jdbc.queryForList(
            "SELECT s.id, s.store_name, s.store_code, s.brand_id, " +
            "       w.id AS warehouse_id, w.warehouse_name, w.warehouse_type " +
            "FROM pos_store s " +
            "LEFT JOIN wms_warehouse w ON w.store_id = s.id AND w.deleted=0 AND w.warehouse_type='STORE' " +
            "WHERE s.deleted=0 AND s.tenant_id=? " +
            "ORDER BY s.id",
            tenantId);
    }

    // ============================================================
    // 14. 商品下拉
    // ============================================================

    @Override
    public Object productList(Long tenantId, String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, product_code, product_name, unit, category, min_stock, max_stock " +
            "FROM stock_product WHERE deleted=0 AND status=1 AND tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (product_code LIKE ? OR product_name LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY product_name LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ============================================================
    // 定时扫描（每天 02:00）
    // ============================================================

    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledScan() {
        log.info("[M12-02] 定时扫描开始");
        // 后台无租户上下文，定时任务由外部触发 scanSchedule(tenantId, null)
        // 实际使用请在 ApplicationRunner 或定时任务中按租户逐个调用
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 聚合列在无匹配行时为 NULL（SUM 不带 COALESCE），统一兜底为 0。
     */
    private static long toLongValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Long findOrInferWarehouseId(Long storeId) {
        if (storeId == null) return null;
        try {
            List<WmsWarehouse> list = warehouseMapper.selectList(
                    new LambdaQueryWrapper<WmsWarehouse>()
                    .eq(WmsWarehouse::getStoreId, storeId)
                    .eq(WmsWarehouse::getWarehouseType, "STORE")
                    .eq(WmsWarehouse::getEnabled, 1)
                    .last("LIMIT 1"));
            return list.isEmpty() ? null : list.get(0).getId();
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseStoreIdFromWarehouse(Long warehouseId) {
        if (warehouseId == null) return null;
        try {
            WmsWarehouse w = warehouseMapper.selectById(warehouseId);
            return w == null ? null : w.getStoreId();
        } catch (Exception e) {
            return null;
        }
    }

    private String generateTransferNo() {
        return "TR" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
