package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.WmsStockTransfer;
import com.meiyetec.entity.WmsWarehouse;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.WmsStockTransferMapper;
import com.meiyetec.mapper.WmsWarehouseMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R3.0-D 补丁：
 *  - 多仓管理（WmsWarehouse CRUD）
 *  - 调拨管理（WmsStockTransfer 状态机：PENDING → SHIPPED → RECEIVED）
 *  - 评价维度分析（CrmReview 按服务/员工/门店聚合）
 *
 * 路径前缀：/wms  + /crm/care/review-analysis
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WmsService {

    private final WmsWarehouseMapper warehouseMapper;
    private final WmsStockTransferMapper transferMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================== 仓库 CRUD ====================

    @GetMapping("/wms/warehouse/page")
    public Result<Map<String, Object>> pageWarehouses(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String warehouseType,
            @RequestParam(required = false) Integer enabled) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<WmsWarehouse> qw = new LambdaQueryWrapper<>();
        qw.eq(WmsWarehouse::getTenantId, tenantId);
        if (storeId != null) qw.eq(WmsWarehouse::getStoreId, storeId);
        if (warehouseType != null && !warehouseType.isEmpty())
            qw.eq(WmsWarehouse::getWarehouseType, warehouseType);
        if (enabled != null) qw.eq(WmsWarehouse::getEnabled, enabled);
        qw.orderByAsc(WmsWarehouse::getWarehouseType).orderByAsc(WmsWarehouse::getId);
        Long total = warehouseMapper.selectCount(qw);
        List<WmsWarehouse> records = warehouseMapper.selectList(qw);
        Map<String, Object> p = new HashMap<>();
        p.put("total", total);
        p.put("records", records);
        p.put("current", pageNum);
        p.put("size", pageSize);
        return Result.success(p);
    }

    @PostMapping("/wms/warehouse")
    @Transactional(rollbackFor = Exception.class)
    public Result<WmsWarehouse> createWarehouse(@RequestBody WmsWarehouse w) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (w.getWarehouseCode() == null || w.getWarehouseName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (w.getEnabled() == null) w.setEnabled(1);
        w.setTenantId(tenantId);
        // 唯一性校验
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wms_warehouse WHERE deleted=0 AND tenant_id=? AND warehouse_code=?",
            Long.class, tenantId, w.getWarehouseCode());
        if (dup != null && dup > 0) return Result.error(BizCode.CONFLICT, "编码已存在");
        warehouseMapper.insert(w);
        return Result.success("已创建", w);
    }

    @PostMapping("/wms/warehouse/{id}/toggle")
    public Result<Void> toggleWarehouse(@PathVariable Long id, @RequestParam Boolean enabled) {
        if (warehouseMapper.selectById(id) == null)
            return Result.error(BizCode.NOT_FOUND, "仓库不存在");
        warehouseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<WmsWarehouse>()
                .eq("id", id).set("enabled", enabled ? 1 : 0));
        return Result.success(enabled ? "已启用" : "已停用", null);
    }

    // ==================== 调拨单 ====================

    @PostMapping("/wms/transfer")
    @Transactional(rollbackFor = Exception.class)
    public Result<WmsStockTransfer> createTransfer(@RequestBody WmsStockTransfer t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getProductId() == null || t.getFromWarehouseId() == null || t.getToWarehouseId() == null
                || t.getQuantity() == null || t.getQuantity() <= 0) {
            return Result.error(BizCode.BAD_REQUEST, "productId / 仓库 / 数量 必填");
        }
        if (t.getFromWarehouseId().equals(t.getToWarehouseId())) {
            return Result.error(BizCode.BAD_REQUEST, "源/目标仓库不能相同");
        }
        t.setTenantId(tenantId);
        t.setTransferNo("TR" + LocalDateTime.now().toString().replace(":", "").substring(0, 15)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        t.setStatus("PENDING");
        t.setOperatorName(t.getOperatorName() == null ? "admin" : t.getOperatorName());
        transferMapper.insert(t);
        return Result.success("调拨单已创建（待发货）", t);
    }

    @PostMapping("/wms/transfer/{id}/ship")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> shipTransfer(@PathVariable Long id) {
        WmsStockTransfer t = transferMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "调拨单不存在");
        if (!"PENDING".equals(t.getStatus())) return Result.error(BizCode.CONFLICT, "当前状态不可发货");
        // 扣减源仓库存
        int out = jdbc.update(
            "UPDATE stock_inventory SET quantity = quantity - ?, version = version + 1 " +
            "WHERE warehouse_id = ? AND product_id = ? AND batch_no <=> ? " +
            "  AND deleted = 0 AND quantity >= ? LIMIT 1",
            t.getQuantity(), t.getFromWarehouseId(), t.getProductId(),
            t.getBatchNo(), t.getQuantity());
        if (out == 0) return Result.error(BizCode.BAD_REQUEST, "源仓库存不足");
        jdbc.update(
            "UPDATE wms_stock_transfer SET status='SHIPPED', shipped_at=NOW() WHERE id=?",
            id);
        return Result.success("已发货", null);
    }

    @PostMapping("/wms/transfer/{id}/receive")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> receiveTransfer(@PathVariable Long id) {
        WmsStockTransfer t = transferMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "调拨单不存在");
        if (!"SHIPPED".equals(t.getStatus())) return Result.error(BizCode.CONFLICT, "当前状态不可收货");
        // 目标仓入库：复用 stock_inventory 入库逻辑（无 warehouseId 时自动按 storeId 创建）
        jdbc.update(
            "INSERT INTO stock_inventory (tenant_id, store_id, warehouse_id, product_id, batch_no, quantity, version) " +
            "VALUES (?, ?, ?, ?, ?, ?, 0) " +
            "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity), version = version + 1",
            t.getTenantId(), t.getToStoreId(), t.getToWarehouseId(),
            t.getProductId(), t.getBatchNo(), t.getQuantity());
        jdbc.update(
            "UPDATE wms_stock_transfer SET status='RECEIVED', received_at=NOW() WHERE id=?",
            id);
        return Result.success("已收货", null);
    }

    @GetMapping("/wms/transfer/page")
    public Result<Map<String, Object>> pageTransfers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<WmsStockTransfer> qw = new LambdaQueryWrapper<>();
        qw.eq(WmsStockTransfer::getTenantId, tenantId);
        if (status != null && !status.isEmpty()) qw.eq(WmsStockTransfer::getStatus, status);
        qw.orderByDesc(WmsStockTransfer::getId);
        Long total = transferMapper.selectCount(qw);
        List<WmsStockTransfer> records = transferMapper.selectList(qw);
        Map<String, Object> p = new HashMap<>();
        p.put("total", total);
        p.put("records", records);
        p.put("current", pageNum);
        p.put("size", pageSize);
        return Result.success(p);
    }

    // ==================== 评价维度分析（P1-4 增量） ====================

    /**
     * 评价多维度聚合：按 source（订单/服务/员工/门店）
     */
    @GetMapping("/crm/care/review-analysis")
    public Result<Map<String, Object>> reviewAnalysis(
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "30") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(AVG(overall_rating),0) AS avgOverall, " +
            "       COUNT(*) AS total, " +
            "       SUM(CASE WHEN overall_rating >= 4 THEN 1 ELSE 0 END) AS positive, " +
            "       SUM(CASE WHEN overall_rating = 3 THEN 1 ELSE 0 END) AS neutral, " +
            "       SUM(CASE WHEN overall_rating <= 2 THEN 1 ELSE 0 END) AS negative " +
            "FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 " +
            "  AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY) ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId); args.add(days);
        if (sourceType != null && !sourceType.isEmpty()) {
            sql.append("AND source_type=? ");
            args.add(sourceType);
        }
        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args.toArray());

        // 按维度细分（service/skill/environment/price）
        StringBuilder dimSql = new StringBuilder(
            "SELECT " +
            "  COALESCE(AVG(service_rating),0) AS avgService, " +
            "  COALESCE(AVG(skill_rating),0) AS avgSkill, " +
            "  COALESCE(AVG(environment_rating),0) AS avgEnv, " +
            "  COALESCE(AVG(price_rating),0) AS avgPrice " +
            "FROM crm_review WHERE deleted=0 AND tenant_id=? AND status=2 " +
            "  AND create_time >= DATE_SUB(NOW(), INTERVAL ? DAY) ");
        List<Object> dimArgs = new ArrayList<>();
        dimArgs.add(tenantId); dimArgs.add(days);
        if (sourceType != null && !sourceType.isEmpty()) {
            dimSql.append("AND source_type=? ");
            dimArgs.add(sourceType);
        }
        Map<String, Object> dim = jdbc.queryForMap(dimSql.toString(), dimArgs.toArray());

        Map<String, Object> data = new HashMap<>();
        data.put("period", days + "天");
        data.put("total", toLongValue(row.get("total")));
        data.put("avgOverall", round1(toDoubleValue(row.get("avgOverall"))));
        data.put("positive", toLongValue(row.get("positive")));
        data.put("neutral", toLongValue(row.get("neutral")));
        data.put("negative", toLongValue(row.get("negative")));
        data.put("avgService", round1(toDoubleValue(dim.get("avgService"))));
        data.put("avgSkill", round1(toDoubleValue(dim.get("avgSkill"))));
        data.put("avgEnv", round1(toDoubleValue(dim.get("avgEnv"))));
        data.put("avgPrice", round1(toDoubleValue(dim.get("avgPrice"))));

        // NPS 简化版 = (好评 - 差评) / 总数 × 100
        long total = toLongValue(data.get("total"));
        long positive = toLongValue(data.get("positive"));
        long negative = toLongValue(data.get("negative"));
        double nps = total == 0 ? 0 : Math.round(((positive - negative) * 1000.0 / total)) / 10.0;
        data.put("nps", nps);

        // 改进行动建议（基于维度短板）
        List<String> actions = new ArrayList<>();
        if (toDoubleValue(dim.get("avgService")) < 3.5)
            actions.add("服务态度评分偏低（" + data.get("avgService") + "），建议开展服务礼仪培训");
        if (toDoubleValue(dim.get("avgSkill")) < 3.5)
            actions.add("技术评分偏低，建议技师技能复训 + SOP 考核");
        if (toDoubleValue(dim.get("avgEnv")) < 3.5)
            actions.add("环境评分偏低，建议门店 5S 检查 + 装修升级");
        if (toDoubleValue(dim.get("avgPrice")) < 3.5)
            actions.add("性价比评分偏低，建议调整套餐组合 / 增加增值服务");
        if (nps < 30) actions.add("NPS 偏低（" + nps + "），建议开展低分客户一对一跟进");
        if (actions.isEmpty()) actions.add("暂无明显短板，请保持现状");
        data.put("improvementActions", actions);

        return Result.success(data);
    }

    private double round1(Object v) {
        if (v == null) return 0;
        return Math.round(((Number) v).doubleValue() * 10) / 10.0;
    }

    /**
     * 聚合列在无匹配行时为 NULL（SUM(CASE...) 不带 COALESCE），统一兜底为 0。
     */
    private static long toLongValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static double toDoubleValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }
}