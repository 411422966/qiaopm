package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.dto.StockProductImportDTO;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.IdempotencyService;
import com.meiyetec.service.InventoryConsumptionService;
import com.meiyetec.service.StockDualVerifyService;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import com.meiyetec.util.XlsxExportUtil;
import com.meiyetec.annotation.SysOpLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M08 库存管理
 * - 商品 CRUD + 库存查询 + 入库/出库/盘点 + 预警
 */
@RestController
@RequestMapping("/stock")
@SysOpLog(module = "库存")
public class StockController {

    @Autowired private StockProductMapper productMapper;
    @Autowired private StockInventoryMapper inventoryMapper;
    @Autowired private StockRecordMapper recordMapper;
    @Autowired private StockCheckMapper checkMapper;
    @Autowired private BizServiceMaterialMapper bomMapper;
    @Autowired private PosOrderConsumptionMapper orderConsumptionMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdempotencyService idem;
    @Autowired private InventoryConsumptionService consumptionService;
    @Autowired private StockDualVerifyService dualVerifyService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** T07-D1：取当前租户；缺失则抛 UNAUTHORIZED，禁止 fallback 到默认租户 1L */
    private Long tenantId() {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return tid;
    }

    // ==================== 商品（耗材）===================

    @GetMapping("/products/page")
    @RequirePermission("stock:product:list")
    public Result<Page<StockProduct>> productPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        Long tid = tenantId();
        LambdaQueryWrapper<StockProduct> q = new LambdaQueryWrapper<>();
        q.eq(StockProduct::getTenantId, tid);
        if (storeId != null) q.eq(StockProduct::getStoreId, storeId);
        if (category != null && !category.isEmpty()) q.eq(StockProduct::getCategory, category);
        if (status != null) q.eq(StockProduct::getStatus, status);
        if (keyword != null && !keyword.isEmpty())
            q.and(w -> w.like(StockProduct::getProductCode, keyword)
                    .or().like(StockProduct::getProductName, keyword));
        q.orderByDesc(StockProduct::getId);
        Page<StockProduct> page = productMapper.selectPage(new Page<>(pageNum, pageSize), q);
        return Result.success(page);
    }

    @GetMapping("/products/list")
    @RequirePermission("stock:product:list")
    public Result<List<StockProduct>> productList(@RequestParam(required = false) Long storeId,
                                                  @RequestParam(required = false) Integer status) {
        Long tid = tenantId();
        LambdaQueryWrapper<StockProduct> q = new LambdaQueryWrapper<>();
        q.eq(StockProduct::getTenantId, tid);
        if (storeId != null) q.eq(StockProduct::getStoreId, storeId);
        if (status != null) q.eq(StockProduct::getStatus, status);
        q.orderByAsc(StockProduct::getProductName);
        return Result.success(productMapper.selectList(q));
    }

    @GetMapping("/products/{id}")
    @RequirePermission("stock:product:list")
    public Result<StockProduct> productDetail(@PathVariable Long id) {
        Long tid = tenantId();
        StockProduct p = productMapper.selectById(id);
        if (p == null || !tid.equals(p.getTenantId())) return Result.error("商品不存在");
        return Result.success(p);
    }

    @PostMapping("/products")
    @RequirePermission("stock:product:add")
    public Result<StockProduct> createProduct(@RequestBody StockProduct p) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        if (p.getProductCode() == null || p.getProductCode().isEmpty())
            return Result.error("商品编码必填");
        if (p.getProductName() == null || p.getProductName().isEmpty())
            return Result.error("商品名称必填");
        if (isOnlyPlaceholder(p.getProductName()))
            return Result.error("商品名称不能全为 ? 或乱码占位符，请输入正确中文");
        if (p.getCategory() != null && isOnlyPlaceholder(p.getCategory()))
            return Result.error("分类不能全为 ? 或乱码占位符");
        if (p.getUnit() != null && isOnlyPlaceholder(p.getUnit()))
            return Result.error("单位不能全为 ? 或乱码占位符");
        if (p.getStoreId() == null) p.setStoreId(1L); // 默认门店
        // 唯一性
        Long exist = productMapper.selectCount(new LambdaQueryWrapper<StockProduct>()
                .eq(StockProduct::getProductCode, p.getProductCode()));
        if (exist > 0) return Result.error("商品编码已存在");
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getMinStock() == null) p.setMinStock(10);
        if (p.getMaxStock() == null) p.setMaxStock(1000);
        if (p.getEnableBatch() == null) p.setEnableBatch(0);
        p.setTenantId(tenantId);
        productMapper.insert(p);
        return Result.success("创建成功", p);
    }

    private static boolean isOnlyPlaceholder(String s) {
        if (s == null || s.isEmpty()) return false;
        // 全部都是 ? 或 全部都是常见占位字符（�、？等）
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '?' && c != '？' && c != '\uFFFD' && c != '\u25A1') return false;
        }
        return true;
    }

    @PutMapping("/products/{id}")
    @RequirePermission("stock:product:edit")
    public Result<StockProduct> updateProduct(@PathVariable Long id, @RequestBody StockProduct p) {
        Long tid = tenantId();
        StockProduct old = productMapper.selectById(id);
        if (old == null || !tid.equals(old.getTenantId())) return Result.error("商品不存在");
        p.setId(id);
        p.setProductCode(old.getProductCode()); // 编码不可改
        p.setTenantId(tid);
        productMapper.updateById(p);
        return Result.success("更新成功", productMapper.selectById(id));
    }

    @DeleteMapping("/products/{id}")
    @RequirePermission("stock:product:delete")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        Long tid = tenantId();
        StockProduct p = productMapper.selectById(id);
        if (p == null || !tid.equals(p.getTenantId())) return Result.error("商品不存在");
        // 检查是否有库存
        Long inv = inventoryMapper.selectCount(new LambdaQueryWrapper<StockInventory>()
                .eq(StockInventory::getProductId, id).gt(StockInventory::getQuantity, 0));
        if (inv > 0) return Result.error("该商品仍有库存，不能删除");
        productMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    // ==================== R2.0-D：商品批量导入 ====================

    /**
     * 批量导入商品（幂等）
     *  - 入参：StockProductImportDTO{items, mode}
     *  - 幂等：依赖 Idempotency-Key 头（同一 key 重放只返回上次结果）
     *  - mode: UPSERT（默认）/ INSERT
     */
    @PostMapping("/products/import")
    @RequirePermission("stock:product:import")
    public Result<Map<String, Object>> importProducts(
            @org.springframework.web.bind.annotation.RequestBody StockProductImportDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "导入明细为空");
        }
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "stock.product.import", key, dto, () -> doImportProducts(dto));
        return r.result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doImportProducts(StockProductImportDTO dto) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        String mode = dto.getMode() == null ? "UPSERT" : dto.getMode().toUpperCase();

        int inserted = 0, updated = 0, skipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<String> codes = new ArrayList<>();

        for (int i = 0; i < dto.getItems().size(); i++) {
            StockProductImportDTO.Item it = dto.getItems().get(i);
            if (it.getProductCode() == null || it.getProductCode().isEmpty()) {
                errors.add(rowError(i, "缺少 productCode"));
                continue;
            }
            if (it.getProductName() == null || it.getProductName().isEmpty()) {
                errors.add(rowError(i, "缺少 productName"));
                continue;
            }
            if (isOnlyPlaceholder(it.getProductName())) {
                errors.add(rowError(i, "productName 不可为占位符"));
                continue;
            }

            StockProduct existing = productMapper.selectOne(
                    new LambdaQueryWrapper<StockProduct>()
                            .eq(StockProduct::getProductCode, it.getProductCode()));
            if (existing != null && "INSERT".equals(mode)) {
                skipped++;
                continue;
            }

            StockProduct p = new StockProduct();
            p.setProductCode(it.getProductCode());
            p.setProductName(it.getProductName());
            if (it.getCategory() != null) p.setCategory(it.getCategory());
            if (it.getSpec() != null) p.setSpec(it.getSpec());
            if (it.getUnit() != null) p.setUnit(it.getUnit());
            if (it.getCostPrice() != null) p.setCostPrice(it.getCostPrice());
            if (it.getSalePrice() != null) p.setSalePrice(it.getSalePrice());
            if (it.getMinStock() != null) p.setMinStock(it.getMinStock());
            if (it.getMaxStock() != null) p.setMaxStock(it.getMaxStock());
            if (it.getEnableBatch() != null) p.setEnableBatch(it.getEnableBatch());
            if (it.getStatus() != null) p.setStatus(it.getStatus());
            if (it.getRemark() != null) p.setRemark(it.getRemark());

            if (existing == null) {
                p.setTenantId(tenantId);
                if (p.getStoreId() == null) p.setStoreId(1L);
                if (p.getStatus() == null) p.setStatus(1);
                if (p.getMinStock() == null) p.setMinStock(10);
                if (p.getMaxStock() == null) p.setMaxStock(1000);
                if (p.getEnableBatch() == null) p.setEnableBatch(0);
                productMapper.insert(p);
                inserted++;
                codes.add(it.getProductCode());
            } else {
                p.setId(existing.getId());
                p.setProductCode(existing.getProductCode()); // 编码不可改
                productMapper.updateById(p);
                updated++;
                codes.add(it.getProductCode());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", dto.getItems().size());
        data.put("inserted", inserted);
        data.put("updated", updated);
        data.put("skipped", skipped);
        data.put("errorCount", errors.size());
        data.put("errors", errors);
        data.put("codes", codes);
        return Result.success("导入完成", data);
    }

    private static Map<String, Object> rowError(int idx, String msg) {
        Map<String, Object> e = new HashMap<>();
        e.put("row", idx);
        e.put("msg", msg);
        return e;
    }

    /**
     * 商品导入模板（CSV，含占位说明）
     */
    @GetMapping("/products/import-template")
    @RequirePermission("stock:product:import")
    public ResponseEntity<byte[]> importTemplate() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# 商品导入模板 · 必填：productCode,productName；可选：category,spec,unit,costPrice,salePrice,minStock,maxStock,enableBatch,status,remark\n");
        sb.append("productCode,productName,category,spec,unit,costPrice,salePrice,minStock,maxStock,enableBatch,status,remark\n");
        sb.append("SP-EXAMPLE-001,示例商品A,耗材,30ml/瓶,瓶,80.00,198.00,10,500,0,1,示例\n");
        sb.append("SP-EXAMPLE-002,示例商品B,产品,500ml/盒,盒,60.00,158.00,20,300,0,1,示例\n");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        baos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        String filename = "商品导入模板.csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
        headers.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
    }

    // ==================== 库存查询 ====================

    @GetMapping("/inventory/page")
    @RequirePermission("stock:stat")
    public Result<Page<Map<String, Object>>> inventoryPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String expiryStatus) {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT i.id, i.store_id, i.product_id, i.batch_no, i.quantity, " +
            "i.production_date, i.expiry_date, i.supplier, i.last_in_time, i.last_out_time, " +
            "p.product_code, p.product_name, p.category, p.spec, p.unit, " +
            "p.min_stock, p.max_stock, p.cost_price, p.sale_price, p.enable_batch, p.status AS product_status " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(tid);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        if (category != null && !category.isEmpty()) {
            sql.append("AND p.category = ? ");
            args.add(category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (p.product_code LIKE ? OR p.product_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        // 库存状态 / 有效期 过滤：先拉全量再过滤会丢分页，改为子查询 + HAVING
        // 这里采用 2 次查询：先按基础条件拿到候选，再用 alertType/expiryStatus 在内存里过滤后再分页
        // 数据量大时可改写为派生表 + CASE WHEN

        // count
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tid); cargs.add(tid);
        if (storeId != null) { csql.append("AND i.store_id = ? "); cargs.add(storeId); }
        if (category != null && !category.isEmpty()) { csql.append("AND p.category = ? "); cargs.add(category); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (p.product_code LIKE ? OR p.product_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }

        boolean needPostFilter = (alertType != null && !alertType.isEmpty())
                || (expiryStatus != null && !expiryStatus.isEmpty());
        if (needPostFilter) {
            // 拉全部候选（不带 limit/offset），在内存里过滤后再分页
            List<Map<String, Object>> candidates = jdbc.queryForList(sql.toString(), args.toArray());
            LocalDate today = LocalDate.now();
            candidates = candidates.stream().filter(r -> {
                int qty = ((Number) r.get("quantity")).intValue();
                int min = ((Number) r.get("min_stock")).intValue();
                int max = ((Number) r.get("max_stock")).intValue();
                String computedAlert;
                if (qty <= 0) computedAlert = "OUT";
                else if (qty < min) computedAlert = "LOW";
                else if (qty > max) computedAlert = "OVER";
                else computedAlert = "OK";

                if (alertType != null && !alertType.isEmpty() && !computedAlert.equals(alertType)) {
                    return false;
                }
                if (expiryStatus != null && !expiryStatus.isEmpty()) {
                    Object exp = r.get("expiry_date");
                    String computedExpiry;
                    if (exp == null) {
                        computedExpiry = "OK"; // 没设过期日期视为正常
                    } else {
                        LocalDate d = LocalDate.parse(exp.toString());
                        if (d.isBefore(today)) computedExpiry = "EXPIRED";
                        else if (!d.isAfter(today.plusDays(30))) computedExpiry = "EXPIRING";
                        else computedExpiry = "OK";
                    }
                    if (!computedExpiry.equals(expiryStatus)) return false;
                }
                return true;
            }).collect(Collectors.toList());

            int total = candidates.size();
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(candidates.size(), from + pageSize);
            List<Map<String, Object>> records = candidates.subList(from, to);

            records.forEach(r -> {
                int qty = ((Number) r.get("quantity")).intValue();
                int min = ((Number) r.get("min_stock")).intValue();
                int max = ((Number) r.get("max_stock")).intValue();
                String alert;
                if (qty <= 0) alert = "OUT";
                else if (qty < min) alert = "LOW";
                else if (qty > max) alert = "OVER";
                else alert = "OK";
                r.put("alert", alert);
            });

            Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
            page.setRecords(Keys.camel(records));
            page.setTotal(total);
            return Result.success(page);
        }

        sql.append("ORDER BY i.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        // 标记预警
        records.forEach(r -> {
            int qty = ((Number) r.get("quantity")).intValue();
            int min = ((Number) r.get("min_stock")).intValue();
            int max = ((Number) r.get("max_stock")).intValue();
            String alert;
            if (qty <= 0) alert = "OUT";          // 缺货
            else if (qty < min) alert = "LOW";    // 低于最低
            else if (qty > max) alert = "OVER";   // 超出最高
            else alert = "OK";
            r.put("alert", alert);
        });

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/inventory/alerts")
    @RequirePermission("stock:alert")
    public Result<List<Map<String, Object>>> alerts(@RequestParam(required = false) Long storeId) {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT i.id, i.store_id, i.product_id, i.batch_no, i.quantity, " +
            "p.product_code, p.product_name, p.unit, p.min_stock, p.max_stock " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND p.status = 1 " +
            "AND i.tenant_id = ? AND p.tenant_id = ? " +
            "AND (i.quantity <= 0 OR i.quantity < p.min_stock OR i.quantity > p.max_stock) ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(tid);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        sql.append("ORDER BY i.quantity ASC");
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        list.forEach(r -> {
            int qty = ((Number) r.get("quantity")).intValue();
            int min = ((Number) r.get("min_stock")).intValue();
            int max = ((Number) r.get("max_stock")).intValue();
            String alert;
            if (qty <= 0) alert = "缺货";
            else if (qty < min) alert = "低于最低库存";
            else if (qty > max) alert = "超出最高库存";
            else alert = "正常";
            r.put("alert_msg", alert);
        });
        return Result.success(Keys.camel(list));
    }    // ==================== 入库 ====================

    @PostMapping("/inbound")
    @RequirePermission("stock:inbound")
    public Result<Map<String, Object>> inbound(@RequestBody Map<String, Object> body) {
        Long tid = tenantId();
        Long productId = ((Number) body.get("productId")).longValue();
        int qty = ((Number) body.get("quantity")).intValue();
        if (qty <= 0) return Result.error(BizCode.BAD_REQUEST, "入库数量必须 > 0");

        StockProduct product = productMapper.selectById(productId);
        if (product == null || !tid.equals(product.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "商品不存在");
        }

        String batchNo = (String) body.getOrDefault("batchNo",
                "BATCH-" + LocalDateTime.now().format(DTF));
        String supplier = (String) body.get("supplier");
        LocalDate productionDate = body.get("productionDate") != null
                ? LocalDate.parse(body.get("productionDate").toString()) : null;
        LocalDate expiryDate = body.get("expiryDate") != null
                ? LocalDate.parse(body.get("expiryDate").toString()) : null;
        BigDecimal unitPrice = body.get("unitPrice") != null
                ? new BigDecimal(body.get("unitPrice").toString()) : product.getCostPrice();
        Long operatorId = body.get("operatorId") != null
                ? ((Number) body.get("operatorId")).longValue() : 1L;
        String operatorName = (String) body.getOrDefault("operatorName", "admin");
        String remark = (String) body.get("remark");

        StockInventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<StockInventory>()
                .eq(StockInventory::getProductId, productId)
                .eq(StockInventory::getBatchNo, batchNo)
                .eq(StockInventory::getStoreId, product.getStoreId()));
        if (inv == null) {
            inv = new StockInventory();
            inv.setTenantId(product.getTenantId());
            inv.setStoreId(product.getStoreId());
            inv.setProductId(productId);
            inv.setBatchNo(batchNo);
            inv.setQuantity(0);
            inv.setProductionDate(productionDate);
            inv.setExpiryDate(expiryDate);
            inv.setSupplier(supplier);
            inventoryMapper.insert(inv);
        }
        int rows = inventoryMapper.casInbound(inv.getId(), qty);
        if (rows == 0) {
            return Result.error(BizCode.CONCURRENCY_CONFLICT, "入库并发失败");
        }
        inv = inventoryMapper.selectById(inv.getId());
        if (supplier != null) inv.setSupplier(supplier);
        if (productionDate != null) inv.setProductionDate(productionDate);
        if (expiryDate != null) inv.setExpiryDate(expiryDate);

        StockRecord rec = new StockRecord();
        rec.setTenantId(TenantContextHolder.get().getTenantId());
        rec.setStoreId(product.getStoreId());
        rec.setProductId(productId);
        rec.setBatchNo(batchNo);
        // 业务语义：1=普通入库 6=采购入库（补货建议来源）。前端可在 body.recordType 显式指定。
        Integer recordType = body.get("recordType") != null
                ? ((Number) body.get("recordType")).intValue()
                : 1;
        if (recordType != 1 && recordType != 6) {
            return Result.error(BizCode.BAD_REQUEST, "recordType 仅支持 1（普通入库）或 6（采购入库）");
        }
        rec.setType(recordType);
        rec.setQuantity(qty);
        rec.setBeforeQty(inv.getQuantity() - qty);
        rec.setAfterQty(inv.getQuantity());
        rec.setUnitPrice(unitPrice);
        rec.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(qty)));
        rec.setSupplier(supplier);
        rec.setOperatorId(operatorId);
        rec.setOperatorName(operatorName);
        rec.setRemark(remark);
        rec.setRecordTime(LocalDateTime.now());
        recordMapper.insert(rec);

        Map<String, Object> result = new HashMap<>();
        result.put("record", rec);
        result.put("inventory", inv);
        return Result.success("入库成功", result);
    }

// ==================== 出库（领用）R2.0-B 升级版 ====================

    @PostMapping("/outbound")
    @RequirePermission("stock:outbound")
    public Result<Map<String, Object>> outbound(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "stock.outbound", key, body, () -> doOutbound(body));
        return r.result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doOutbound(Map<String, Object> body) {
        Long tid = tenantId();
        Long productId = ((Number) body.get("productId")).longValue();
        int qty = ((Number) body.get("quantity")).intValue();
        if (qty <= 0) throw new BusinessException(BizCode.BAD_REQUEST, "出库数量必须 > 0");

        StockProduct product = productMapper.selectById(productId);
        if (product == null || !tid.equals(product.getTenantId())) throw new BusinessException(BizCode.NOT_FOUND, "商品不存在");

        String batchNo = (String) body.get("batchNo");
        Integer type = body.get("type") != null ? ((Number) body.get("type")).intValue() : 4;
        Long orderId = body.get("orderId") != null ? ((Number) body.get("orderId")).longValue() : null;
        Long operatorId = body.get("operatorId") != null
                ? ((Number) body.get("operatorId")).longValue() : 1L;
        String operatorName = (String) body.getOrDefault("operatorName", "admin");
        String remark = (String) body.get("remark");

        StockInventory inv;
        if (batchNo != null) {
            inv = inventoryMapper.selectOne(new LambdaQueryWrapper<StockInventory>()
                    .eq(StockInventory::getProductId, productId)
                    .eq(StockInventory::getBatchNo, batchNo)
                    .eq(StockInventory::getStoreId, product.getStoreId()));
        } else {
            // FEFO：按到期日期 ASC
            List<StockInventory> list = inventoryMapper.listFefoCandidates(productId, product.getStoreId());
            if (list.isEmpty()) throw new BusinessException(BizCode.STOCK_NOT_ENOUGH, "该商品无库存");
            inv = list.get(0);
            batchNo = inv.getBatchNo();
        }
        if (inv == null) throw new BusinessException(BizCode.NOT_FOUND, "批次不存在");

        int rows = inventoryMapper.casOutbound(inv.getId(), inv.getVersion(), qty);
        if (rows == 0) {
            List<StockInventory> list = inventoryMapper.listFefoCandidates(productId, product.getStoreId());
            StockInventory next = null;
            for (StockInventory candidate : list) {
                if (candidate.getQuantity() >= qty) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                throw new BusinessException(BizCode.STOCK_NOT_ENOUGH, "库存不足");
            }
            rows = inventoryMapper.casOutbound(next.getId(), next.getVersion(), qty);
            if (rows == 0) {
                throw new BusinessException(BizCode.STOCK_OVERBOOK, "库存并发竞争失败，请重试");
            }
            inv = inventoryMapper.selectById(next.getId());
            batchNo = inv.getBatchNo();
        }

        StockRecord rec = new StockRecord();
        rec.setTenantId(TenantContextHolder.get().getTenantId());
        rec.setStoreId(product.getStoreId());
        rec.setProductId(productId);
        rec.setBatchNo(batchNo);
        rec.setType(type);
        rec.setQuantity(-qty);
        rec.setBeforeQty(inv.getQuantity() + qty);
        rec.setAfterQty(inv.getQuantity());
        rec.setUnitPrice(product.getCostPrice());
        rec.setTotalAmount(product.getCostPrice().multiply(BigDecimal.valueOf(qty)));
        rec.setOrderId(orderId);
        rec.setOperatorId(operatorId);
        rec.setOperatorName(operatorName);
        rec.setRemark(remark);
        rec.setRecordTime(LocalDateTime.now());
        recordMapper.insert(rec);

        // P2-11 IND-05：出库完成后检查是否超过双人核销阈值，超则写 PENDING 记录
        BigDecimal totalAmt = product.getCostPrice().multiply(BigDecimal.valueOf(qty));
        dualVerifyService.createPendingRecordIfNeeded(
                rec.getId(), null,          // stockRecordId, checkId
                "OUTBOUND", "4",             // verifyType, recordType
                operatorId, operatorName,
                product.getStoreId(), totalAmt);

        Map<String, Object> result = new HashMap<>();
        result.put("record", rec);
        result.put("inventory", inv);
        return Result.success("出库成功", result);
    }

    // ==================== 盘点（R2.0-B 升级版） ====================

    /**
     * 创建/确认盘点：状态机草稿→已确认→已作废；单笔请求内一个事务。
     * status 转换：
     *   - confirm=false → 草稿（status=1）
     *   - confirm=true  → 已完成（status=2），所有差异记录流水并 CAS 改库存
     *   - 后续 cancelCheck / confirmCheck 用于在草稿/已确认之间切换
     */
    @PostMapping("/check")
    @RequirePermission("stock:check")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> check(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "盘点明细不能为空");
        }

        Long storeId = ((Number) body.get("storeId")).longValue();
        Long operatorId = body.get("operatorId") != null
                ? ((Number) body.get("operatorId")).longValue() : 1L;
        String operatorName = (String) body.getOrDefault("operatorName", "admin");
        String remark = (String) body.get("remark");
        boolean confirm = Boolean.TRUE.equals(body.get("confirm"));

        Long tenantId = TenantContextHolder.get().getTenantId();
        StockCheck check = new StockCheck();
        check.setTenantId(tenantId);
        check.setStoreId(storeId);
        check.setCheckNo("CHK" + LocalDateTime.now().format(DTF)
                + (int)(Math.random() * 1000));
        check.setCheckDate(LocalDate.now());
        check.setTotalItems(items.size());
        check.setDiffItems(0);
        check.setProfitQty(0);
        check.setLossQty(0);
        check.setOperatorId(operatorId);
        check.setOperatorName(operatorName);
        check.setStatus(confirm ? 2 : 1);
        check.setRemark(remark);
        checkMapper.insert(check);

        if (!confirm) {
            Map<String, Object> result = new HashMap<>();
            result.put("check", check);
            return Result.success("已保存为草稿", result);
        }

        int diffCount = 0, profit = 0, loss = 0;
        List<StockRecord> records = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Long productId = ((Number) it.get("productId")).longValue();
            int sysQty = ((Number) it.get("systemQty")).intValue();
            int actQty = ((Number) it.get("actualQty")).intValue();
            int diff = actQty - sysQty;
            if (diff == 0) continue;
            diffCount++;
            String batchNo = (String) it.get("batchNo");
            StockInventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<StockInventory>()
                    .eq(StockInventory::getProductId, productId)
                    .eq(StockInventory::getStoreId, storeId)
                    .eq(batchNo != null, StockInventory::getBatchNo, batchNo));
            if (inv == null) continue;

            // 二次确认无差异生成空流水：CAS 改库存为 actQty；必须 quantity + diff >= 0
            int rows = inventoryMapper.casAdjust(inv.getId(), inv.getVersion(), diff);
            if (rows == 0) {
                throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
                        "盘点 CAS 失败：商品 " + productId + " 并发更新冲突");
            }

            StockRecord rec = new StockRecord();
            rec.setTenantId(tenantId);
            rec.setStoreId(storeId);
            rec.setProductId(productId);
            rec.setBatchNo(inv.getBatchNo());
            rec.setType(3);
            rec.setQuantity(diff);
            rec.setBeforeQty(sysQty);
            rec.setAfterQty(actQty);
            rec.setOperatorId(operatorId);
            rec.setOperatorName(operatorName);
            rec.setRemark("盘点 " + check.getCheckNo() + " " + (diff > 0 ? "盘盈" : "盘亏") + " " + Math.abs(diff));
            rec.setRecordTime(LocalDateTime.now());
            recordMapper.insert(rec);
            records.add(rec);

            if (diff > 0) profit += diff; else loss += -diff;
        }

        check.setDiffItems(diffCount);
        check.setProfitQty(profit);
        check.setLossQty(loss);
        if (diffCount == 0) {
            // 二次确认无差异生成空流水：所有明细都无差异，不写 stock_record
            check.setRemark((remark == null ? "" : remark) + "（无差异）");
        }
        checkMapper.updateById(check);

        Map<String, Object> result = new HashMap<>();
        result.put("check", check);
        result.put("records", records);
        return Result.success("盘点完成", result);
    }

    @GetMapping("/check/page")
    @RequirePermission("stock:check")
    public Result<Page<Map<String, Object>>> checkPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String keyword) {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT c.*, s.store_name " +
            "FROM stock_check c LEFT JOIN pos_store s ON c.store_id = s.id " +
            "WHERE c.deleted = 0 AND c.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND c.store_id = ? "); args.add(storeId); }
        if (status != null) { sql.append("AND c.status = ? "); args.add(status); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND c.check_date >= ? "); args.add(dateFrom); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND c.check_date <= ? "); args.add(dateTo); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (c.check_no LIKE ? OR c.operator_name LIKE ? OR c.remark LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY c.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM stock_check WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tid);
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (dateFrom != null && !dateFrom.isEmpty()) { csql.append("AND check_date >= ? "); cargs.add(dateFrom); }
        if (dateTo != null && !dateTo.isEmpty()) { csql.append("AND check_date <= ? "); cargs.add(dateTo); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (check_no LIKE ? OR operator_name LIKE ? OR remark LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        // 状态码转描述 + 计算差异金额 / 差异率 / 确认时长
        records.forEach(r -> {
            Integer st = (Integer) r.get("status");
            String desc; String type;
            if (st == null) { desc = "草稿"; type = "info"; }
            else if (st == 1) { desc = "进行中"; type = "warning"; }
            else if (st == 2) { desc = "已完成"; type = "success"; }
            else if (st == 3) { desc = "已作废"; type = "danger"; }
            else { desc = "未知"; type = "info"; }
            r.put("statusDesc", desc);
            r.put("statusTag", type);

            // 差异金额 = 盘盈数 - 盘亏数（单位成本 = 商品平均价，按 record 表汇总）
            String checkNo = (String) r.get("check_no");
            Long storeId2 = r.get("store_id") == null ? null : ((Number) r.get("store_id")).longValue();
            Double diffAmount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(r.quantity * IFNULL(p.cost_price,0)),0) " +
                "FROM stock_record r LEFT JOIN stock_product p ON r.product_id = p.id " +
                "WHERE r.deleted = 0 AND r.type = 3 AND r.remark LIKE ? AND r.store_id = ?",
                Double.class, "%" + checkNo + "%", storeId2);
            r.put("diffAmount", diffAmount == null ? 0 : diffAmount);

            // 差异率 = |盈亏数| / max(总项,1)
            Integer totalItems = (Integer) r.get("total_items");
            Integer profit = ((Number) r.get("profit_qty")).intValue();
            Integer loss = ((Number) r.get("loss_qty")).intValue();
            double diffRate = (totalItems == null || totalItems == 0) ? 0
                    : (profit + loss) * 1.0 / totalItems;
            r.put("diffRate", diffRate);

            // 确认时长（分钟，仅已完成）
            Object confirmedTime = r.get("confirmed_time");
            Object createTime = r.get("create_time");
            if (confirmedTime != null && createTime != null) {
                long mins = java.time.Duration.between(
                    ((java.sql.Timestamp) createTime).toLocalDateTime(),
                    ((java.sql.Timestamp) confirmedTime).toLocalDateTime()
                ).toMinutes();
                r.put("durationMin", mins);
            } else {
                r.put("durationMin", null);
            }
        });

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/check/stats")
    @RequirePermission("stock:stat")
    public Result<Map<String, Object>> checkStats(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT " +
            "COUNT(*) total_checks, " +
            "SUM(CASE WHEN status=2 THEN 1 ELSE 0 END) confirmed_checks, " +
            "SUM(CASE WHEN status=1 THEN 1 ELSE 0 END) draft_checks, " +
            "SUM(CASE WHEN status=3 THEN 1 ELSE 0 END) cancelled_checks, " +
            "SUM(diff_items) diff_items, " +
            "SUM(profit_qty) profit_qty, " +
            "SUM(loss_qty) loss_qty " +
            "FROM stock_check WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND check_date >= ? "); args.add(dateFrom); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND check_date <= ? "); args.add(dateTo); }
        Map<String, Object> base = jdbc.queryForMap(sql.toString(), args.toArray());

        // 今日新增
        StringBuilder sql2 = new StringBuilder(
            "SELECT COUNT(*) today_count FROM stock_check WHERE deleted=0 AND tenant_id=? AND DATE(create_time)=CURDATE() ");
        List<Object> args2 = new ArrayList<>();
        args2.add(tid);
        if (storeId != null) { sql2.append("AND store_id = ? "); args2.add(storeId); }
        Map<String, Object> today = jdbc.queryForMap(sql2.toString(), args2.toArray());

        // 异常单数：有差异项的
        StringBuilder sql3 = new StringBuilder(
            "SELECT COUNT(*) abnormal FROM stock_check WHERE deleted=0 AND tenant_id=? AND diff_items > 0 ");
        List<Object> args3 = new ArrayList<>();
        args3.add(tid);
        if (storeId != null) { sql3.append("AND store_id = ? "); args3.add(storeId); }
        Map<String, Object> abnormal = jdbc.queryForMap(sql3.toString(), args3.toArray());

        Map<String, Object> result = new HashMap<>();
        result.putAll(base);
        result.putAll(today);
        result.putAll(abnormal);
        return Result.success(Keys.camel(result));
    }

    @GetMapping("/check/export")
    @RequirePermission("stock:export")
    public ResponseEntity<byte[]> exportCheck(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String keyword) throws Exception {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT c.check_no, c.check_date, s.store_name, c.total_items, c.diff_items, " +
            "c.profit_qty, c.loss_qty, c.operator_name, c.status, c.remark, c.create_time " +
            "FROM stock_check c LEFT JOIN pos_store s ON c.store_id = s.id " +
            "WHERE c.deleted = 0 AND c.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND c.store_id = ? "); args.add(storeId); }
        if (status != null) { sql.append("AND c.status = ? "); args.add(status); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND c.check_date >= ? "); args.add(dateFrom); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND c.check_date <= ? "); args.add(dateTo); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (c.check_no LIKE ? OR c.operator_name LIKE ? OR c.remark LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY c.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // R2.0-D T-D05：导出 xlsx，租户水印 + 审计
        String tenantName = resolveTenantName();
        String[] headers = {"盘点单号", "盘点日期", "门店", "商品数", "差异项",
                "盘盈", "盘亏", "操作人", "状态", "备注", "创建时间"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Integer st = (Integer) r.get("status");
            String statusDesc = st == null ? "草稿" : st == 1 ? "进行中" : st == 2 ? "已完成" : st == 3 ? "已作废" : "未知";
            body.add(Arrays.asList(
                    XlsxExportUtil.str(r.get("check_no")),
                    XlsxExportUtil.str(r.get("check_date")),
                    XlsxExportUtil.str(r.get("store_name")),
                    r.get("total_items"),
                    r.get("diff_items"),
                    r.get("profit_qty"),
                    r.get("loss_qty"),
                    XlsxExportUtil.str(r.get("operator_name")),
                    statusDesc,
                    XlsxExportUtil.str(r.get("remark")),
                    XlsxExportUtil.str(r.get("create_time"))
            ));
        }
        byte[] bytes = XlsxExportUtil.export(
                "盘点记录",
                "# 盘点记录 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用",
                headers, body);

        String filename = "盘点记录_" + LocalDate.now() + ".xlsx";
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers2.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        headers2.add("X-Tenant-Name",
                new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers2).body(bytes);
    }

    // 确认盘点（草稿 → 已完成）R2.0-B：状态机 1→2
    @PostMapping("/check/confirm/{id}")
    @RequirePermission("stock:check")
    @Transactional(rollbackFor = Exception.class)
    public Result<StockCheck> confirmCheck(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long tid = tenantId();
        StockCheck check = checkMapper.selectById(id);
        if (check == null || !tid.equals(check.getTenantId())) throw new BusinessException(BizCode.NOT_FOUND, "盘点单不存在");
        if (check.getStatus() != null && check.getStatus() == 2) {
            throw new BusinessException(BizCode.CONFLICT, "该盘点单已完成，不能重复确认");
        }
        if (check.getStatus() != null && check.getStatus() == 3) {
            throw new BusinessException(BizCode.CONFLICT, "已作废的盘点单不能确认");
        }
        // 状态机：从草稿(1) → 已完成(2)
        Long confirmedBy = body != null && body.get("confirmedBy") != null
                ? ((Number) body.get("confirmedBy")).longValue() : 1L;
        String confirmedByName = body != null && body.get("confirmedByName") != null
                ? body.get("confirmedByName").toString() : "admin";
        check.setStatus(2);
        check.setConfirmedBy(confirmedBy);
        check.setConfirmedByName(confirmedByName);
        check.setConfirmedTime(LocalDateTime.now());
        // P2-11 IND-05：盘点确认后强制双人核销
        // 主核销人 = confirmedBy（盘点提交人），复核人留空待补
        check.setMainVerifierId(confirmedBy);
        check.setMainVerifierName(confirmedByName);
        check.setDualVerifyStatus("PENDING");
        checkMapper.updateById(check);

        // 写双人核销记录（金额不适用盘点，用 null）
        dualVerifyService.createPendingRecordIfNeeded(
                null, check.getId(),          // stockRecordId=null, checkId
                "CHECK", "3",                 // verifyType=CHECK, recordType=盘点=3
                confirmedBy, confirmedByName,
                check.getStoreId(), null);    // totalAmount=null（盘点不看金额）

        return Result.success("已确认", check);
    }

    // 盘点详情（含明细）
    @GetMapping("/check/{id:\\d+}")
    @RequirePermission("stock:check")
    public Result<Map<String, Object>> checkDetail(@PathVariable Long id) {
        Long tid = tenantId();
        StockCheck check = checkMapper.selectById(id);
        if (check == null || !tid.equals(check.getTenantId())) return Result.error("盘点单不存在");

        // 简化方案：从 stock_record remark 里匹配 "盘点 {checkNo}"
        String sql = "SELECT r.*, p.product_code, p.product_name, p.unit " +
                     "FROM stock_record r JOIN stock_product p ON r.product_id = p.id " +
                     "WHERE r.type = 3 AND r.remark LIKE ? ORDER BY r.id ASC";
        List<Map<String, Object>> items = jdbc.queryForList(sql, "%盘点 " + check.getCheckNo() + "%");
        if (items.isEmpty()) {
            items = jdbc.queryForList(sql, "%" + check.getCheckNo() + "%");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("check", check);
        result.put("items", Keys.camel(items));
        return Result.success(result);
    }

    // 作废盘点单 R2.0-B：状态机 1→3（草稿才可作废）
    @PostMapping("/check/cancel/{id}")
    @RequirePermission("stock:check")
    @Transactional(rollbackFor = Exception.class)
    public Result<StockCheck> cancelCheck(@PathVariable Long id) {
        Long tid = tenantId();
        StockCheck check = checkMapper.selectById(id);
        if (check == null || !tid.equals(check.getTenantId())) throw new BusinessException(BizCode.NOT_FOUND, "盘点单不存在");
        if (check.getStatus() != null && check.getStatus() == 2) {
            throw new BusinessException(BizCode.CONFLICT, "已完成的盘点单不能直接作废，请使用冲正接口");
        }
        if (check.getStatus() != null && check.getStatus() == 3) {
            throw new BusinessException(BizCode.CONFLICT, "盘点单已作废");
        }
        check.setStatus(3);
        checkMapper.updateById(check);
        return Result.success("已作废", check);
    }

    /**
     * 盘点冲正（R2.0-B T-B06）：已完成(2)的盘点单 → 写抵销流水回滚差异 → 状态改为已冲正(3)。
     * 逻辑：
     * 1. 查询该盘点产生的所有 stock_record（type=3, remark 含 checkNo）
     * 2. 按 batchNo + productId 查找对应库存批次，用 casAdjust 回滚 quantity
     * 3. 写抵销流水（type=6）标记冲正记录
     */
    @PostMapping("/check/reverse/{id}")
    @RequirePermission("stock:check")
    @Transactional(rollbackFor = Exception.class)
    public Result<StockCheck> reverseCheck(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Long tid = tenantId();
        StockCheck check = checkMapper.selectById(id);
        if (check == null || !tid.equals(check.getTenantId())) throw new BusinessException(BizCode.NOT_FOUND, "盘点单不存在");
        if (check.getStatus() == null || check.getStatus() != 2) {
            throw new BusinessException(BizCode.CONFLICT, "只有已完成的盘点单才能冲正");
        }

        // 1. 查找盘点产生的 stock_record（type=3）
        String sql = "SELECT r.*, p.product_code, p.product_name " +
                     "FROM stock_record r JOIN stock_product p ON r.product_id = p.id " +
                     "WHERE r.deleted = 0 AND r.type = 3 AND r.remark LIKE ? ORDER BY r.id ASC";
        List<Map<String, Object>> checkRecords = jdbc.queryForList(
                sql, "%" + check.getCheckNo() + "%");

        if (checkRecords.isEmpty()) {
            // 无盘点流水，直接改状态
            check.setStatus(3);
            check.setRemark((check.getRemark() == null ? "" : check.getRemark()) + " [已冲正-无关联流水]");
            checkMapper.updateById(check);
            return Result.success("冲正完成（无关联流水）", check);
        }

        // 2. 遍历每条盘点记录，写抵销流水 + 回滚库存批次
        for (Map<String, Object> r : checkRecords) {
            Long productId = ((Number) r.get("product_id")).longValue();
            String batchNo = (String) r.get("batch_no");
            Integer diffQty = r.get("quantity") != null ? ((Number) r.get("quantity")).intValue() : 0;

            // 2a. 找到当前有效的库存批次（按 batchNo 匹配）
            StockInventory batch = null;
            if (batchNo != null && !batchNo.isEmpty()) {
                batch = inventoryMapper.selectOne(new LambdaQueryWrapper<StockInventory>()
                        .eq(StockInventory::getProductId, productId)
                        .eq(StockInventory::getBatchNo, batchNo)
                        .eq(StockInventory::getDeleted, 0));
            }
            // 如果按批次号找不到，尝试按 productId + storeId 找任意一个（兜底）
            if (batch == null) {
                batch = inventoryMapper.selectOne(new LambdaQueryWrapper<StockInventory>()
                        .eq(StockInventory::getProductId, productId)
                        .eq(StockInventory::getStoreId, check.getStoreId())
                        .eq(StockInventory::getDeleted, 0)
                        .last("LIMIT 1"));
            }

            // 2b. 用 casAdjust 回滚差异（盘盈 → 扣回，盘亏 → 补回）
            if (batch != null) {
                int reversed = inventoryMapper.casAdjust(batch.getId(), batch.getVersion(), -diffQty);
                if (reversed == 0) {
                    // 批次已变化（并发修改或软删除），改为只写抵销流水
                    writeReversalRecord(check, productId, batchNo, diffQty,
                            "冲正-批次不可用(id=" + batch.getId() + ")");
                    continue;
                }
            }

            // 2c. 写抵销流水（type=6 冲正，quantity 取负）
            writeReversalRecord(check, productId, batchNo, diffQty, null);
        }

        // 3. 改盘点单状态为已冲正(3)
        check.setStatus(3);
        check.setRemark((check.getRemark() == null ? "" : check.getRemark()) + " [已冲正]");
        checkMapper.updateById(check);

        String reversedByName = body != null && body.get("reversedByName") != null
                ? body.get("reversedByName").toString() : "admin";
        return Result.success("冲正完成，共回滚 " + checkRecords.size() + " 条库存记录", check);
    }

    private void writeReversalRecord(StockCheck check, Long productId, String batchNo,
                                     Integer diffQty, String extraRemark) {
        StockRecord rec = new StockRecord();
        rec.setTenantId(check.getTenantId());
        rec.setStoreId(check.getStoreId());
        rec.setProductId(productId);
        rec.setBatchNo(batchNo);
        rec.setType(6); // 6=冲正
        rec.setQuantity(-diffQty); // 取负：盘盈回扣，盘亏回补
        rec.setRemark("冲正 " + check.getCheckNo()
                + (extraRemark != null ? " " + extraRemark : ""));
        rec.setOperatorId(check.getOperatorId());
        rec.setOperatorName(check.getOperatorName());
        rec.setRecordTime(LocalDateTime.now());
        recordMapper.insert(rec);
    }

    // ==================== 出入库记录流水 ====================

    @GetMapping("/record/page")
    @RequirePermission("stock:record")
    public Result<Page<Map<String, Object>>> recordPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT r.*, p.product_code, p.product_name, p.unit, o.order_no " +
            "FROM stock_record r " +
            "JOIN stock_product p ON r.product_id = p.id " +
            "LEFT JOIN pos_order o ON r.order_id = o.id " +
            "WHERE r.deleted = 0 AND r.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND r.store_id = ? "); args.add(storeId); }
        if (productId != null) { sql.append("AND r.product_id = ? "); args.add(productId); }
        if (type != null) { sql.append("AND r.type = ? "); args.add(type); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND r.record_time >= ? "); args.add(dateFrom + " 00:00:00"); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND r.record_time <= ? "); args.add(dateTo + " 23:59:59"); }
        sql.append("ORDER BY r.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM stock_record WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tid);
        if (storeId != null) { csql.append("AND store_id = ? "); cargs.add(storeId); }
        if (productId != null) { csql.append("AND product_id = ? "); cargs.add(productId); }
        if (type != null) { csql.append("AND type = ? "); cargs.add(type); }
        if (dateFrom != null && !dateFrom.isEmpty()) { csql.append("AND record_time >= ? "); cargs.add(dateFrom + " 00:00:00"); }
        if (dateTo != null && !dateTo.isEmpty()) { csql.append("AND record_time <= ? "); cargs.add(dateTo + " 23:59:59"); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    /**
     * R2.0-D：出入库流水记录 CSV 导出（含租户水印 + 审计日志）
     */
    @GetMapping("/record/export")
    @RequirePermission("stock:export")
    public ResponseEntity<byte[]> exportRecords(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) throws Exception {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT r.record_time, r.type, p.product_code, p.product_name, p.unit, " +
            "r.batch_no, r.quantity, r.before_qty, r.after_qty, r.unit_price, r.total_amount, " +
            "r.supplier, o.order_no, r.operator_name, r.remark " +
            "FROM stock_record r " +
            "JOIN stock_product p ON r.product_id = p.id " +
            "LEFT JOIN pos_order o ON r.order_id = o.id " +
            "WHERE r.deleted = 0 AND r.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND r.store_id = ? "); args.add(storeId); }
        if (productId != null) { sql.append("AND r.product_id = ? "); args.add(productId); }
        if (type != null) { sql.append("AND r.type = ? "); args.add(type); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND r.record_time >= ? "); args.add(dateFrom + " 00:00:00"); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND r.record_time <= ? "); args.add(dateTo + " 23:59:59"); }
        sql.append("ORDER BY r.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // R2.0-D T-D05：导出 xlsx，租户水印 + @RequirePermission("stock:export") 审计
        String tenantName = resolveTenantName();
        String[] headers = {"时间", "类型", "商品编码", "商品名称", "单位", "批次号",
                "数量(>0入,<0出)", "操作前", "操作后", "单价", "金额",
                "供应商", "订单号", "操作人", "备注"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Integer t = (Integer) r.get("type");
            String typeName = t == null ? "" : switch (t) {
                case 1 -> "入库"; case 2 -> "出库"; case 3 -> "盘点";
                case 4 -> "领用"; case 5 -> "报损"; case 6 -> "采购入库"; default -> "其他";
            };
            body.add(Arrays.asList(
                    XlsxExportUtil.str(r.get("record_time")),
                    typeName,
                    XlsxExportUtil.str(r.get("product_code")),
                    XlsxExportUtil.str(r.get("product_name")),
                    XlsxExportUtil.str(r.get("unit")),
                    XlsxExportUtil.str(r.get("batch_no")),
                    r.get("quantity"),
                    r.get("before_qty"),
                    r.get("after_qty"),
                    r.get("unit_price"),
                    r.get("total_amount"),
                    XlsxExportUtil.str(r.get("supplier")),
                    XlsxExportUtil.str(r.get("order_no")),
                    XlsxExportUtil.str(r.get("operator_name")),
                    XlsxExportUtil.str(r.get("remark"))
            ));
        }
        byte[] bytes = XlsxExportUtil.export(
                "出入库流水",
                "# 出入库流水 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用",
                headers, body);

        String filename = "出入库流水_" + LocalDate.now() + ".xlsx";
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers2.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        headers2.add("X-Tenant-Name",
                new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers2).body(bytes);
    }

    /** 通用：取租户名（用于水印） */
    private String resolveTenantName() {
        try {
            Long tid = TenantContextHolder.get().getTenantId();
            if (tid != null) {
                List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT tenant_name FROM sys_tenant WHERE id = ? LIMIT 1", tid);
                if (!t.isEmpty()) return String.valueOf(t.get(0).get("tenant_name"));
            }
        } catch (Exception ignored) {}
        return "默认租户";
    }

    @GetMapping("/consumption/page")
    @RequirePermission("stock:consumption")
    public Result<Map<String, Object>> consumptionPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        Long tid = tenantId();
        // 消耗 = 出库/领用/报损 且 有关联订单
        StringBuilder sql = new StringBuilder(
            "SELECT r.id, r.record_time, r.product_id, r.batch_no, r.quantity, r.remark, r.order_id, " +
            "p.product_code, p.product_name, p.unit, p.cost_price, " +
            "o.order_no, o.member_name AS customer_name, " +
            "(SELECT i.service_name FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 ORDER BY i.id ASC LIMIT 1) AS service_name, " +
            "(SELECT i.employee_id FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 ORDER BY i.id ASC LIMIT 1) AS technician_id, " +
            "(SELECT i.employee_name FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 ORDER BY i.id ASC LIMIT 1) AS technician_name " +
            "FROM stock_record r " +
            "JOIN stock_product p ON r.product_id = p.id " +
            "LEFT JOIN pos_order o ON r.order_id = o.id " +
            "WHERE r.deleted = 0 AND r.tenant_id = ? AND r.type IN (2,4,5) AND r.order_id IS NOT NULL ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND r.store_id = ? "); args.add(storeId); }
        if (productId != null) { sql.append("AND r.product_id = ? "); args.add(productId); }
        if (orderId != null) { sql.append("AND r.order_id = ? "); args.add(orderId); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND r.record_time >= ? "); args.add(dateFrom + " 00:00:00"); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND r.record_time <= ? "); args.add(dateTo + " 23:59:59"); }
        if (technicianId != null) {
            sql.append("AND EXISTS (SELECT 1 FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 AND i.employee_id = ?) ");
            args.add(technicianId);
        }
        sql.append("ORDER BY r.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM stock_record r WHERE r.deleted = 0 AND r.tenant_id = ? AND r.type IN (2,4,5) AND r.order_id IS NOT NULL ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tid);
        if (storeId != null) { csql.append("AND r.store_id = ? "); cargs.add(storeId); }
        if (productId != null) { csql.append("AND r.product_id = ? "); cargs.add(productId); }
        if (orderId != null) { csql.append("AND r.order_id = ? "); cargs.add(orderId); }
        if (dateFrom != null && !dateFrom.isEmpty()) { csql.append("AND r.record_time >= ? "); cargs.add(dateFrom + " 00:00:00"); }
        if (dateTo != null && !dateTo.isEmpty()) { csql.append("AND r.record_time <= ? "); cargs.add(dateTo + " 23:59:59"); }
        if (technicianId != null) {
            csql.append("AND EXISTS (SELECT 1 FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 AND i.employee_id = ?) ");
            cargs.add(technicianId);
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        // 统计（仅当为第一页且无筛选时计算）
        Map<String, Object> stats = new HashMap<>();
        if (pageNum == 1 && productId == null && orderId == null && technicianId == null) {
            StringBuilder ssql = new StringBuilder(
                "SELECT " +
                "COALESCE(SUM(CASE WHEN DATE(record_time)=CURDATE() THEN 1 ELSE 0 END),0) today_count, " +
                "COALESCE(SUM(CASE WHEN DATE_FORMAT(record_time,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m') THEN quantity*IFNULL((SELECT cost_price FROM stock_product WHERE id=stock_record.product_id),0) ELSE 0 END),0) month_amount, " +
                "COUNT(DISTINCT order_id) order_count, " +
                "(SELECT COUNT(DISTINCT i.service_id) FROM pos_order_item i WHERE i.deleted=0 AND i.order_id IN (SELECT DISTINCT order_id FROM stock_record WHERE deleted=0 AND type IN (2,4,5) AND order_id IS NOT NULL AND order_id IS NOT NULL)) service_count " +
                "FROM stock_record WHERE deleted = 0 AND tenant_id = ? AND type IN (2,4,5) AND order_id IS NOT NULL ");
            List<Object> sargs = new ArrayList<>();
            sargs.add(tid);
            if (storeId != null) { ssql.append("AND store_id = ? "); sargs.add(storeId); }
            stats = jdbc.queryForMap(ssql.toString(), sargs.toArray());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", Keys.camel(records));
        result.put("total", total);
        result.put("stats", Keys.camel(stats));
        return Result.success(result);
    }

    @GetMapping("/consumption/order/{orderId}")
    @RequirePermission("stock:consumption")
    public Result<List<Map<String, Object>>> consumptionByOrder(@PathVariable Long orderId) {
        Long tid = tenantId();
        String sql =
            "SELECT r.id, r.record_time, r.product_id, r.batch_no, r.quantity, r.remark, r.order_id, " +
            "p.product_code, p.product_name, p.unit, p.cost_price " +
            "FROM stock_record r " +
            "JOIN stock_product p ON r.product_id = p.id " +
            "WHERE r.deleted = 0 AND r.tenant_id = ? AND r.type IN (2,4,5) AND r.order_id = ? " +
            "ORDER BY r.id DESC";
        List<Map<String, Object>> records = jdbc.queryForList(sql, tid, orderId);
        return Result.success(Keys.camel(records));
    }

    /**
     * R2.0-D：手动重放订单消耗（补救用：订单已支付但 BOM 库存未扣的场景）
     */
    @PostMapping("/consumption/replay/{orderId}")
    @RequirePermission("stock:outbound")
    public Result<Map<String, Object>> replayConsumption(@PathVariable Long orderId) {
        int n = consumptionService.consumeByOrder(orderId);
        Map<String, Object> res = new HashMap<>();
        res.put("consumed", n);
        return Result.success("已重放，" + n + " 条耗材写入", res);
    }

    /**
     * R2.0-D：服务的 BOM（为前端"服务→耗材"展示用）
     */
    @GetMapping("/consumption/bom/{serviceId}")
    @RequirePermission("stock:consumption")
    public Result<List<Map<String, Object>>> bomByService(@PathVariable Long serviceId) {
        return Result.success(Keys.camel(bomMapper.listBomByService(serviceId)));
    }

    /**
     * R2.0-D：今日/近 30 天消耗汇总（消耗闭环的关键指标）
     */
    @GetMapping("/consumption/overview")
    @RequirePermission("stock:consumption")
    public Result<Map<String, Object>> consumptionOverview(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "30") int days) {
        Long tid = tenantId();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(SUM(CASE WHEN DATE(record_time)=CURDATE() THEN quantity ELSE 0 END),0) today_count, " +
            "COALESCE(SUM(CASE WHEN DATE(record_time)=CURDATE() THEN ABS(quantity)*IFNULL((SELECT cost_price FROM stock_product WHERE id=stock_record.product_id),0) ELSE 0 END),0) today_amount, " +
            "COALESCE(SUM(CASE WHEN record_time >= ? THEN ABS(quantity) ELSE 0 END),0) recent_count, " +
            "COALESCE(SUM(CASE WHEN record_time >= ? THEN ABS(quantity)*IFNULL((SELECT cost_price FROM stock_product WHERE id=stock_record.product_id),0) ELSE 0 END),0) recent_amount, " +
            "COUNT(DISTINCT order_id) order_count, " +
            "COUNT(DISTINCT product_id) product_count " +
            "FROM stock_record WHERE deleted = 0 AND tenant_id = ? AND type IN (2,4,5) ");
        List<Object> args = new ArrayList<>();
        args.add(since); args.add(since); args.add(tid);
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args.toArray());
        return Result.success(Keys.camel(row));
    }

    /**
     * R2.0-D：商品 BOM（哪些服务用到了该耗材）
     */
    @GetMapping("/consumption/bom-product/{productId}")
    @RequirePermission("stock:consumption")
    public Result<List<Map<String, Object>>> bomByProduct(@PathVariable Long productId) {
        String sql = "SELECT m.id, m.service_id, m.quantity, m.unit, m.is_required, " +
                "       s.service_code, s.service_name " +
                "FROM biz_service_material m " +
                "JOIN biz_service s ON s.id = m.service_id AND s.deleted = 0 " +
                "WHERE m.deleted = 0 AND m.product_id = ?";
        return Result.success(Keys.camel(jdbc.queryForList(sql, productId)));
    }

    /**
     * R2.0-D：消耗记录 CSV 导出（含订单 / 服务 / 客户 / 商品 / 批次 / 数量 / 成本）
     */
    @GetMapping("/consumption/export")
    @RequirePermission("stock:export")
    public ResponseEntity<byte[]> exportConsumption(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) throws Exception {
        StringBuilder sql = new StringBuilder(
            "SELECT r.record_time, o.order_no, " +
            "(SELECT i.service_name FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 ORDER BY i.id ASC LIMIT 1) service_name, " +
            "(SELECT i.employee_name FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 ORDER BY i.id ASC LIMIT 1) technician_name, " +
            "o.member_name customer_name, p.product_code, p.product_name, p.unit, " +
            "r.batch_no, r.quantity, p.cost_price, " +
            "(r.quantity * p.cost_price) AS cost_total, r.remark " +
            "FROM stock_record r " +
            "JOIN stock_product p ON r.product_id = p.id " +
            "LEFT JOIN pos_order o ON r.order_id = o.id " +
            "WHERE r.deleted = 0 AND r.type IN (2,4,5) AND r.order_id IS NOT NULL ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND r.store_id = ? "); args.add(storeId); }
        if (productId != null) { sql.append("AND r.product_id = ? "); args.add(productId); }
        if (orderId != null) { sql.append("AND r.order_id = ? "); args.add(orderId); }
        if (dateFrom != null && !dateFrom.isEmpty()) { sql.append("AND r.record_time >= ? "); args.add(dateFrom + " 00:00:00"); }
        if (dateTo != null && !dateTo.isEmpty()) { sql.append("AND r.record_time <= ? "); args.add(dateTo + " 23:59:59"); }
        if (technicianId != null) {
            sql.append("AND EXISTS (SELECT 1 FROM pos_order_item i WHERE i.order_id = r.order_id AND i.deleted=0 AND i.employee_id = ?) ");
            args.add(technicianId);
        }
        sql.append("ORDER BY r.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // R2.0-D T-D05：导出 xlsx，租户水印 + 审计
        String tenantName = resolveTenantName();
        String[] headers = {"消耗时间", "订单号", "服务项目", "技师", "客户",
                "商品编码", "商品名称", "单位", "批次号", "数量",
                "成本单价", "消耗成本", "备注"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            body.add(Arrays.asList(
                    XlsxExportUtil.str(r.get("record_time")),
                    XlsxExportUtil.str(r.get("order_no")),
                    XlsxExportUtil.str(r.get("service_name")),
                    XlsxExportUtil.str(r.get("technician_name")),
                    XlsxExportUtil.str(r.get("customer_name")),
                    XlsxExportUtil.str(r.get("product_code")),
                    XlsxExportUtil.str(r.get("product_name")),
                    XlsxExportUtil.str(r.get("unit")),
                    XlsxExportUtil.str(r.get("batch_no")),
                    r.get("quantity"),
                    r.get("cost_price"),
                    r.get("cost_total"),
                    XlsxExportUtil.str(r.get("remark"))
            ));
        }
        byte[] bytes = XlsxExportUtil.export(
                "库存消耗记录",
                "# 库存消耗记录 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用",
                headers, body);

        String filename = "库存消耗记录_" + LocalDate.now() + ".xlsx";
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers2.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        headers2.add("X-Tenant-Name",
                new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers2).body(bytes);
    }

    // ==================== 批次追溯 ====================

    @GetMapping("/trace/{productId}")
    @RequirePermission("stock:record")
    public Result<Map<String, Object>> trace(@PathVariable Long productId) {
        Long tid = tenantId();
        StockProduct p = productMapper.selectById(productId);
        if (p == null || !tid.equals(p.getTenantId())) return Result.error("商品不存在");
        List<StockInventory> invs = inventoryMapper.selectList(new LambdaQueryWrapper<StockInventory>()
                .eq(StockInventory::getProductId, productId)
                .eq(StockInventory::getTenantId, tid)
                .orderByAsc(StockInventory::getExpiryDate));
        List<StockRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<StockRecord>()
                .eq(StockRecord::getProductId, productId)
                .eq(StockRecord::getTenantId, tid)
                .orderByDesc(StockRecord::getId));
        Map<String, Object> result = new HashMap<>();
        result.put("product", p);
        result.put("inventory", invs);
        result.put("records", recs);
        return Result.success(result);
    }

    // ==================== 库存预警（按 alertType 维度的分页接口）====================

    @GetMapping("/alert/page")
    @RequirePermission("stock:alert")
    public Result<Page<Map<String, Object>>> alertPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long storeId) {
        Long tid = tenantId();
        LocalDate today = LocalDate.now();
        StringBuilder sql = new StringBuilder(
            "SELECT i.id, i.store_id, i.product_id, i.batch_no, i.quantity, " +
            "i.expiry_date, i.supplier, " +
            "p.product_code, p.product_name, p.category, p.unit, p.spec, " +
            "p.min_stock, p.max_stock, p.cost_price, p.sale_price, p.status AS product_status " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND p.status = 1 " +
            "AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(tid);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        if (category != null && !category.isEmpty()) { sql.append("AND p.category = ? "); args.add(category); }
        sql.append("ORDER BY i.quantity ASC, p.product_code ASC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 计算每行 alertType
        rows.forEach(r -> {
            int qty = ((Number) r.get("quantity")).intValue();
            int min = ((Number) r.get("min_stock")).intValue();
            int max = ((Number) r.get("max_stock")).intValue();
            String computed;
            if (qty <= 0) computed = "OUT";
            else if (qty < min) computed = "LOW";
            else if (qty > max) computed = "OVER";
            else computed = "OK";
            r.put("alertType", computed);
            r.put("quantity", qty);
            r.put("minStock", min);
            r.put("maxStock", max);
        });

        // 临期/过期 标记（独立 alertType）
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String at = (String) r.get("alertType");
            Object exp = r.get("expiry_date");
            if (exp != null) {
                LocalDate d = LocalDate.parse(exp.toString());
                if (d.isBefore(today)) {
                    r.put("alertType", "EXPIRED");
                } else if (!d.isAfter(today.plusDays(30))) {
                    // 临期覆盖（仅当不是 OUT/LOW/OVER 时才升级为 EXPIRING）
                    if (at == null || at.equals("OK") || at.equals("OUT") || at.equals("LOW") || at.equals("OVER")) {
                        r.put("alertType", "EXPIRING");
                    }
                }
            }
            enriched.add(r);
        }

        // 按 alertType 过滤（支持 CSV，如 OUT,LOW 表示缺货或低于最低）
        List<Map<String, Object>> filtered = enriched;
        if (alertType != null && !alertType.isEmpty()) {
            java.util.Set<String> allowed = new java.util.HashSet<>(
                    java.util.Arrays.asList(alertType.split(",")));
            filtered = enriched.stream()
                    .filter(r -> allowed.contains(r.get("alertType")))
                    .collect(Collectors.toList());
        }

        int total = filtered.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(filtered.size(), from + pageSize);
        List<Map<String, Object>> records = filtered.subList(from, to);

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/alert/stats")
    @RequirePermission("stock:alert")
    public Result<Map<String, Object>> alertStats(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long storeId) {        Long tid = tenantId();
        LocalDate today = LocalDate.now();
        StringBuilder sql = new StringBuilder(
            "SELECT i.quantity, p.min_stock, p.max_stock, i.expiry_date, p.category " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND p.status = 1 " +
            "AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(tid);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        if (category != null && !category.isEmpty()) { sql.append("AND p.category = ? "); args.add(category); }
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        long total = 0, out = 0, low = 0, over = 0, expiring = 0, expired = 0, normal = 0;
        for (Map<String, Object> r : rows) {
            total++;
            int qty = ((Number) r.get("quantity")).intValue();
            int min = ((Number) r.get("min_stock")).intValue();
            int max = ((Number) r.get("max_stock")).intValue();
            String at;
            if (qty <= 0) at = "OUT";
            else if (qty < min) at = "LOW";
            else if (qty > max) at = "OVER";
            else at = "OK";

            boolean isExp = false, isExpSoon = false;
            Object exp = r.get("expiry_date");
            if (exp != null) {
                LocalDate d = LocalDate.parse(exp.toString());
                if (d.isBefore(today)) { isExp = true; at = "EXPIRED"; }
                else if (!d.isAfter(today.plusDays(30))) { isExpSoon = true; }
            }

            switch (at) {
                case "OUT": out++; break;
                case "LOW": low++; break;
                case "OVER": over++; break;
                case "EXPIRED": expired++; break;
                default: normal++; break;
            }
            if (isExpSoon) expiring++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("out_of_stock", out);
        result.put("low_stock", low);
        result.put("over_stock", over);
        result.put("expiring", expiring);
        result.put("expired", expired);
        result.put("normal", normal);
        return Result.success(Keys.camel(result));
    }

    /**
     * R2.0-D：补货建议（基于近 N 天日均消耗 + 安全库存天数 + 当前缺口）
     * 公式：建议补货量 = max(0, ceil(日均消耗 × (安全库存天数) - 当前库存))
     * @param storeId 门店（可空）
     * @param days 回溯天数（默认 30）
     * @param safetyDays 安全库存天数（默认 14）
     */
    @GetMapping("/alert/replenish")
    @RequirePermission("stock:replenish")
    public Result<List<Map<String, Object>>> replenish(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "14") int safetyDays) {
        Long tid = tenantId();
        // 1) 候选商品：所有启用耗材
        // 2) 计算当前库存（按门店，门店为空则全部门店汇总）
        // 3) 计算近 N 天消耗（按门店）
        // 4) 输出建议补货量
        StringBuilder sql = new StringBuilder(
            "SELECT p.id AS product_id, p.product_code, p.product_name, p.category, p.unit, " +
            "       p.min_stock, p.max_stock, p.cost_price, p.sale_price, " +
            "       COALESCE((" +
            "           SELECT SUM(quantity) FROM stock_inventory ii " +
            "           WHERE ii.deleted = 0 AND ii.product_id = p.id AND ii.tenant_id = ? " +
            "           (invStoreFilter)" +
            "       ), 0) AS stock_qty, " +
            "       COALESCE((" +
            "           SELECT SUM(ABS(r.quantity)) FROM stock_record r " +
            "           WHERE r.deleted = 0 AND r.tenant_id = ? AND r.type IN (2,4,5) AND r.product_id = p.id " +
            "           AND r.record_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
            "           (recStoreFilter)" +
            "       ), 0) AS recent_consume, " +
            "       (SELECT MAX(ii.expiry_date) FROM stock_inventory ii " +
            "        WHERE ii.deleted = 0 AND ii.product_id = p.id AND ii.tenant_id = ? (invStoreFilterSub)) AS latest_expiry " +
            "FROM stock_product p " +
            "WHERE p.deleted = 0 AND p.status = 1 AND p.tenant_id = ? ");

        List<Object> args = new ArrayList<>();
        args.add(tid); // invFilter tenant
        args.add(tid); // recFilter tenant
        args.add(days);
        args.add(tid); // invFilterSub tenant
        args.add(tid); // main product tenant

        String invFilter = "";
        String recFilter = "";
        String invFilterSub = "";
        if (storeId != null) {
            invFilter = "AND ii.store_id = ?";
            recFilter = "AND r.store_id = ?";
            invFilterSub = "AND ii.store_id = ?";
        }
        String fullSql = sql.toString()
                .replace("(invStoreFilter)", invFilter)
                .replace("(recStoreFilter)", recFilter)
                .replace("(invStoreFilterSub)", invFilterSub);
        if (storeId != null) {
            args.add(storeId); // invFilter
            args.add(storeId); // recFilter
            args.add(storeId); // invFilterSub
        }
        fullSql += " ORDER BY stock_qty ASC, recent_consume DESC";

        List<Map<String, Object>> rows = jdbc.queryForList(fullSql, args.toArray());

        // 计算补货量 + 预警标记
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            int stock = ((Number) r.get("stock_qty")).intValue();
            int recent = ((Number) r.get("recent_consume")).intValue();
            int minStock = ((Number) r.get("min_stock")).intValue();
            int maxStock = ((Number) r.get("max_stock")).intValue();
            double avgDaily = days <= 0 ? 0 : recent * 1.0 / days;
            // 安全库存阈值 = 日均消耗 × 安全天数
            int safetyTarget = (int) Math.ceil(avgDaily * safetyDays);
            // 建议补货量 = max(0, max(safetyTarget, minStock) - stock)；不要超过 maxStock
            int target = Math.max(safetyTarget, minStock);
            int need = Math.max(0, target - stock);
            need = Math.min(need, Math.max(maxStock - stock, 0));
            // 标记紧急
            String urgency;
            if (stock <= 0 || (avgDaily > 0 && stock < avgDaily * 3)) urgency = "URGENT";
            else if (stock < minStock) urgency = "HIGH";
            else if (stock < safetyTarget) urgency = "MEDIUM";
            else urgency = "LOW";

            r.put("avgDaily", Math.round(avgDaily * 100.0) / 100.0);
            r.put("safetyTarget", safetyTarget);
            r.put("suggestQty", need);
            r.put("urgency", urgency);
            r.put("estCost", Math.round(need * ((Number) r.get("cost_price")).doubleValue() * 100.0) / 100.0);
            r.put("days", days);
            r.put("safetyDays", safetyDays);

            // 过滤掉"无消耗且库存正常"的无意义条目
            if (need > 0 || stock < minStock) {
                result.add(r);
            }
        }
        // 按紧急度排序：URGENT > HIGH > MEDIUM > LOW
        Map<String, Integer> order = new HashMap<>();
        order.put("URGENT", 0); order.put("HIGH", 1); order.put("MEDIUM", 2); order.put("LOW", 3);
        result.sort((a, b) -> {
            int oa = order.getOrDefault(a.get("urgency"), 9);
            int ob = order.getOrDefault(b.get("urgency"), 9);
            if (oa != ob) return Integer.compare(oa, ob);
            return Integer.compare(((Number) b.get("suggestQty")).intValue(),
                    ((Number) a.get("suggestQty")).intValue());
        });
        return Result.success(Keys.camel(result));
    }

    /**
     * R2.0-D：补货建议 CSV 导出（含水印行 + 审计）
     */
    @GetMapping("/alert/replenish/export")
    @RequirePermission("stock:replenish")
    public ResponseEntity<byte[]> exportReplenish(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "14") int safetyDays) throws Exception {
        // 重新计算 + 简易组织
        StringBuilder sql = new StringBuilder(
            "SELECT p.product_code, p.product_name, p.category, p.unit, p.min_stock, p.max_stock, " +
            "       IFNULL(SUM(i.quantity),0) AS stock_qty, " +
            "       IFNULL((SELECT SUM(ABS(r.quantity)) FROM stock_record r " +
            "               WHERE r.deleted=0 AND r.type IN (2,4,5) AND r.product_id = p.id " +
            "               AND r.record_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY)),0) AS recent_consume, " +
            "       p.cost_price " +
            "FROM stock_product p LEFT JOIN stock_inventory i ON i.product_id = p.id AND i.deleted = 0 " +
            "WHERE p.deleted = 0 AND p.status = 1 " +
            "GROUP BY p.id, p.product_code, p.product_name, p.category, p.unit, p.min_stock, p.max_stock, p.cost_price");
        List<Object> args = new ArrayList<>();
        args.add(days);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // R2.0-D T-D05：导出 xlsx，租户水印 + 审计
        // 取租户名(用于水印)
        String tenantName = "默认租户";
        try {
            Long tid = TenantContextHolder.get().getTenantId();
            if (tid != null) {
                List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT tenant_name FROM sys_tenant WHERE id = ? LIMIT 1", tid);
                if (!t.isEmpty()) tenantName = String.valueOf(t.get(0).get("tenant_name"));
            }
        } catch (Exception ignored) {}

        String[] headers = {"商品编码", "商品名称", "分类", "单位", "当前库存",
                "最低库存", "最高库存", "日均消耗", "安全阈值",
                "建议补货", "预计成本", "紧急度"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            int stock = XlsxExportUtil.integer(r.get("stock_qty"));
            int recent = XlsxExportUtil.integer(r.get("recent_consume"));
            int min = XlsxExportUtil.integer(r.get("min_stock"));
            int max = XlsxExportUtil.integer(r.get("max_stock"));
            double avgDaily = days <= 0 ? 0 : recent * 1.0 / days;
            int safetyTarget = (int) Math.ceil(avgDaily * safetyDays);
            int target = Math.max(safetyTarget, min);
            int need = Math.max(0, target - stock);
            need = Math.min(need, Math.max(max - stock, 0));
            if (need <= 0) continue;
            String urgency;
            if (stock <= 0 || (avgDaily > 0 && stock < avgDaily * 3)) urgency = "URGENT";
            else if (stock < min) urgency = "HIGH";
            else if (stock < safetyTarget) urgency = "MEDIUM";
            else urgency = "LOW";
            double cost = need * ((Number) r.get("cost_price")).doubleValue();
            double roundedCost = Math.round(cost * 100.0) / 100.0;
            body.add(Arrays.asList(
                    XlsxExportUtil.str(r.get("product_code")),
                    XlsxExportUtil.str(r.get("product_name")),
                    XlsxExportUtil.str(r.get("category")),
                    XlsxExportUtil.str(r.get("unit")),
                    stock, min, max,
                    Math.round(avgDaily * 100.0) / 100.0,
                    safetyTarget,
                    need,
                    roundedCost,
                    urgency
            ));
        }
        byte[] bytes = XlsxExportUtil.export(
                "补货建议",
                "# 补货建议 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用",
                headers, body);

        String filename = "补货建议_" + LocalDate.now() + ".xlsx";
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers2.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        headers2.add("X-Tenant-Name",
                new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers2).body(bytes);
    }

    // ==================== 导出 Excel（CSV 兼容） ====================

    @GetMapping("/inventory/export")
    @RequirePermission("stock:export")
    public ResponseEntity<byte[]> exportInventory(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String expiryStatus) throws Exception {
        StringBuilder sql = new StringBuilder(
            "SELECT p.product_code, p.product_name, p.category, p.spec, p.unit, " +
            "i.batch_no, i.quantity, p.min_stock, p.max_stock, " +
            "i.production_date, i.expiry_date, i.supplier, " +
            "p.cost_price, p.sale_price " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        if (category != null && !category.isEmpty()) { sql.append("AND p.category = ? "); args.add(category); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (p.product_code LIKE ? OR p.product_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY p.product_code, i.batch_no");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 应用 alertType / expiryStatus 过滤
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> filtered = rows.stream().filter(r -> {
            int qty = ((Number) r.get("quantity")).intValue();
            int min = ((Number) r.get("min_stock")).intValue();
            int max = ((Number) r.get("max_stock")).intValue();
            String computedAlert;
            if (qty <= 0) computedAlert = "OUT";
            else if (qty < min) computedAlert = "LOW";
            else if (qty > max) computedAlert = "OVER";
            else computedAlert = "OK";
            if (alertType != null && !alertType.isEmpty() && !computedAlert.equals(alertType)) return false;
            if (expiryStatus != null && !expiryStatus.isEmpty()) {
                Object exp = r.get("expiry_date");
                String computedExpiry;
                if (exp == null) computedExpiry = "OK";
                else {
                    LocalDate d = LocalDate.parse(exp.toString());
                    if (d.isBefore(today)) computedExpiry = "EXPIRED";
                    else if (!d.isAfter(today.plusDays(30))) computedExpiry = "EXPIRING";
                    else computedExpiry = "OK";
                }
                if (!computedExpiry.equals(expiryStatus)) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // R2.0-D T-D05：导出 xlsx，租户水印 + 审计（@SysOpLog 类级 + @RequirePermission）
        String tenantName = resolveTenantName();
        String[] headers = {"商品编码", "商品名称", "分类", "规格", "单位", "批次号",
                "库存数量", "最低库存", "最高库存", "生产日期", "过期日期",
                "供应商", "成本价", "售价", "库存状态"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : filtered) {
            int qty = XlsxExportUtil.integer(r.get("quantity"));
            int min = XlsxExportUtil.integer(r.get("min_stock"));
            int max = XlsxExportUtil.integer(r.get("max_stock"));
            String alert;
            if (qty <= 0) alert = "缺货";
            else if (qty < min) alert = "低于最低";
            else if (qty > max) alert = "超储";
            else alert = "正常";

            body.add(Arrays.asList(
                    XlsxExportUtil.str(r.get("product_code")),
                    XlsxExportUtil.str(r.get("product_name")),
                    XlsxExportUtil.str(r.get("category")),
                    XlsxExportUtil.str(r.get("spec")),
                    XlsxExportUtil.str(r.get("unit")),
                    XlsxExportUtil.str(r.get("batch_no")),
                    qty, min, max,
                    XlsxExportUtil.str(r.get("production_date")),
                    XlsxExportUtil.str(r.get("expiry_date")),
                    XlsxExportUtil.str(r.get("supplier")),
                    r.get("cost_price"),
                    r.get("sale_price"),
                    alert
            ));
        }
        byte[] bytes = XlsxExportUtil.export(
                "库存清单",
                "# 库存清单 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用",
                headers, body);

        String filename = "库存清单_" + LocalDate.now() + ".xlsx";
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers2.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        headers2.add("X-Tenant-Name",
                new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(headers2).body(bytes);
    }

    private static String csv(Object o) {
        if (o == null) return "";
        String s = o.toString();
        // CSV 字段：含 , 或 " 时整体加双引号，内部 " 转 ""
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ==================== 统计 ====================

    /**
     * 总览统计（卡片 + 分类占比）
     */
    @GetMapping("/stats")
    @RequirePermission("stock:stat")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long storeId) {
        Long tid = tenantId();
        // 1. 商品/库存概览
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) total_products, " +
            "SUM(CASE WHEN p.status=1 THEN 1 ELSE 0 END) active_products " +
            "FROM stock_product p WHERE p.deleted=0 AND p.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (storeId != null) { sql.append("AND p.store_id = ? "); args.add(storeId); }
        Map<String, Object> prods = jdbc.queryForMap(sql.toString(), args.toArray());

        // 2. 库存总量 + 总价值（成本口径）
        StringBuilder sql2 = new StringBuilder(
            "SELECT COALESCE(SUM(i.quantity), 0) total_qty, " +
            "COALESCE(SUM(i.quantity * p.cost_price), 0) total_value, " +
            "COUNT(DISTINCT i.product_id) covered_products, " +
            "SUM(CASE WHEN i.quantity <= 0 THEN 1 ELSE 0 END) out_of_stock, " +
            "SUM(CASE WHEN i.quantity > 0 AND i.quantity < p.min_stock THEN 1 ELSE 0 END) low_stock, " +
            "SUM(CASE WHEN i.quantity > p.max_stock THEN 1 ELSE 0 END) over_stock " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 " +
            "AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> args2 = new ArrayList<>();
        args2.add(tid); args2.add(tid);
        if (storeId != null) { sql2.append("AND i.store_id = ? "); args2.add(storeId); }
        Map<String, Object> invs = jdbc.queryForMap(sql2.toString(), args2.toArray());

        // 3. 本月消耗/出库（type=2,4,5 表示出/领用/消耗）
        StringBuilder sql3 = new StringBuilder(
            "SELECT " +
            "COALESCE(SUM(CASE WHEN type=1 AND DATE_FORMAT(record_time,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m') THEN quantity ELSE 0 END),0) month_in, " +
            "COALESCE(SUM(CASE WHEN type IN (2,4,5) AND DATE_FORMAT(record_time,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m') THEN quantity ELSE 0 END),0) month_out_count, " +
            "COALESCE(SUM(CASE WHEN type IN (2,4,5) AND DATE_FORMAT(record_time,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m') THEN total_amount ELSE 0 END),0) month_cost, " +
            "COUNT(*) record_total " +
            "FROM stock_record WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args3 = new ArrayList<>();
        args3.add(tid);
        if (storeId != null) { sql3.append("AND store_id = ? "); args3.add(storeId); }
        Map<String, Object> records = jdbc.queryForMap(sql3.toString(), args3.toArray());

        // 4. 分类库存占比（按库存价值）
        StringBuilder sql4 = new StringBuilder(
            "SELECT p.category, COALESCE(SUM(i.quantity * p.cost_price), 0) value, " +
            "COALESCE(SUM(i.quantity), 0) qty " +
            "FROM stock_inventory i JOIN stock_product p ON i.product_id = p.id " +
            "WHERE i.deleted = 0 AND p.deleted = 0 AND i.tenant_id = ? AND p.tenant_id = ? ");
        List<Object> args4 = new ArrayList<>();
        args4.add(tid); args4.add(tid);
        if (storeId != null) { sql4.append("AND i.store_id = ? "); args4.add(storeId); }
        sql4.append("GROUP BY p.category ORDER BY value DESC");
        List<Map<String, Object>> categoryRows = jdbc.queryForList(sql4.toString(), args4.toArray());
        List<Map<String, Object>> categoryStats = new ArrayList<>();
        for (Map<String, Object> r : categoryRows) {
            Map<String, Object> m = new HashMap<>();
            String cat = r.get("category") == null ? "未分类" : r.get("category").toString();
            m.put("category", cat);
            m.put("value", r.get("value"));
            m.put("qty", r.get("qty"));
            categoryStats.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.putAll(prods);
        result.putAll(invs);
        result.putAll(records);
        result.put("category_stats", Keys.camel(categoryStats));
        return Result.success(Keys.camel(result));
    }

    /**
     * 月度消耗趋势（近 N 个月）
     */
    @GetMapping("/stats/trend")
    @RequirePermission("stock:stat")
    public Result<List<Map<String, Object>>> statsTrend(
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) Long storeId) {
        Long tid = tenantId();
        // MySQL 生成最近 N 个月月份
        StringBuilder sql = new StringBuilder(
            "SELECT DATE_FORMAT(record_time,'%Y-%m') month, " +
            "COALESCE(SUM(quantity), 0) qty, " +
            "COALESCE(SUM(total_amount), 0) amount " +
            "FROM stock_record " +
            "WHERE deleted = 0 AND tenant_id = ? AND type IN (2,4,5) " +
            "AND record_time >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(months);
        if (storeId != null) { sql.append("AND store_id = ? "); args.add(storeId); }
        sql.append("GROUP BY DATE_FORMAT(record_time,'%Y-%m') ORDER BY month ASC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 补齐没有数据的月份（保证折线/柱图连续）
        Map<String, Map<String, Object>> indexed = new HashMap<>();
        for (Map<String, Object> r : rows) indexed.put(r.get("month").toString(), r);

        List<Map<String, Object>> filled = new ArrayList<>();
        java.time.YearMonth ym = java.time.YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            java.time.YearMonth back = ym.minusMonths(i);
            String key = String.format("%d-%02d", back.getYear(), back.getMonthValue());
            Map<String, Object> m = new HashMap<>();
            m.put("month", key);
            Map<String, Object> hit = indexed.get(key);
            m.put("qty", hit == null ? 0 : hit.get("qty"));
            m.put("amount", hit == null ? 0 : hit.get("amount"));
            filled.add(m);
        }
        return Result.success(Keys.camel(filled));
    }

    /**
     * 消耗排行 TOP N（按金额或数量）
     */
    @GetMapping("/stats/ranking")
    @RequirePermission("stock:stat")
    public Result<List<Map<String, Object>>> statsRanking(
            @RequestParam(defaultValue = "amount") String type,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long storeId) {
        Long tid = tenantId();
        String order = "amount".equalsIgnoreCase(type) ? "total_amount" : "total_qty";
        StringBuilder sql = new StringBuilder(
            "SELECT p.id, p.product_code, p.product_name, p.category, p.unit, p.cost_price, " +
            "COALESCE(SUM(r.quantity), 0) total_qty, " +
            "COALESCE(SUM(r.total_amount), 0) total_amount " +
            "FROM stock_record r JOIN stock_product p ON r.product_id = p.id " +
            "WHERE r.deleted = 0 AND p.deleted = 0 AND r.tenant_id = ? AND p.tenant_id = ? AND r.type IN (2,4,5) ");
        List<Object> args = new ArrayList<>();
        args.add(tid); args.add(tid);
        if (storeId != null) { sql.append("AND r.store_id = ? "); args.add(storeId); }
        sql.append("GROUP BY p.id, p.product_code, p.product_name, p.category, p.unit, p.cost_price ");
        sql.append("ORDER BY ").append(order).append(" DESC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 占比
        double sumAmount = 0;
        for (Map<String, Object> r : rows) sumAmount += ((Number) r.get("total_amount")).doubleValue();
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            double amt = ((Number) r.get("total_amount")).doubleValue();
            r.put("pct", sumAmount > 0 ? Math.round(amt * 10000.0 / sumAmount) / 100.0 : 0);
            enriched.add(r);
        }
        return Result.success(Keys.camel(enriched));
    }

    /**
     * 一次性修复接口：把 pos_store.store_name（当前是 VARBINARY 且双重编码）的字节
     * 按 latin1 解码为字符串，再以 utf8mb4 写回；并恢复字段类型为 utf8mb4。
     */

}
