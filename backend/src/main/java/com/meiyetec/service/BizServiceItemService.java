package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.BizServiceCategory;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizServiceCategoryMapper;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import com.meiyetec.util.XlsxExportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R2.1-A T-A12：服务分类 + 服务项目 CRUD + 耗材绑定
 */
@RestController
@RequestMapping("/biz/service")
@RequiredArgsConstructor
public class BizServiceItemService {

    private final BizServiceCategoryMapper categoryMapper;
    private final com.meiyetec.mapper.BizServiceItemMapper itemMapper;
    private final com.meiyetec.mapper.BizServiceMaterialMapper bomMapper;
    @Autowired
    private JdbcTemplate jdbc;

    // ==================== 分类 ====================

    @GetMapping("/category/list")
    public Result<List<BizServiceCategory>> categoryList(@RequestParam(required = false) Integer status) {
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<BizServiceCategory> q = new LambdaQueryWrapper<>();
        q.eq(BizServiceCategory::getTenantId, tenantId);
        if (status != null) q.eq(BizServiceCategory::getStatus, status);
        q.orderByAsc(BizServiceCategory::getSortOrder).orderByAsc(BizServiceCategory::getId);
        return Result.success(categoryMapper.selectList(q));
    }

    @GetMapping("/category/page")
    public Result<Page<BizServiceCategory>> categoryPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<BizServiceCategory> q = new LambdaQueryWrapper<>();
        q.eq(BizServiceCategory::getTenantId, tenantId);
        if (status != null) q.eq(BizServiceCategory::getStatus, status);
        if (keyword != null && !keyword.isEmpty())
            q.and(w -> w.like(BizServiceCategory::getCategoryCode, keyword)
                    .or().like(BizServiceCategory::getCategoryName, keyword));
        q.orderByAsc(BizServiceCategory::getSortOrder).orderByAsc(BizServiceCategory::getId);
        return Result.success(categoryMapper.selectPage(new Page<>(pageNum, pageSize), q));
    }

    @PostMapping("/category")
    public Result<BizServiceCategory> createCategory(@RequestBody BizServiceCategory c) {
        if (c.getCategoryCode() == null || c.getCategoryCode().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "分类编码必填");
        if (c.getCategoryName() == null || c.getCategoryName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "分类名称必填");
        Long exist = categoryMapper.selectCount(new LambdaQueryWrapper<BizServiceCategory>()
                .eq(BizServiceCategory::getCategoryCode, c.getCategoryCode()));
        if (exist > 0) return Result.error(BizCode.CONFLICT, "分类编码已存在");
        if (c.getStatus() == null) c.setStatus(1);
        if (c.getSortOrder() == null) c.setSortOrder(0);
        if (c.getParentId() == null) c.setParentId(0L);
        if (c.getTenantId() == null) c.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (c.getStoreId() == null) c.setStoreId(1L);
        categoryMapper.insert(c);
        return Result.success("创建成功", c);
    }

    @PutMapping("/category/{id}")
    public Result<BizServiceCategory> updateCategory(@PathVariable Long id, @RequestBody BizServiceCategory c) {
        BizServiceCategory old = categoryMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "分类不存在");
        c.setId(id);
        c.setCategoryCode(old.getCategoryCode()); // 编码不可改
        categoryMapper.updateById(c);
        return Result.success("更新成功", categoryMapper.selectById(id));
    }

    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        BizServiceCategory old = categoryMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "分类不存在");
        Long inUse = itemMapper.selectCount(new LambdaQueryWrapper<com.meiyetec.entity.BizServiceItem>()
                .eq(com.meiyetec.entity.BizServiceItem::getCategoryId, id));
        if (inUse > 0) return Result.error(BizCode.CONFLICT, "该分类下还有项目，无法删除");
        categoryMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    // ==================== 服务项目 ====================

    @GetMapping("/item/page")
    public Result<Page<Map<String, Object>>> itemPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long tenantId = currentTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT i.id, i.service_code, i.service_name, i.category_id, i.unit_price, i.member_price, " +
            "i.cost_price, i.duration, i.unit, i.description, i.image_url, i.status, i.sort_order, " +
            "i.create_time, i.update_time, " +
            "c.category_code, c.category_name " +
            "FROM biz_service_item i LEFT JOIN biz_service_category c ON i.category_id = c.id " +
            "WHERE i.deleted = 0 AND i.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        if (categoryId != null) { sql.append("AND i.category_id = ? "); args.add(categoryId); }
        if (status != null) { sql.append("AND i.status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (i.service_code LIKE ? OR i.service_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY i.sort_order ASC, i.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM biz_service_item i WHERE i.deleted = 0 AND i.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (storeId != null) { csql.append("AND i.store_id = ? "); cargs.add(storeId); }
        if (categoryId != null) { csql.append("AND i.category_id = ? "); cargs.add(categoryId); }
        if (status != null) { csql.append("AND i.status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (i.service_code LIKE ? OR i.service_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @GetMapping("/item/{id}")
    public Result<Map<String, Object>> itemDetail(@PathVariable Long id) {
        com.meiyetec.entity.BizServiceItem item = itemMapper.selectById(id);
        if (item == null) return Result.error(BizCode.NOT_FOUND, "项目不存在");
        if (item.getTenantId() != null && !item.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权访问该项目");
        }
        // 拉耗材绑定
        List<Map<String, Object>> bom = bomMapper.listBomByService(id);
        Map<String, Object> result = new HashMap<>();
        result.put("item", item);
        result.put("materials", Keys.camel(bom));
        return Result.success(result);
    }

    @PostMapping("/item")
    public Result<com.meiyetec.entity.BizServiceItem> createItem(@RequestBody com.meiyetec.entity.BizServiceItem p) {
        if (p.getServiceCode() == null || p.getServiceCode().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "项目编码必填");
        if (p.getServiceName() == null || p.getServiceName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "项目名称必填");
        Long exist = itemMapper.selectCount(new LambdaQueryWrapper<com.meiyetec.entity.BizServiceItem>()
                .eq(com.meiyetec.entity.BizServiceItem::getServiceCode, p.getServiceCode()));
        if (exist > 0) return Result.error(BizCode.CONFLICT, "项目编码已存在");
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getSortOrder() == null) p.setSortOrder(0);
        if (p.getUnit() == null) p.setUnit("次");
        if (p.getTenantId() == null) p.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        if (p.getStoreId() == null) p.setStoreId(1L);
        itemMapper.insert(p);
        return Result.success("创建成功", p);
    }

    @PutMapping("/item/{id}")
    public Result<com.meiyetec.entity.BizServiceItem> updateItem(@PathVariable Long id,
            @RequestBody com.meiyetec.entity.BizServiceItem p) {
        com.meiyetec.entity.BizServiceItem old = itemMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "项目不存在");
        if (old.getTenantId() != null && !old.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权修改该项目");
        }
        p.setId(id);
        p.setServiceCode(old.getServiceCode()); // 编码不可改
        itemMapper.updateById(p);
        // 自动重算 cost_price = SUM(bom.quantity * stock_product.cost_price)
        recomputeCostPrice(id);
        return Result.success("更新成功", itemMapper.selectById(id));
    }

    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        com.meiyetec.entity.BizServiceItem old = itemMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "项目不存在");
        if (old.getTenantId() != null && !old.getTenantId().equals(currentTenantId())) {
            return Result.error(BizCode.FORBIDDEN, "无权删除该项目");
        }
        Long inUse = jdbc.queryForObject(
            "SELECT COUNT(*) FROM biz_treatment_plan WHERE deleted = 0 AND service_id = ?",
            Long.class, id);
        if (inUse != null && inUse > 0)
            return Result.error(BizCode.CONFLICT, "该项目关联疗程方案，无法删除");
        itemMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 重新计算成本价 = SUM(bom.quantity * stock_product.cost_price)
     * 保存到 biz_service_item.cost_price
     */
    private void recomputeCostPrice(Long serviceId) {
        try {
            Double cost = jdbc.queryForObject(
                "SELECT COALESCE(SUM(m.quantity * p.cost_price), 0) " +
                "FROM biz_service_material m " +
                "JOIN stock_product p ON p.id = m.product_id AND p.deleted = 0 " +
                "WHERE m.deleted = 0 AND m.service_id = ?",
                Double.class, serviceId);
            jdbc.update("UPDATE biz_service_item SET cost_price = ? WHERE id = ?",
                    cost == null ? 0 : cost, serviceId);
        } catch (Exception ignored) {}
    }

    // ==================== 耗材绑定（基于 BOM） ====================

    /**
     * 给项目添加一个耗材绑定（同 product+service 唯一）
     */
    @PostMapping("/item/{id}/material")
    public Result<Map<String, Object>> addMaterial(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long productId = ((Number) body.get("productId")).longValue();
        java.math.BigDecimal quantity = new java.math.BigDecimal(body.get("quantity").toString());
        if (quantity.signum() <= 0) return Result.error(BizCode.BAD_REQUEST, "数量必须 > 0");

        com.meiyetec.entity.BizServiceItem item = itemMapper.selectById(id);
        if (item == null) return Result.error(BizCode.NOT_FOUND, "项目不存在");

        Long exist = jdbc.queryForObject(
            "SELECT COUNT(*) FROM biz_service_material WHERE deleted=0 AND service_id=? AND product_id=?",
            Long.class, id, productId);
        if (exist != null && exist > 0)
            return Result.error(BizCode.CONFLICT, "该耗材已绑定到该项目");

        jdbc.update(
            "INSERT INTO biz_service_material(tenant_id, service_id, product_id, quantity, unit, is_required, remark) " +
            "VALUES(?,?,?,?,?,?,?)",
            item.getTenantId(), id, productId, quantity,
            body.get("unit") == null ? null : body.get("unit").toString(),
            body.get("isRequired") == null ? 1 : ((Number) body.get("isRequired")).intValue(),
            body.get("remark") == null ? null : body.get("remark").toString());

        recomputeCostPrice(id);
        Map<String, Object> data = new HashMap<>();
        data.put("serviceId", id);
        data.put("productId", productId);
        return Result.success("绑定成功", data);
    }

    @DeleteMapping("/item/{id}/material/{productId}")
    public Result<Void> removeMaterial(@PathVariable Long id, @PathVariable Long productId) {
        jdbc.update("UPDATE biz_service_material SET deleted = 1 WHERE service_id = ? AND product_id = ?",
                id, productId);
        recomputeCostPrice(id);
        return Result.success("已解绑", null);
    }

    // ==================== 导出 XLSX（R2.1-A T-A14 + T-A16 升级） ====================

    @GetMapping("/item/export")
    public ResponseEntity<byte[]> exportItem(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) throws Exception {
        Long tenantId = currentTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT i.service_code, i.service_name, c.category_name, " +
            "i.unit_price, i.member_price, i.cost_price, i.duration, i.unit, " +
            "i.description, i.sop, i.status, i.sort_order, i.create_time " +
            "FROM biz_service_item i LEFT JOIN biz_service_category c ON i.category_id = c.id " +
            "WHERE i.deleted = 0 AND i.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (storeId != null) { sql.append("AND i.store_id = ? "); args.add(storeId); }
        if (categoryId != null) { sql.append("AND i.category_id = ? "); args.add(categoryId); }
        if (status != null) { sql.append("AND i.status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (i.service_code LIKE ? OR i.service_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY i.sort_order, i.id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        String tenantName = resolveTenantName();
        String watermark = "服务项目清单 · 租户：" + tenantName + " · 生成时间：" + LocalDateTime.now() + " · 仅供内部使用";
        String[] headers = {"项目编码", "项目名称", "分类", "标准价", "会员价", "成本价",
                            "时长(分钟)", "单位", "描述", "SOP", "状态", "排序", "创建时间"};
        List<List<Object>> body = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Integer st = (Integer) r.get("status");
            String statusDesc = st == null ? "停用" : st == 1 ? "启用" : "停用";
            List<Object> line = new ArrayList<>();
            line.add(XlsxExportUtil.str(r.get("service_code")));
            line.add(XlsxExportUtil.str(r.get("service_name")));
            line.add(XlsxExportUtil.str(r.get("category_name")));
            line.add(r.get("unit_price"));
            line.add(r.get("member_price"));
            line.add(r.get("cost_price"));
            line.add(r.get("duration"));
            line.add(XlsxExportUtil.str(r.get("unit")));
            line.add(XlsxExportUtil.str(r.get("description")));
            line.add(XlsxExportUtil.str(r.get("sop")));
            line.add(statusDesc);
            line.add(r.get("sort_order"));
            line.add(XlsxExportUtil.str(r.get("create_time")));
            body.add(line);
        }

        byte[] bytes = XlsxExportUtil.export("服务项目", watermark, headers, body);
        String filename = "服务项目清单_" + LocalDate.now() + ".xlsx";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.setContentDispositionFormData("attachment",
                new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        h.add("X-Tenant-Name", new String(tenantName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(h).body(bytes);
    }

    // ==================== 导入 XLSX ====================

    /**
     * 下载导入模板
     */
    @GetMapping("/item/template")
    public ResponseEntity<byte[]> itemImportTemplate() throws Exception {
        String[] headers = {"项目编码*", "项目名称*", "分类编码", "标准价", "会员价", "时长(分钟)*",
                            "单位", "排序", "状态(启用/停用)", "描述", "SOP"};
        List<List<Object>> body = new ArrayList<>();
        // 写两行示例数据
        body.add(List.of("SV001", "面部护理基础版", "FACE", "198.00", "168.00", "60", "次", "1", "启用", "清洁 + 按摩 + 面膜", "1. 卸妆 2. 清洁 3. 热敷..."));
        body.add(List.of("SV002", "身体按摩 60 分钟", "BODY", "298.00", "268.00", "60", "次", "2", "启用", "全身放松按摩", "1. 肩颈 2. 背部 3. 腿部..."));
        byte[] bytes = XlsxExportUtil.export("服务项目导入模板", "请按示例格式填写，必填项带 *", headers, body);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String fn = "服务项目导入模板.xlsx";
        h.setContentDispositionFormData("attachment",
                new String(fn.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        return ResponseEntity.ok().headers(h).body(bytes);
    }

    /**
     * 批量导入服务项目
     */
    @PostMapping("/item/import")
    public Result<Map<String, Object>> importItem(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        Long tenantId = currentTenantId();
        if (file == null || file.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "请上传文件");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx")) return Result.error(BizCode.BAD_REQUEST, "仅支持 .xlsx 格式");

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("serviceCode", "项目编码*");
        headerMap.put("serviceName", "项目名称*");
        headerMap.put("categoryCode", "分类编码");
        headerMap.put("unitPrice", "标准价");
        headerMap.put("memberPrice", "会员价");
        headerMap.put("duration", "时长(分钟)*");
        headerMap.put("unit", "单位");
        headerMap.put("sortOrder", "排序");
        headerMap.put("status", "状态(启用/停用)");
        headerMap.put("description", "描述");
        headerMap.put("sop", "SOP");

        List<Map<String, Object>> rows = XlsxExportUtil.importRows(file.getInputStream(), headerMap);
        if (rows.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "模板无数据");

        // 一次性查分类映射 categoryCode → id
        Map<String, Long> catMap = new HashMap<>();
        for (BizServiceCategory c : categoryMapper.selectList(
                new LambdaQueryWrapper<BizServiceCategory>().eq(BizServiceCategory::getTenantId, tenantId))) {
            catMap.put(c.getCategoryCode(), c.getId());
        }

        int ok = 0, fail = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            int rowNo = i + 3; // 表头在第2行，数据从第3行
            try {
                String code = str(r.get("serviceCode"));
                String name = str(r.get("serviceName"));
                String duration = str(r.get("duration"));
                if (code.isEmpty()) throw new RuntimeException("项目编码不能为空");
                if (name.isEmpty()) throw new RuntimeException("项目名称不能为空");
                if (duration.isEmpty()) throw new RuntimeException("时长不能为空");
                // 编码唯一
                Long exist = itemMapper.selectCount(new LambdaQueryWrapper<com.meiyetec.entity.BizServiceItem>()
                        .eq(com.meiyetec.entity.BizServiceItem::getServiceCode, code));
                if (exist > 0) throw new RuntimeException("项目编码已存在");
                // 分类（按编码查 id，未知分类置空）
                Long categoryId = null;
                String catCode = str(r.get("categoryCode"));
                if (!catCode.isEmpty()) categoryId = catMap.get(catCode);
                com.meiyetec.entity.BizServiceItem p = new com.meiyetec.entity.BizServiceItem();
                p.setServiceCode(code);
                p.setServiceName(name);
                p.setCategoryId(categoryId);
                p.setUnitPrice(bigDec(r.get("unitPrice")));
                p.setMemberPrice(bigDec(r.get("memberPrice")));
                p.setDuration(duration.isEmpty() ? 0 : Double.valueOf(duration).intValue());
                p.setUnit(str(r.get("unit")).isEmpty() ? "次" : str(r.get("unit")));
                Integer sortOrder = integer(r.get("sortOrder"));
                p.setSortOrder(sortOrder == null ? 0 : sortOrder);
                String st = str(r.get("status"));
                p.setStatus(st.contains("启用") || st.equals("1") ? 1 : 0);
                p.setDescription(str(r.get("description")));
                p.setSop(str(r.get("sop")));
                p.setTenantId(tenantId);
                p.setStoreId(1L);
                itemMapper.insert(p);
                ok++;
            } catch (Exception e) {
                fail++;
                if (errors.size() < 20) {
                    errors.add("第 " + rowNo + " 行：" + e.getMessage());
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("total", rows.size());
        data.put("success", ok);
        data.put("failed", fail);
        data.put("errors", errors);
        return Result.success("导入完成，成功 " + ok + " 条，失败 " + fail + " 条", data);
    }

    // 帮助函数
    private static String str(Object o) { return XlsxExportUtil.str(o); }
    private static Integer integer(Object o) { return XlsxExportUtil.integer(o); }
    private static Double dbl(Object o) { return XlsxExportUtil.dbl(o); }
    private static java.math.BigDecimal bigDec(Object o) { return XlsxExportUtil.bigDec(o); }

    /**
     * 取当前会话的 tenantId；未登录或上下文为空时返回 0L（兜底，
     * 单元测试 / 定时任务等非 web 场景下使用）。
     */
    private Long currentTenantId() {
        Long tid = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        return tid == null ? 0L : tid;
    }

    private String resolveTenantName() {
        try {
            Long tid = com.meiyetec.security.TenantContextHolder.get().getTenantId();
            if (tid != null) {
                List<Map<String, Object>> t = jdbc.queryForList(
                    "SELECT tenant_name FROM sys_tenant WHERE id = ? LIMIT 1", tid);
                if (!t.isEmpty()) return String.valueOf(t.get(0).get("tenant_name"));
            }
        } catch (Exception ignored) {}
        return "默认租户";
    }
}
