package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * R3.0-A：总部/品牌/门店三级模型
 *  - T-A21 brand 表与品牌-门店-租户关系
 *  - T-A22 数据范围：品牌级 / 租户级 / 本店 / 本人
 *  - T-A23 总部视角看板（跨店汇总、Top 门店）
 */
@RestController
@RequestMapping("/org")
@RequiredArgsConstructor
public class OrgHierarchyService {

    private final MktBrandMapper brandMapper;
    private final StoreMapper storeMapper;
    private final PosEmployeeStoreMapper employeeStoreMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T-A21 品牌 CRUD
    // ====================================================================

    @GetMapping("/brand/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> brandPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, tenant_id, brand_code, brand_name, brand_type, contact_person, contact_phone, " +
            "province, city, status, priority, operator_name, create_time " +
            "FROM mkt_brand WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (brand_code LIKE ? OR brand_name LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY priority DESC, id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mkt_brand WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (status != null) { csql.append("AND status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (brand_code LIKE ? OR brand_name LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/brand")
    public Result<MktBrand> createBrand(@RequestBody MktBrand b) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        if (b.getBrandCode() == null || b.getBrandCode().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "品牌编码必填");
        if (b.getBrandName() == null || b.getBrandName().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "品牌名称必填");
        Long dup = brandMapper.selectCount(new LambdaQueryWrapper<MktBrand>()
                .eq(MktBrand::getTenantId, tenantId)
                .eq(MktBrand::getBrandCode, b.getBrandCode()));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "品牌编码已存在");
        if (b.getStatus() == null) b.setStatus(1);
        if (b.getPriority() == null) b.setPriority(0);
        if (b.getMemberShareScope() == null) b.setMemberShareScope("BRAND");
        if (b.getPointsShareScope() == null) b.setPointsShareScope("BRAND");
        if (b.getBalanceShareScope() == null) b.setBalanceShareScope("BRAND");
        b.setTenantId(tenantId);
        brandMapper.insert(b);
        return Result.success("已创建", b);
    }

    @PutMapping("/brand/{id}")
    public Result<MktBrand> updateBrand(@PathVariable Long id, @RequestBody MktBrand b) {
        MktBrand old = brandMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "品牌不存在");
        b.setId(id);
        b.setBrandCode(old.getBrandCode());
        b.setTenantId(old.getTenantId());
        brandMapper.updateById(b);
        return Result.success("已更新", brandMapper.selectById(id));
    }

    @DeleteMapping("/brand/{id}")
    public Result<Void> deleteBrand(@PathVariable Long id) {
        MktBrand b = brandMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "品牌不存在");
        Long storeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_store WHERE deleted=0 AND brand_id = ?", Long.class, id);
        if (storeCount != null && storeCount > 0)
            return Result.error(BizCode.CONFLICT, "该品牌下有 " + storeCount + " 家门店，不能删除");
        brandMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    // ====================================================================
    // T-A22 数据范围过滤器
    // ====================================================================

    /**
     * 获取当前用户的数据范围（品牌内 / 本店 / 本人）
     */
    @GetMapping("/scope/me")
    public Result<Map<String, Object>> myScope() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long userId = TenantContextHolder.get().getUserId();
        String roleCode = TenantContextHolder.get().getRoleCode();
        Long storeId = TenantContextHolder.get().getStoreId();
        Long brandId = TenantContextHolder.get().getBrandId();

        Map<String, Object> scope = new HashMap<>();
        scope.put("tenantId", tenantId);
        scope.put("userId", userId);
        scope.put("roleCode", roleCode);
        scope.put("storeId", storeId);
        scope.put("brandId", brandId);

        // 判断可访问范围
        List<Long> allowedStoreIds = new ArrayList<>();
        List<Long> allowedBrandIds = new ArrayList<>();

        if ("HQ_ADMIN".equals(roleCode) || "SUPER_ADMIN".equals(roleCode)) {
            // 总部/超级管理员：所有品牌+门店
            scope.put("dataScope", "ALL");
            scope.put("scopeLabel", "全部（总部/超级管理员）");
        } else if ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode)) {
            // 品牌级：品牌内所有门店
            scope.put("dataScope", "BRAND");
            scope.put("scopeLabel", "品牌内（" + (brandId != null ? brandId : "?") + "）");
            if (brandId != null) {
                allowedBrandIds.add(brandId);
                // 拉品牌内所有门店
                List<Long> sids = jdbc.queryForList(
                    "SELECT id FROM pos_store WHERE deleted=0 AND brand_id = ? AND status = 1",
                    Long.class, brandId);
                allowedStoreIds.addAll(sids);
            }
        } else if (storeId != null) {
            // 门店级
            scope.put("dataScope", "STORE");
            scope.put("scopeLabel", "本店（" + storeId + "）");
            allowedStoreIds.add(storeId);
        } else {
            // 个人
            scope.put("dataScope", "SELF");
            scope.put("scopeLabel", "本人");
        }

        scope.put("allowedStoreIds", allowedStoreIds);
        scope.put("allowedBrandIds", allowedBrandIds);
        return Result.success(scope);
    }

    /**
     * 根据 dataScope 自动拼接 SQL WHERE 条件
     * T-A22：所有报表/查询接口统一使用此工具方法
     */
    @PostMapping("/scope/filter")
    public Result<Map<String, Object>> filterScope(@RequestBody Map<String, Object> body) {
        Long userId = TenantContextHolder.get().getUserId();
        String roleCode = TenantContextHolder.get().getRoleCode();
        Long storeId = TenantContextHolder.get().getStoreId();
        Long brandId = TenantContextHolder.get().getBrandId();

        StringBuilder where = new StringBuilder("deleted = 0 ");
        List<Object> args = new ArrayList<>();

        if ("HQ_ADMIN".equals(roleCode) || "SUPER_ADMIN".equals(roleCode)) {
            // 不过滤
        } else if ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode)) {
            if (brandId == null) return Result.error(BizCode.FORBIDDEN, "未识别品牌");
            where.append("AND brand_id = ? ");
            args.add(brandId);
        } else {
            // 默认：本店
            if (storeId == null) return Result.error(BizCode.FORBIDDEN, "未识别门店");
            where.append("AND store_id = ? ");
            args.add(storeId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("whereClause", where.toString());
        data.put("args", args);
        data.put("dataScope", getScopeType(roleCode, storeId, brandId));
        return Result.success(data);
    }

    private String getScopeType(String roleCode, Long storeId, Long brandId) {
        if ("HQ_ADMIN".equals(roleCode) || "SUPER_ADMIN".equals(roleCode)) return "ALL";
        if ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode)) return "BRAND";
        if (storeId != null) return "STORE";
        return "SELF";
    }

    // ====================================================================
    // T-A22 员工-门店关联
    // ====================================================================

    @PostMapping("/employee/store/bind")
    @Transactional(rollbackFor = Exception.class)
    public Result<PosEmployeeStore> bindEmployeeStore(@RequestBody Map<String, Object> body) {
        Long employeeId = body.get("employeeId") == null ? null : ((Number) body.get("employeeId")).longValue();
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        String roleInStore = (String) body.getOrDefault("roleInStore", "STAFF");
        Integer isPrimary = body.get("isPrimary") == null ? 0 : (((Number) body.get("isPrimary")).intValue());
        if (employeeId == null || storeId == null) return Result.error(BizCode.BAD_REQUEST, "employeeId 和 storeId 必填");

        Long dup = employeeStoreMapper.selectCount(new LambdaQueryWrapper<PosEmployeeStore>()
                .eq(PosEmployeeStore::getEmployeeId, employeeId)
                .eq(PosEmployeeStore::getStoreId, storeId));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "员工已在该门店");

        // 拉 brand_id
        Long brandId = jdbc.queryForObject(
            "SELECT brand_id FROM pos_store WHERE id = ?", Long.class, storeId);
        Long tenantId = TenantContextHolder.get().getTenantId();
        // T07-D1：缺失则拒绝，禁止 fallback 到默认租户 1L
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }

        PosEmployeeStore es = new PosEmployeeStore();
        es.setEmployeeId(employeeId);
        es.setStoreId(storeId);
        es.setBrandId(brandId);
        es.setTenantId(tenantId);
        es.setRoleInStore(roleInStore);
        es.setIsPrimary(isPrimary);
        es.setStatus(1);
        if (isPrimary == 1) {
            jdbc.update("UPDATE pos_employee_store SET is_primary = 0 WHERE employee_id = ? AND deleted = 0", employeeId);
        }
        employeeStoreMapper.insert(es);
        return Result.success("已绑定", es);
    }

    @PostMapping("/employee/store/unbind")
    public Result<Void> unbindEmployeeStore(@RequestBody Map<String, Object> body) {
        Long employeeId = body.get("employeeId") == null ? null : ((Number) body.get("employeeId")).longValue();
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        if (employeeId == null || storeId == null) return Result.error(BizCode.BAD_REQUEST, "必填");
        employeeStoreMapper.delete(new LambdaQueryWrapper<PosEmployeeStore>()
                .eq(PosEmployeeStore::getEmployeeId, employeeId)
                .eq(PosEmployeeStore::getStoreId, storeId));
        return Result.success("已解绑", null);
    }

    @GetMapping("/employee/{employeeId}/stores")
    public Result<List<Map<String, Object>>> employeeStores(@PathVariable Long employeeId) {
        List<Map<String, Object>> stores = jdbc.queryForList(
            "SELECT es.id, es.store_id, s.store_name, es.role_in_store, es.is_primary, es.status " +
            "FROM pos_employee_store es JOIN pos_store s ON s.id = es.store_id " +
            "WHERE es.employee_id = ? AND es.deleted = 0 AND s.deleted = 0 " +
            "ORDER BY es.is_primary DESC, es.id DESC",
            employeeId);
        return Result.success(Keys.camel(stores));
    }

    @GetMapping("/store/{storeId}/employees")
    public Result<List<Map<String, Object>>> storeEmployees(@PathVariable Long storeId) {
        List<Map<String, Object>> emps = jdbc.queryForList(
            "SELECT e.id, e.employee_code, e.employee_name, e.phone, e.position, es.role_in_store, es.is_primary " +
            "FROM pos_employee_store es " +
            "JOIN pos_employee e ON e.id = es.employee_id " +
            "WHERE es.store_id = ? AND es.deleted = 0 AND e.deleted = 0 AND es.status = 1 " +
            "ORDER BY es.is_primary DESC, es.id DESC",
            storeId);
        return Result.success(Keys.camel(emps));
    }

    // ====================================================================
    // T-A23 总部看板
    // ====================================================================

    /**
     * 总部视角：跨品牌/门店汇总
     * 自动根据用户角色过滤数据范围
     */
    @GetMapping("/hq/dashboard")
    public Result<Map<String, Object>> hqDashboard(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String roleCode = TenantContextHolder.get().getRoleCode();
        Long storeId = TenantContextHolder.get().getStoreId();
        Long brandId = TenantContextHolder.get().getBrandId();

        LocalDate s = startDate == null ? LocalDate.now().minusDays(30) : LocalDate.parse(startDate);
        LocalDate e = endDate == null ? LocalDate.now() : LocalDate.parse(endDate);
        java.time.LocalDateTime sDt = s.atStartOfDay();
        java.time.LocalDateTime eDt = e.atTime(23, 59, 59);

        Map<String, Object> data = new HashMap<>();
        data.put("periodStart", s);
        data.put("periodEnd", e);
        data.put("dataScope", getScopeType(roleCode, storeId, brandId));

        // 过滤条件
        String extraWhere = "";
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(sDt);
        args.add(eDt);

        if ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode)) {
            if (brandId != null) { extraWhere = "AND s.brand_id = ? "; args.add(brandId); }
        } else if (storeId != null) {
            extraWhere = "AND o.store_id = ? "; args.add(storeId);
        }

        // 品牌级汇总
        String sqlBrand =
            "SELECT s.brand_id, b.brand_name, " +
            "       COUNT(DISTINCT s.id) AS store_count, " +
            "       COUNT(DISTINCT o.member_id) AS member_count, " +
            "       COUNT(DISTINCT CASE WHEN o.status IN (3,4) THEN o.id END) AS order_count, " +
            "       COALESCE(SUM(CASE WHEN o.status IN (3,4) THEN o.final_amount - IFNULL(o.refund_amount,0) ELSE 0 END), 0) AS net_revenue " +
            "FROM pos_store s " +
            "LEFT JOIN mkt_brand b ON b.id = s.brand_id " +
            "LEFT JOIN pos_order o ON o.store_id = s.id AND o.deleted = 0 AND o.pay_time BETWEEN ? AND ? " +
            "WHERE s.deleted = 0 AND s.status = 1 AND s.tenant_id = ? " +
            extraWhere +
            "GROUP BY s.brand_id, b.brand_name " +
            "ORDER BY net_revenue DESC";
        List<Map<String, Object>> brandSummary = jdbc.queryForList(sqlBrand, args.toArray());
        data.put("brandSummary", Keys.camel(brandSummary));

        // Top 门店
        String sqlTop =
            "SELECT o.store_id, s.store_name, b.brand_name, " +
            "       COUNT(CASE WHEN o.status IN (3,4) THEN 1 END) AS order_count, " +
            "       COALESCE(SUM(CASE WHEN o.status IN (3,4) THEN o.final_amount - IFNULL(o.refund_amount,0) ELSE 0 END), 0) AS net_revenue " +
            "FROM pos_order o " +
            "JOIN pos_store s ON s.id = o.store_id " +
            "LEFT JOIN mkt_brand b ON b.id = s.brand_id " +
            "WHERE o.deleted = 0 AND o.pay_time BETWEEN ? AND ? " +
            "  AND s.tenant_id = ? " + extraWhere.replace("AND o.", "AND s.") +
            "GROUP BY o.store_id, s.store_name, b.brand_name " +
            "ORDER BY net_revenue DESC LIMIT 10";
        // brand_name in args needs different position — simplify:
        String sqlTop2 =
            "SELECT o.store_id, MAX(s.store_name) AS store_name, " +
            "       COUNT(CASE WHEN o.status IN (3,4) THEN 1 END) AS order_count, " +
            "       COALESCE(SUM(CASE WHEN o.status IN (3,4) THEN o.final_amount - IFNULL(o.refund_amount,0) ELSE 0 END), 0) AS net_revenue " +
            "FROM pos_order o " +
            "JOIN pos_store s ON s.id = o.store_id " +
            "WHERE o.deleted = 0 AND o.pay_time BETWEEN ? AND ? " +
            "  AND s.tenant_id = ? " + extraWhere +
            "GROUP BY o.store_id " +
            "ORDER BY net_revenue DESC LIMIT 10";
        List<Map<String, Object>> topStores = jdbc.queryForList(sqlTop2, args.toArray());
        data.put("topStores", Keys.camel(topStores));

        // 总计
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalOrders = 0;
        for (Map<String, Object> b : brandSummary) {
            BigDecimal r = (BigDecimal) b.getOrDefault("netRevenue", BigDecimal.ZERO);
            if (r == null) r = BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(r);
            totalOrders += ((Number) b.getOrDefault("orderCount", 0)).longValue();
        }
        data.put("totalNetRevenue", totalRevenue);
        data.put("totalOrderCount", totalOrders);

        // --- P0: 趋势数据（每日营收 + 订单数） ---
        String sqlTrend =
            "SELECT DATE(o.pay_time) AS stat_date, " +
            "       COALESCE(SUM(CASE WHEN o.status IN (3,4) THEN o.final_amount - IFNULL(o.refund_amount,0) ELSE 0 END), 0) AS net_revenue, " +
            "       COUNT(CASE WHEN o.status IN (3,4) THEN 1 END) AS order_count " +
            "FROM pos_order o " +
            "JOIN pos_store s ON s.id = o.store_id " +
            "WHERE o.deleted = 0 AND o.pay_time BETWEEN ? AND ? AND s.tenant_id = ? " +
            (storeId != null ? "AND o.store_id = ? " : "") +
            (brandId != null && ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode)) ? "AND s.brand_id = ? " : "") +
            "GROUP BY DATE(o.pay_time) ORDER BY stat_date";
        List<Object> tArgs = new ArrayList<>();
        tArgs.add(sDt); tArgs.add(eDt); tArgs.add(tenantId);
        if (storeId != null) tArgs.add(storeId);
        if (brandId != null && ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode))) tArgs.add(brandId);
        List<Map<String, Object>> rawTrend = jdbc.queryForList(sqlTrend, tArgs.toArray());
        data.put("trend", Keys.camel(rawTrend));

        // --- P0: 上期对比数据（同周期长度往前推） ---
        long periodDays = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
        LocalDate ps = s.minusDays(periodDays);
        LocalDate pe = s.minusDays(1);
        java.time.LocalDateTime psDt = ps.atStartOfDay();
        java.time.LocalDateTime peDt = pe.atTime(23, 59, 59);
        String sqlPrev =
            "SELECT COALESCE(SUM(CASE WHEN o.status IN (3,4) THEN o.final_amount - IFNULL(o.refund_amount,0) ELSE 0 END), 0) AS net_revenue, " +
            "       COUNT(CASE WHEN o.status IN (3,4) THEN 1 END) AS order_count " +
            "FROM pos_order o " +
            "JOIN pos_store s ON s.id = o.store_id " +
            "WHERE o.deleted = 0 AND o.pay_time BETWEEN ? AND ? AND s.tenant_id = ? " +
            (storeId != null ? "AND o.store_id = ? " : "") +
            (brandId != null && ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode)) ? "AND s.brand_id = ? " : "");
        List<Object> pArgs = new ArrayList<>();
        pArgs.add(psDt); pArgs.add(peDt); pArgs.add(tenantId);
        if (storeId != null) pArgs.add(storeId);
        if (brandId != null && ("BRAND_ADMIN".equals(roleCode) || "BRAND_STAFF".equals(roleCode))) pArgs.add(brandId);
        List<Map<String, Object>> prevRows = jdbc.queryForList(sqlPrev, pArgs.toArray());
        BigDecimal prevRevenue = BigDecimal.ZERO;
        long prevOrders = 0;
        if (!prevRows.isEmpty()) {
            Map<String, Object> p = prevRows.get(0);
            prevRevenue = p.get("netRevenue") == null ? BigDecimal.ZERO : (BigDecimal) p.get("netRevenue");
            prevOrders = ((Number) p.getOrDefault("orderCount", 0)).longValue();
        }
        Map<String, Object> compare = new LinkedHashMap<>();
        compare.put("prevRevenue", prevRevenue);
        compare.put("prevOrders", prevOrders);
        // 计算同比增长率（null-safe）
        double revenueGrowth = prevRevenue.signum() > 0
            ? (totalRevenue.subtract(prevRevenue)).divide(prevRevenue, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100
            : (totalRevenue.signum() > 0 ? 100.0 : 0.0);
        double ordersGrowth = prevOrders > 0
            ? ((double)(totalOrders - prevOrders) / prevOrders) * 100
            : (totalOrders > 0 ? 100.0 : 0.0);
        compare.put("revenueGrowth", revenueGrowth);
        compare.put("ordersGrowth", ordersGrowth);
        data.put("compare", compare);

        return Result.success(data);
    }

    @GetMapping("/hq/brand/{brandId}/stores")
    public Result<List<Map<String, Object>>> brandStores(@PathVariable Long brandId) {
        List<Map<String, Object>> stores = jdbc.queryForList(
            "SELECT s.id, s.store_name, s.store_code, s.store_type, s.province, s.city, s.status, " +
            "       s.manager_name, s.open_date, s.is_hq " +
            "FROM pos_store s " +
            "WHERE s.deleted = 0 AND s.brand_id = ? AND s.tenant_id = ? " +
            "ORDER BY s.is_hq DESC, s.brand_priority ASC, s.id ASC",
            brandId, TenantContextHolder.get().getTenantId());
        return Result.success(Keys.camel(stores));
    }

    @GetMapping("/store/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> storePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT s.id, s.store_name, s.store_code, s.store_type, s.province, s.city, s.status, " +
            "       s.contact_person, s.contact_phone, s.is_hq, s.manager_name, s.open_date, s.brand_priority, " +
            "       b.brand_name, b.brand_code " +
            "FROM pos_store s " +
            "LEFT JOIN mkt_brand b ON b.id = s.brand_id " +
            "WHERE s.deleted = 0 AND s.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (brandId != null) { sql.append("AND s.brand_id = ? "); args.add(brandId); }
        if (status != null) { sql.append("AND s.status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (s.store_name LIKE ? OR s.store_code LIKE ? OR s.contact_person LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY s.is_hq DESC, s.brand_priority ASC, s.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM pos_store s WHERE s.deleted = 0 AND s.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (brandId != null) { csql.append("AND s.brand_id = ? "); cargs.add(brandId); }
        if (status != null) { csql.append("AND s.status = ? "); cargs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (s.store_name LIKE ? OR s.store_code LIKE ? OR s.contact_person LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PutMapping("/store/{id}")
    public Result<Store> updateStore(@PathVariable Long id, @RequestBody Store s) {
        Store old = storeMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "门店不存在");
        s.setId(id);
        s.setTenantId(old.getTenantId());
        storeMapper.updateById(s);
        return Result.success("已更新", storeMapper.selectById(id));
    }
}
