package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.util.PageResult;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.exception.BizCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * M11 数据报表 Controller
 * 端点：/report/*
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
@SysOpLog(module = "报表")
public class ReportController {

    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper itemMapper;
    private final PosPaymentMapper paymentMapper;
    private final MemberMapper memberMapper;
    private final EmployeeMapper employeeMapper;
    private final ReportConfigMapper configMapper;
    private final ReportExportLogMapper exportLogMapper;
    private final ReportSnapshotMapper snapshotMapper;
    private final JwtUtil jwtUtil;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    // ==================== 看板：KPI 总览（带同比） ====================

    @GetMapping("/dashboard/kpi")
    @RequirePermission("report:dashboard")
    public Result<Map<String, Object>> dashboardKpi(@RequestParam(required = false) Long storeId) {
        Long t = tid();
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = dayStart;

        LambdaQueryWrapper<PosOrder> q = new LambdaQueryWrapper<>();
        q.eq(PosOrder::getTenantId, t);
        if (storeId != null) q.eq(PosOrder::getStoreId, storeId);
        List<PosOrder> all = orderMapper.selectList(q);

        BigDecimal todayRevenue = all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .filter(o -> o.getPayTime() != null && !o.getPayTime().isBefore(dayStart) && o.getPayTime().isBefore(dayEnd))
                .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal yesterdayRevenue = all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .filter(o -> o.getPayTime() != null && !o.getPayTime().isBefore(yesterdayStart) && o.getPayTime().isBefore(yesterdayEnd))
                .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long todayOrders = all.stream()
                .filter(o -> o.getPayTime() != null && !o.getPayTime().isBefore(dayStart) && o.getPayTime().isBefore(dayEnd))
                .count();

        long yesterdayOrders = all.stream()
                .filter(o -> o.getPayTime() != null && !o.getPayTime().isBefore(yesterdayStart) && o.getPayTime().isBefore(yesterdayEnd))
                .count();

        LocalDate firstDay = today.withDayOfMonth(1);
        BigDecimal monthRevenue = all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .filter(o -> o.getPayTime() != null && !o.getPayTime().isBefore(firstDay.atStartOfDay()))
                .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate lastMonthFirst = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthLast = today.withDayOfMonth(1);
        BigDecimal lastMonthRevenue = all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .filter(o -> o.getPayTime() != null && !o.getPayTime().isBefore(lastMonthFirst.atStartOfDay()) && o.getPayTime().isBefore(lastMonthLast.atStartOfDay()))
                .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrder = todayOrders == 0
                ? BigDecimal.ZERO
                : todayRevenue.divide(BigDecimal.valueOf(todayOrders), 2, RoundingMode.HALF_UP);

        LambdaQueryWrapper<Member> mq = new LambdaQueryWrapper<>();
        mq.eq(Member::getTenantId, t);
        List<Member> members = memberMapper.selectList(mq);
        long totalMembers = members.size();
        LocalDateTime sevenDaysAgo = today.minusDays(7).atStartOfDay();
        long newMembers = members.stream()
                .filter(m -> m.getCreateTime() != null && m.getCreateTime().isAfter(sevenDaysAgo))
                .count();

        long activeMembers = all.stream()
                .filter(o -> o.getPayTime() != null && o.getPayTime().isAfter(today.minusDays(30).atStartOfDay()))
                .filter(o -> o.getMemberId() != null)
                .map(PosOrder::getMemberId).distinct().count();

        long lastMonthActiveMembers = all.stream()
                .filter(o -> o.getPayTime() != null
                        && !o.getPayTime().isBefore(lastMonthFirst.atStartOfDay())
                        && o.getPayTime().isBefore(lastMonthLast.atStartOfDay()))
                .filter(o -> o.getMemberId() != null)
                .map(PosOrder::getMemberId).distinct().count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayRevenue", todayRevenue);
        data.put("yesterdayRevenue", yesterdayRevenue);
        data.put("todayRevenueVsYesterday", pctChange(todayRevenue, yesterdayRevenue));
        data.put("todayOrders", todayOrders);
        data.put("yesterdayOrders", yesterdayOrders);
        data.put("todayOrdersVsYesterday", pctChange(BigDecimal.valueOf(todayOrders), BigDecimal.valueOf(yesterdayOrders)));
        data.put("monthRevenue", monthRevenue);
        data.put("lastMonthRevenue", lastMonthRevenue);
        data.put("monthRevenueVsLastMonth", pctChange(monthRevenue, lastMonthRevenue));
        data.put("avgOrder", avgOrder);
        data.put("totalMembers", totalMembers);
        data.put("newMembers", newMembers);
        data.put("activeMembers", activeMembers);
        data.put("lastMonthActiveMembers", lastMonthActiveMembers);
        data.put("activeMembersVsLastMonth", pctChange(BigDecimal.valueOf(activeMembers), BigDecimal.valueOf(lastMonthActiveMembers)));
        data.put("totalOrders", all.size());
        // === KPI 体系补全（4 个独立指标）===
        // 1) 毛利率 = (营收 - 耗材成本) / 营收
        //    耗材成本 = stock_record.type=4（领用）总金额（按门店/租户过滤）
        // 2) 库存周转 = 月度耗材成本 / 当前库存货值（×12 年化）
        // 3) 耗材差异率 = 已完成盘点单的（盘亏量 / 总盘点量）
        // 4) NPS = 推荐者% − 贬损者%（来自 crm_review.overallRating，近 30 天已发布）
        attachAdvancedKpis(data, t, storeId, monthRevenue);
        return Result.success(data);
    }

    /**
     * 补全 4 个高级 KPI：毛利率 / 库存周转 / 耗材差异率 / NPS
     * <p>全部用 JdbcTemplate 走 SQL，不走 Java streams（避免大表性能问题）
     */
    private void attachAdvancedKpis(Map<String, Object> data, Long tenantId, Long storeId, BigDecimal monthRevenue) {
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime thirtyDaysAgo = today.minusDays(30).atStartOfDay();
        LocalDate thirtyDaysAgoDate = today.minusDays(30);

        String storeFilter = storeId == null ? "" : " AND store_id = ? ";

        // === 1) 耗材成本（月度） + 当前库存货值（库存周转分母）===
        BigDecimal monthCogs = BigDecimal.ZERO;
        BigDecimal currentInventoryValue = BigDecimal.ZERO;
        try {
            String cogsSql = "SELECT COALESCE(SUM(total_amount), 0) FROM stock_record " +
                    "WHERE deleted = 0 AND tenant_id = ? AND type = 4 " +
                    "AND record_time >= ?" + storeFilter;
            List<Object> cogsArgs = new ArrayList<>();
            cogsArgs.add(tenantId);
            cogsArgs.add(monthStart);
            if (storeId != null) cogsArgs.add(storeId);
            Map<String, Object> row = jdbc.queryForMap(cogsSql, cogsArgs.toArray());
            Object v = row.values().iterator().next();
            if (v != null) monthCogs = new BigDecimal(v.toString());

            // 库存货值 = sum(quantity * cost_price)；关联 stock_product 取成本价
            String invSql = "SELECT COALESCE(SUM(si.quantity * IFNULL(p.cost_price, 0)), 0) " +
                    "FROM stock_inventory si " +
                    "LEFT JOIN stock_product p ON si.product_id = p.id AND p.deleted = 0 " +
                    "WHERE si.deleted = 0 AND si.tenant_id = ?" + (storeId == null ? "" : " AND si.store_id = ?");
            List<Object> invArgs = new ArrayList<>();
            invArgs.add(tenantId);
            if (storeId != null) invArgs.add(storeId);
            Map<String, Object> invRow = jdbc.queryForMap(invSql, invArgs.toArray());
            Object iv = invRow.values().iterator().next();
            if (iv != null) currentInventoryValue = new BigDecimal(iv.toString());
        } catch (Exception e) {
            // 表不存在 / 列不存在时降级为 0
        }

        // 毛利率
        BigDecimal grossMargin = BigDecimal.ZERO;
        if (monthRevenue != null && monthRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossMargin = monthRevenue.subtract(monthCogs)
                    .divide(monthRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // === 2) 库存周转（月度倍数，年化 ×12）===
        BigDecimal turnover = BigDecimal.ZERO;
        if (currentInventoryValue.compareTo(BigDecimal.ZERO) > 0 && monthCogs.compareTo(BigDecimal.ZERO) > 0) {
            // 月度周转 = 月度耗材成本 / 当前库存货值；年化 ×12
            turnover = monthCogs.divide(currentInventoryValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(12))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // === 3) 耗材差异率（盘点差异，近 30 天已完成 status=2）===
        BigDecimal consumablesDiffRate = BigDecimal.ZERO;
        long totalCheckItems = 0;
        long totalDiffItems = 0;
        try {
            String chkSql = "SELECT COALESCE(SUM(total_items), 0) AS totalItems, " +
                    "       COALESCE(SUM(diff_items), 0) AS diffItems " +
                    "FROM stock_check " +
                    "WHERE deleted = 0 AND tenant_id = ? AND status = 2 " +
                    "AND check_date >= ?" + storeFilter;
            List<Object> chkArgs = new ArrayList<>();
            chkArgs.add(tenantId);
            chkArgs.add(thirtyDaysAgoDate);
            if (storeId != null) chkArgs.add(storeId);
            Map<String, Object> chkRow = jdbc.queryForMap(chkSql, chkArgs.toArray());
            Object ti = chkRow.get("totalItems");
            Object di = chkRow.get("diffItems");
            if (ti != null) totalCheckItems = ((Number) ti).longValue();
            if (di != null) totalDiffItems = ((Number) di).longValue();
            if (totalCheckItems > 0) {
                consumablesDiffRate = BigDecimal.valueOf(totalDiffItems)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalCheckItems), 2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            // ignore
        }

        // === 4) NPS（近 30 天，已发布 status=2）===
        BigDecimal nps = BigDecimal.ZERO;
        long promoters = 0;   // 9-10
        long passives = 0;    // 7-8
        long detractors = 0;  // 0-6
        long totalReviews = 0;
        try {
            String npsSql = "SELECT " +
                    "SUM(CASE WHEN overall_rating BETWEEN 9 AND 10 THEN 1 ELSE 0 END) AS promoters, " +
                    "SUM(CASE WHEN overall_rating BETWEEN 7 AND 8 THEN 1 ELSE 0 END) AS passives, " +
                    "SUM(CASE WHEN overall_rating BETWEEN 0 AND 6 THEN 1 ELSE 0 END) AS detractors, " +
                    "COUNT(*) AS total " +
                    "FROM crm_review " +
                    "WHERE deleted = 0 AND tenant_id = ? AND status = 2 " +
                    "AND overall_rating IS NOT NULL " +
                    "AND create_time >= ?" + storeFilter;
            List<Object> npsArgs = new ArrayList<>();
            npsArgs.add(tenantId);
            npsArgs.add(thirtyDaysAgo);
            if (storeId != null) npsArgs.add(storeId);
            Map<String, Object> npsRow = jdbc.queryForMap(npsSql, npsArgs.toArray());
            Object p = npsRow.get("promoters");
            Object pa = npsRow.get("passives");
            Object de = npsRow.get("detractors");
            Object tot = npsRow.get("total");
            if (p != null) promoters = ((Number) p).longValue();
            if (pa != null) passives = ((Number) pa).longValue();
            if (de != null) detractors = ((Number) de).longValue();
            if (tot != null) totalReviews = ((Number) tot).longValue();
            if (totalReviews > 0) {
                BigDecimal promoterPct = BigDecimal.valueOf(promoters)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);
                BigDecimal detractorPct = BigDecimal.valueOf(detractors)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);
                nps = promoterPct.subtract(detractorPct).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            // ignore
        }

        Map<String, Object> advanced = new LinkedHashMap<>();
        advanced.put("grossMargin", grossMargin);
        advanced.put("grossMarginMonthCogs", monthCogs);
        advanced.put("grossMarginMonthRevenue", monthRevenue == null ? BigDecimal.ZERO : monthRevenue);
        advanced.put("inventoryTurnover", turnover);
        advanced.put("inventoryTurnoverAnnualized", turnover);
        advanced.put("currentInventoryValue", currentInventoryValue);
        advanced.put("consumablesDiffRate", consumablesDiffRate);
        advanced.put("consumablesCheckTotalItems", totalCheckItems);
        advanced.put("consumablesCheckDiffItems", totalDiffItems);
        advanced.put("nps", nps);
        advanced.put("npsPromoters", promoters);
        advanced.put("npsPassives", passives);
        advanced.put("npsDetractors", detractors);
        advanced.put("npsTotalReviews", totalReviews);
        data.put("kpiAdvanced", advanced);
    }

    private Double pctChange(BigDecimal now, BigDecimal prev) {
        if (now == null || prev == null) return null;
        if (prev.compareTo(BigDecimal.ZERO) == 0) return now.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        return Math.round(now.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).doubleValue() * 10000.0) / 100.0;
    }

    // ==================== 营业日报 ====================

    @GetMapping("/biz/daily")
    @RequirePermission("report:biz:list")
    public Result<List<Map<String, Object>>> bizDaily(@RequestParam(required = false) Long storeId,
                                                      @RequestParam(defaultValue = "30") int days) {
        if (days <= 0 || days > 365) days = 30;
        Long t = tid();

        LambdaQueryWrapper<PosOrder> q = new LambdaQueryWrapper<>();
        q.eq(PosOrder::getTenantId, t);
        if (storeId != null) q.eq(PosOrder::getStoreId, storeId);
        q.ge(PosOrder::getPayTime, LocalDate.now().minusDays(days).atStartOfDay());
        List<PosOrder> orders = orderMapper.selectList(q);

        Map<LocalDate, List<PosOrder>> grouped = orders.stream()
                .filter(o -> o.getPayTime() != null)
                .collect(Collectors.groupingBy(o -> o.getPayTime().toLocalDate()));

        LambdaQueryWrapper<PosOrder> qLy = new LambdaQueryWrapper<>();
        qLy.eq(PosOrder::getTenantId, t);
        if (storeId != null) qLy.eq(PosOrder::getStoreId, storeId);
        qLy.ge(PosOrder::getPayTime, LocalDate.now().minusYears(1).minusDays(days).atStartOfDay());
        qLy.lt(PosOrder::getPayTime, LocalDate.now().minusYears(1).plusDays(1).atStartOfDay());
        List<PosOrder> ordersLy = orderMapper.selectList(qLy);

        Map<LocalDate, List<PosOrder>> groupedLy = ordersLy.stream()
                .filter(o -> o.getPayTime() != null)
                .collect(Collectors.groupingBy(o -> o.getPayTime().toLocalDate()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            List<PosOrder> dayOrders = grouped.getOrDefault(d, Collections.emptyList());

            BigDecimal revenue = dayOrders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                    .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LocalDate dLy = d.minusYears(1);
            BigDecimal lastYearRevenue = BigDecimal.ZERO;
            List<PosOrder> lyOrders = groupedLy.getOrDefault(dLy, Collections.emptyList());
            if (!lyOrders.isEmpty()) {
                lastYearRevenue = lyOrders.stream()
                        .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                        .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            long count = dayOrders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                    .count();
            BigDecimal avg = count == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

            Double vsLastYear = pctChange(revenue, lastYearRevenue);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.format(DateTimeFormatter.ISO_DATE));
            row.put("revenue", revenue);
            row.put("orderCount", count);
            row.put("avgOrder", avg);
            row.put("lastYearRevenue", lastYearRevenue);
            row.put("vsLastYear", vsLastYear);
            rows.add(row);
        }
        return Result.success(rows);
    }

    // ==================== P1-6 耗材差异率详情 ====================

    /**
     * P1-6 耗材差异率 tab：
     * POS 实耗（已完成的 pos_order 上的耗材项，对应服务消耗）与
     * 系统出库（stock_record.type=2/4）按月对比，揭示领用差异。
     *
     * <p>口径：
     * <ul>
     *   <li>POS 实耗 = stock_record.type=4（领用）total_amount，近 30 天</li>
     *   <li>系统出库 = stock_record.type=2（出库）total_amount，近 30 天</li>
     *   <li>差异率 = |POS实耗 - 系统出库| / MAX(POS实耗, 系统出库) × 100%</li>
     * </ul>
     */
    @GetMapping("/dashboard/consumables-variance")
    @RequirePermission("report:dashboard")
    public Result<Map<String, Object>> consumablesVariance(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "30") int days) {
        Long t = tid();
        if (days <= 0 || days > 365) days = 30;
        LocalDateTime startTime = LocalDate.now().minusDays(days).atStartOfDay();

        String storeFilter = storeId == null ? "" : " AND store_id = ? ";
        List<Object> args = new ArrayList<>();
        args.add(t);
        args.add(startTime);
        if (storeId != null) args.add(storeId);

        // 1) POS 实耗：type=4 领用
        BigDecimal posConsumption = BigDecimal.ZERO;
        long posConsumptionCount = 0;
        // 2) 系统出库：type=2
        BigDecimal systemOut = BigDecimal.ZERO;
        long systemOutCount = 0;

        try {
            // POS 领用
            String posSql = "SELECT COALESCE(SUM(ABS(quantity)), 0) AS qty, " +
                    "COALESCE(SUM(ABS(total_amount)), 0) AS amt, COUNT(*) AS cnt " +
                    "FROM stock_record " +
                    "WHERE deleted = 0 AND tenant_id = ? AND type = 4 AND record_time >= ?" + storeFilter;
            Map<String, Object> posRow = jdbc.queryForMap(posSql, args.toArray());
            Object qty = posRow.get("qty");
            Object amt = posRow.get("amt");
            Object cnt = posRow.get("cnt");
            if (qty != null) posConsumption = new BigDecimal(qty.toString());
            if (amt != null) posConsumption = new BigDecimal(amt.toString()); // 用金额作为主指标
            if (cnt != null) posConsumptionCount = ((Number) cnt).longValue();

            // 系统出库
            List<Object> outArgs = new ArrayList<>();
            outArgs.add(t);
            outArgs.add(startTime);
            if (storeId != null) outArgs.add(storeId);
            String outSql = "SELECT COALESCE(SUM(ABS(quantity)), 0) AS qty, " +
                    "COALESCE(SUM(ABS(total_amount)), 0) AS amt, COUNT(*) AS cnt " +
                    "FROM stock_record " +
                    "WHERE deleted = 0 AND tenant_id = ? AND type = 2 AND record_time >= ?" + storeFilter;
            Map<String, Object> outRow = jdbc.queryForMap(outSql, outArgs.toArray());
            Object outQty = outRow.get("qty");
            Object outAmt = outRow.get("amt");
            Object outCnt = outRow.get("cnt");
            if (outQty != null) systemOut = new BigDecimal(outQty.toString());
            if (outAmt != null) systemOut = new BigDecimal(outAmt.toString());
            if (outCnt != null) systemOutCount = ((Number) outCnt).longValue();
        } catch (Exception e) {
            // 表 / 字段不存在时降级为空
        }

        // 3) 按商品明细（type=4 领用 + type=2 出库）
        List<Map<String, Object>> byProduct = new ArrayList<>();
        try {
            List<Object> pArgs = new ArrayList<>();
            pArgs.add(t);
            pArgs.add(startTime);
            if (storeId != null) { pArgs.add(storeId); pArgs.add(storeId); }
            String byProdSql = "SELECT " +
                    "  p.product_name, p.product_code, p.unit, p.category, " +
                    "  SUM(CASE WHEN sr.type = 4 THEN ABS(sr.quantity) ELSE 0 END) AS pos_qty, " +
                    "  SUM(CASE WHEN sr.type = 2 THEN ABS(sr.quantity) ELSE 0 END) AS out_qty, " +
                    "  SUM(CASE WHEN sr.type = 4 THEN ABS(sr.total_amount) ELSE 0 END) AS pos_amt, " +
                    "  SUM(CASE WHEN sr.type = 2 THEN ABS(sr.total_amount) ELSE 0 END) AS out_amt " +
                    "FROM stock_record sr " +
                    "LEFT JOIN stock_product p ON sr.product_id = p.id AND p.deleted = 0 " +
                    "WHERE sr.deleted = 0 AND sr.tenant_id = ? " +
                    "  AND sr.type IN (2, 4) AND sr.record_time >= ? " + storeFilter +
                    "GROUP BY p.product_name, p.product_code, p.unit, p.category " +
                    "HAVING SUM(ABS(sr.quantity)) > 0 " +
                    "ORDER BY pos_amt DESC, out_amt DESC";
            List<Map<String, Object>> raw = jdbc.queryForList(byProdSql, pArgs.toArray());
            for (Map<String, Object> r : raw) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productName", r.get("product_name") == null ? "未知商品" : r.get("product_name"));
                row.put("productCode", r.get("product_code"));
                row.put("unit", r.get("unit") == null ? "—" : r.get("unit"));
                row.put("category", r.get("category") == null ? "耗材" : r.get("category"));

                long pq = ((Number) (r.get("pos_qty") == null ? 0 : r.get("pos_qty"))).longValue();
                long oq = ((Number) (r.get("out_qty") == null ? 0 : r.get("out_qty"))).longValue();
                BigDecimal pa = new BigDecimal(r.get("pos_amt") == null ? "0" : r.get("pos_amt").toString());
                BigDecimal oa = new BigDecimal(r.get("out_amt") == null ? "0" : r.get("out_amt").toString());

                row.put("posQty", pq);
                row.put("systemOutQty", oq);
                row.put("posAmount", pa);
                row.put("systemOutAmount", oa);

                // 差异量 / 差异金额
                long diffQty = Math.abs(pq - oq);
                BigDecimal diffAmt = pa.subtract(oa).abs();
                row.put("diffQty", diffQty);
                row.put("diffAmount", diffAmt);

                // 差异率（以较大值为分母）
                BigDecimal denom = pa.max(oa);
                BigDecimal varianceRate = BigDecimal.ZERO;
                if (denom.compareTo(BigDecimal.ZERO) > 0) {
                    varianceRate = diffAmt.divide(denom, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
                row.put("varianceRate", varianceRate);
                byProduct.add(row);
            }
        } catch (Exception e) {
            // 降级
        }

        // 4) 总体差异率
        BigDecimal totalPos = posConsumption;
        BigDecimal totalOut = systemOut;
        BigDecimal denom = totalPos.max(totalOut);
        BigDecimal totalVarianceRate = BigDecimal.ZERO;
        if (denom.compareTo(BigDecimal.ZERO) > 0) {
            totalVarianceRate = totalPos.subtract(totalOut).abs()
                    .divide(denom, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("startTime", startTime.toLocalDate().toString());
        result.put("endTime", LocalDate.now().toString());
        result.put("posConsumption", totalPos);
        result.put("posConsumptionCount", posConsumptionCount);
        result.put("systemOut", totalOut);
        result.put("systemOutCount", systemOutCount);
        result.put("totalVarianceRate", totalVarianceRate);
        result.put("totalDiffAmount", totalPos.subtract(totalOut).abs());
        result.put("byProduct", byProduct);
        return Result.success(result);
    }

    // ==================== P1-6 库存周转月趋势（近 12 个月） ====================

    /**
     * P1-6 库存周转月趋势：
     * 近 12 个月库存周转倍数（年化 = 月度 × 12）。
     * 公式：月度库存周转 = 月度耗材出库金额 / 月末库存货值
     */
    @GetMapping("/dashboard/inventory-turnover-trend")
    @RequirePermission("report:dashboard")
    public Result<List<Map<String, Object>>> inventoryTurnoverTrend(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "12") int months) {
        Long t = tid();
        if (months <= 0 || months > 60) months = 12;
        LocalDate today = LocalDate.now();

        String storeFilter = storeId == null ? "" : " AND si.store_id = ? ";
        String storeFilter2 = storeId == null ? "" : " AND sr.store_id = ? ";

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            String monthLabel = monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            BigDecimal monthCogs = BigDecimal.ZERO;
            BigDecimal endInventoryValue = BigDecimal.ZERO;

            try {
                // 月度耗材出库 type=4
                List<Object> cogsArgs = new ArrayList<>();
                cogsArgs.add(t);
                cogsArgs.add(monthStart.atStartOfDay());
                cogsArgs.add(monthEnd.plusDays(1).atStartOfDay());
                if (storeId != null) { cogsArgs.add(storeId); }
                String cogsSql = "SELECT COALESCE(SUM(ABS(total_amount)), 0) AS amt " +
                        "FROM stock_record " +
                        "WHERE deleted = 0 AND tenant_id = ? AND type = 4 " +
                        "AND record_time >= ? AND record_time < ?" + storeFilter2;
                Map<String, Object> cogsRow = jdbc.queryForMap(cogsSql, cogsArgs.toArray());
                Object cogsAmt = cogsRow.values().iterator().next();
                if (cogsAmt != null) monthCogs = new BigDecimal(cogsAmt.toString());

                // 月末库存货值
                List<Object> invArgs = new ArrayList<>();
                invArgs.add(t);
                if (storeId != null) invArgs.add(storeId);
                String invSql = "SELECT COALESCE(SUM(si.quantity * IFNULL(p.cost_price, 0)), 0) AS val " +
                        "FROM stock_inventory si " +
                        "LEFT JOIN stock_product p ON si.product_id = p.id AND p.deleted = 0 " +
                        "WHERE si.deleted = 0 AND si.tenant_id = ?" + storeFilter;
                Map<String, Object> invRow = jdbc.queryForMap(invSql, invArgs.toArray());
                Object invVal = invRow.values().iterator().next();
                if (invVal != null) endInventoryValue = new BigDecimal(invVal.toString());
            } catch (Exception e) {
                // 降级
            }

            BigDecimal monthlyTurnover = BigDecimal.ZERO;
            BigDecimal annualizedTurnover = BigDecimal.ZERO;
            if (endInventoryValue.compareTo(BigDecimal.ZERO) > 0 && monthCogs.compareTo(BigDecimal.ZERO) > 0) {
                monthlyTurnover = monthCogs.divide(endInventoryValue, 4, RoundingMode.HALF_UP)
                        .setScale(4, RoundingMode.HALF_UP);
                annualizedTurnover = monthlyTurnover.multiply(BigDecimal.valueOf(12))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", monthLabel);
            row.put("monthlyCogs", monthCogs);
            row.put("endInventoryValue", endInventoryValue);
            row.put("monthlyTurnover", monthlyTurnover);
            row.put("annualizedTurnover", annualizedTurnover);
            trend.add(row);
        }

        return Result.success(trend);
    }

    // ==================== 营业月报 ====================

    @GetMapping("/biz/monthly")
    @RequirePermission("report:biz:list")
    public Result<List<Map<String, Object>>> bizMonthly(@RequestParam(required = false) Long storeId,
                                                        @RequestParam(defaultValue = "12") int months) {
        if (months <= 0 || months > 60) months = 12;
        Long t = tid();

        LambdaQueryWrapper<PosOrder> q = new LambdaQueryWrapper<>();
        q.eq(PosOrder::getTenantId, t);
        if (storeId != null) q.eq(PosOrder::getStoreId, storeId);
        List<PosOrder> orders = orderMapper.selectList(q);

        Map<String, List<PosOrder>> grouped = orders.stream()
                .filter(o -> o.getPayTime() != null)
                .collect(Collectors.groupingBy(o -> {
                    LocalDateTime pt = o.getPayTime();
                    return pt.getYear() + "-" + String.format("%02d", pt.getMonthValue());
                }));

        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate m = today.minusMonths(i).withDayOfMonth(1);
            String key = m.getYear() + "-" + String.format("%02d", m.getMonthValue());
            List<PosOrder> monthOrders = grouped.getOrDefault(key, Collections.emptyList());

            BigDecimal revenue = monthOrders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                    .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = monthOrders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                    .count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", key);
            row.put("revenue", revenue);
            row.put("orderCount", count);
            rows.add(row);
        }
        return Result.success(rows);
    }

    // ==================== 会员分析 ====================

    @GetMapping("/member/analysis")
    @RequirePermission("report:biz:list")
    public Result<Map<String, Object>> memberAnalysis(@RequestParam(required = false) Long storeId) {
        Long t = tid();
        LambdaQueryWrapper<Member> mq = new LambdaQueryWrapper<>();
        mq.eq(Member::getTenantId, t);
        if (storeId != null) mq.eq(Member::getStoreId, storeId);
        List<Member> members = memberMapper.selectList(mq);

        Map<String, Long> byLevel = members.stream()
                .filter(m -> m.getLevelId() != null)
                .collect(Collectors.groupingBy(m -> "L" + m.getLevelId(), Collectors.counting()));

        Map<String, Long> byStore = members.stream()
                .collect(Collectors.groupingBy(m -> m.getStoreId() == null ? "默认门店" : "门店" + m.getStoreId(), Collectors.counting()));

        Map<String, Long> byGender = members.stream()
                .filter(m -> m.getGender() != null)
                .collect(Collectors.groupingBy(m -> m.getGender() == 1 ? "男" : "女", Collectors.counting()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", members.size());
        data.put("byLevel", byLevel);
        data.put("byStore", byStore);
        data.put("byGender", byGender);
        return Result.success(data);
    }

    // ==================== 员工绩效 ====================

    @GetMapping("/employee/performance")
    @RequirePermission("report:biz:list")
    public Result<List<Map<String, Object>>> employeePerformance(@RequestParam(required = false) Long storeId,
                                                                  @RequestParam(defaultValue = "30") int days) {
        if (days <= 0 || days > 365) days = 30;
        Long t = tid();

        LambdaQueryWrapper<PosOrder> q = new LambdaQueryWrapper<>();
        q.eq(PosOrder::getTenantId, t);
        if (storeId != null) q.eq(PosOrder::getStoreId, storeId);
        q.ge(PosOrder::getPayTime, LocalDate.now().minusDays(days).atStartOfDay());
        List<PosOrder> orders = orderMapper.selectList(q);

        Map<Long, List<PosOrder>> grouped = orders.stream()
                .filter(o -> o.getCashierId() != null)
                .collect(Collectors.groupingBy(PosOrder::getCashierId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<PosOrder>> e : grouped.entrySet()) {
            Employee emp = employeeMapper.selectById(e.getKey());
            if (emp == null) continue;
            BigDecimal revenue = e.getValue().stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                    .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", e.getKey());
            row.put("employeeName", emp.getEmployeeName());
            row.put("orderCount", e.getValue().size());
            row.put("revenue", revenue);
            rows.add(row);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("revenue")).compareTo((BigDecimal) a.get("revenue")));
        return Result.success(rows);
    }

    // ==================== 销售汇总（按支付方式） ====================

    @GetMapping("/biz/payType")
    @RequirePermission("report:biz:list")
    public Result<List<Map<String, Object>>> payTypeBreakdown(@RequestParam(required = false) Long storeId,
                                                               @RequestParam(defaultValue = "30") int days) {
        if (days <= 0 || days > 365) days = 30;
        Long t = tid();

        LambdaQueryWrapper<PosPayment> q = new LambdaQueryWrapper<>();
        q.eq(PosPayment::getTenantId, t);
        q.ge(PosPayment::getPayTime, LocalDate.now().minusDays(days).atStartOfDay());
        q.eq(PosPayment::getStatus, 1);
        List<PosPayment> payments = paymentMapper.selectList(q);

        Map<String, List<PosPayment>> grouped = payments.stream()
                .filter(p -> p.getPayTime() != null)
                .collect(Collectors.groupingBy(p -> p.getMethod() == null ? "未知" : p.getMethod()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<PosPayment>> e : grouped.entrySet()) {
            BigDecimal amount = e.getValue().stream()
                    .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", e.getKey());
            row.put("count", e.getValue().size());
            row.put("amount", amount);
            rows.add(row);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));
        return Result.success(rows);
    }

    // ==================== 报表配置 ====================

    @GetMapping("/config/list")
    @RequirePermission("report:caliber")
    public Result<List<ReportConfig>> configList(@RequestParam(required = false) Long storeId,
                                                  @RequestParam(required = false) Long userId) {
        Long t = tid();
        LambdaQueryWrapper<ReportConfig> q = new LambdaQueryWrapper<>();
        q.eq(ReportConfig::getTenantId, t);
        if (storeId != null) q.eq(ReportConfig::getStoreId, storeId);
        if (userId != null) q.eq(ReportConfig::getCreatedBy, userId);
        q.orderByDesc(ReportConfig::getIsPinned)
          .orderByAsc(ReportConfig::getSortOrder)
          .orderByDesc(ReportConfig::getCreatedAt);
        return Result.success(configMapper.selectList(q));
    }

    @PostMapping("/config")
    @RequirePermission("report:caliber:edit")
    public Result<ReportConfig> saveConfig(@RequestBody ReportConfig cfg) {
        Long t = tid();
        cfg.setTenantId(t);
        if (cfg.getStoreId() == null) cfg.setStoreId(1L);
        if (cfg.getIsPinned() == null) cfg.setIsPinned(0);
        if (cfg.getSortOrder() == null) cfg.setSortOrder(0);
        if (cfg.getId() == null) {
            configMapper.insert(cfg);
        } else {
            ReportConfig exist = configMapper.selectById(cfg.getId());
            if (exist == null || !t.equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "配置不存在");
            configMapper.updateById(cfg);
        }
        return Result.success(cfg);
    }

    @DeleteMapping("/config/{id}")
    @RequirePermission("report:caliber:edit")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        Long t = tid();
        ReportConfig exist = configMapper.selectById(id);
        if (exist == null || !t.equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "配置不存在");
        configMapper.deleteById(id);
        return Result.success(null);
    }

    // ==================== 导出管理 ====================

    @GetMapping("/export/list")
    @RequirePermission("report:export:list")
    public Result<?> exportList(@RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "20") int pageSize,
                                                           @RequestParam(required = false) Integer type,
                                                           @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<ReportExportLog> q = new LambdaQueryWrapper<>();
        q.eq(ReportExportLog::getTenantId, t);
        if (type != null) q.eq(ReportExportLog::getType, type);
        if (status != null) q.eq(ReportExportLog::getStatus, status);
        q.orderByDesc(ReportExportLog::getCreatedAt);
        Page<ReportExportLog> pg = exportLogMapper.selectPage(new Page<>(pageNum, pageSize), q);

        LambdaQueryWrapper<ReportExportLog> baseQ = new LambdaQueryWrapper<>();
        baseQ.eq(ReportExportLog::getTenantId, t);
        Long totalCount = exportLogMapper.selectCount(baseQ);
        LambdaQueryWrapper<ReportExportLog> successQ = new LambdaQueryWrapper<>();
        successQ.eq(ReportExportLog::getTenantId, t).eq(ReportExportLog::getStatus, 2);
        Long successCount = exportLogMapper.selectCount(successQ);
        LambdaQueryWrapper<ReportExportLog> colQ = new LambdaQueryWrapper<>();
        colQ.eq(ReportExportLog::getTenantId, t).select(ReportExportLog::getRowCount, ReportExportLog::getFileSize);
        List<ReportExportLog> allLogs = exportLogMapper.selectList(colQ);
        long totalRows = allLogs.stream().mapToLong(l -> l.getRowCount() == null ? 0 : l.getRowCount()).sum();
        long totalSize = allLogs.stream().mapToLong(l -> l.getFileSize() == null ? 0 : l.getFileSize()).sum();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("totalCount", totalCount);
        meta.put("successCount", successCount);
        meta.put("totalRows", totalRows);
        meta.put("totalSize", totalSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", pg.getRecords());
        result.put("total", pg.getTotal());
        result.put("meta", meta);
        return Result.success(result);
    }

    @PostMapping("/export/create")
    @RequirePermission("report:export:add")
    public Result<ReportExportLog> createExport(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String name = String.valueOf(body.getOrDefault("name", "导出任务"));
        Integer type = Integer.valueOf(String.valueOf(body.getOrDefault("type", "1")));
        String dateRange = String.valueOf(body.getOrDefault("dateRange", "last_30_days"));

        Long userId = null;
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try { userId = jwtUtil.extractUserId(token.substring(7)); } catch (Exception ignore) {}
        }

        Long t = tid();
        ReportExportLog log = new ReportExportLog();
        log.setTenantId(t);
        log.setStoreId(1L);
        log.setName(name);
        log.setType(type);
        log.setDateRange(dateRange);
        log.setStatus(1);
        log.setExportBy(userId);
        exportLogMapper.insert(log);

        Long logId = log.getId();
        CompletableFuture.runAsync(() -> {
            try {
                byte[] data = buildExportFileData(type, dateRange);
                int rows = (int) Math.min(Integer.MAX_VALUE, countRows(type, dateRange));
                String path = saveFile(data, logId);
                jdbc.update("UPDATE report_export_log SET status=2,row_count=?,file_size=?,finished_at=NOW(),file_path=? WHERE id=? AND tenant_id=?",
                        rows, (long) data.length, path, logId, t);
            } catch (Exception e) {
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                jdbc.update("UPDATE report_export_log SET status=3,error_msg=?,finished_at=NOW() WHERE id=? AND tenant_id=?",
                        msg, logId, t);
            }
        });

        return Result.success(log);
    }

    private String saveFile(byte[] data, Long logId) {
        String dir = System.getProperty("user.dir") + File.separator + "exports";
        new File(dir).mkdirs();
        String fileName = "export_" + logId + "_" + System.currentTimeMillis() + ".xlsx";
        String path = dir + File.separator + fileName;
        try {
            Files.write(Paths.get(path), data);
        } catch (IOException ignore) {}
        return path;
    }

    @GetMapping("/export/download")
    @RequirePermission("report:export:download")
    public void downloadExport(@RequestParam Long id, HttpServletResponse response) throws IOException {
        Long t = tid();
        ReportExportLog log = exportLogMapper.selectById(id);
        if (log == null || !t.equals(log.getTenantId()) || log.getStatus() != 2) {
            response.setStatus(404);
            return;
        }
        byte[] data;
        String displayName;
        if (log.getFilePath() != null && new File(log.getFilePath()).exists()) {
            data = Files.readAllBytes(Paths.get(log.getFilePath()));
            displayName = log.getName() + ".xlsx";
        } else {
            data = buildExportFileData(log.getType(), log.getDateRange());
            displayName = (log.getName() != null ? log.getName() : "报表") + "_" + System.currentTimeMillis() + ".xlsx";
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(displayName, StandardCharsets.UTF_8));
        response.setHeader("Content-Length", String.valueOf(data.length));
        try (OutputStream out = response.getOutputStream()) {
            out.write(data);
        }
    }

    private byte[] buildExportFileData(Integer type, String dateRange) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("报表");
            Row header = sheet.createRow(0);
            String[] headers = getExportHeaders(type);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
            }
            List<String[]> rows = getExportRows(type, dateRange);
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] arr = rows.get(r);
                for (int i = 0; i < arr.length; i++) {
                    row.createCell(i).setCellValue(arr[i]);
                }
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private long countRows(Integer type, String dateRange) {
        return getExportRows(type, dateRange).size();
    }

    private String[] getExportHeaders(Integer type) {
        return switch (type == null ? 1 : type) {
            case 2 -> new String[]{"月份", "订单数", "营收(元)", "占比(%)"};
            case 3 -> new String[]{"维度", "类型", "数量"};
            case 4 -> new String[]{"员工ID", "员工姓名", "订单数", "营收(元)"};
            case 5 -> new String[]{"支付方式", "笔数", "金额(元)"};
            default -> new String[]{"日期", "订单数", "营收(元)", "客单价(元)", "去年同期(元)", "同比(%)", "占比(%)"};
        };
    }

    private List<String[]> getExportRows(Integer type, String dateRange) {
        int days = parseDays(dateRange);
        List<String[]> rows = new ArrayList<>();
        try {
            if (type == null || type == 1) {
                Result<List<Map<String, Object>>> r = bizDaily(null, days);
                if (r.getCode() == 200 && r.getData() != null) {
                    double total = r.getData().stream()
                            .filter(m -> m.get("revenue") != null)
                            .mapToDouble(m -> ((BigDecimal) m.get("revenue")).doubleValue())
                            .sum();
                    for (Map<String, Object> m : r.getData()) {
                        BigDecimal rev = m.get("revenue") == null ? BigDecimal.ZERO : (BigDecimal) m.get("revenue");
                        BigDecimal ly = m.get("lastYearRevenue") == null ? BigDecimal.ZERO : (BigDecimal) m.get("lastYearRevenue");
                        Double vs = m.get("vsLastYear") == null ? null : (Double) m.get("vsLastYear");
                        double pct = total == 0 ? 0 : (rev.doubleValue() / total * 100);
                        String vsStr = vs == null ? "—" : String.format("%+.1f%%", vs);
                        rows.add(new String[]{
                                String.valueOf(m.get("date")),
                                String.valueOf(m.get("orderCount")),
                                rev.toPlainString(),
                                String.valueOf(m.get("avgOrder")),
                                ly.compareTo(BigDecimal.ZERO) > 0 ? ly.toPlainString() : "—",
                                vsStr,
                                String.format("%.1f%%", pct)
                        });
                    }
                }
            } else if (type == 2) {
                int months = Math.max(1, days / 30);
                Result<List<Map<String, Object>>> r = bizMonthly(null, months);
                if (r.getCode() == 200 && r.getData() != null) {
                    double total = r.getData().stream()
                            .filter(m -> m.get("revenue") != null)
                            .mapToDouble(m -> ((BigDecimal) m.get("revenue")).doubleValue())
                            .sum();
                    for (Map<String, Object> m : r.getData()) {
                        BigDecimal rev = m.get("revenue") == null ? BigDecimal.ZERO : (BigDecimal) m.get("revenue");
                        double pct = total == 0 ? 0 : (rev.doubleValue() / total * 100);
                        rows.add(new String[]{
                                String.valueOf(m.get("month")),
                                String.valueOf(m.get("orderCount")),
                                rev.toPlainString(),
                                String.format("%.1f%%", pct)
                        });
                    }
                }
            } else if (type == 3) {
                Result<Map<String, Object>> r = memberAnalysis(null);
                if (r.getCode() == 200 && r.getData() != null) {
                    Map<String, Long> byLevel = (Map<String, Long>) r.getData().get("byLevel");
                    if (byLevel != null) byLevel.forEach((k, v) ->
                            rows.add(new String[]{"会员等级", k, String.valueOf(v)}));
                    Map<String, Long> byStore = (Map<String, Long>) r.getData().get("byStore");
                    if (byStore != null) byStore.forEach((k, v) ->
                            rows.add(new String[]{"门店", k, String.valueOf(v)}));
                }
            } else if (type == 4) {
                Result<List<Map<String, Object>>> r = employeePerformance(null, days);
                if (r.getCode() == 200 && r.getData() != null) {
                    for (Map<String, Object> m : r.getData()) {
                        rows.add(new String[]{
                                String.valueOf(m.get("employeeId")),
                                String.valueOf(m.get("employeeName")),
                                String.valueOf(m.get("orderCount")),
                                String.valueOf(m.get("revenue"))
                        });
                    }
                }
            } else if (type == 5) {
                Result<List<Map<String, Object>>> r = payTypeBreakdown(null, days);
                if (r.getCode() == 200 && r.getData() != null) {
                    for (Map<String, Object> m : r.getData()) {
                        rows.add(new String[]{
                                String.valueOf(m.get("payTypeName")),
                                String.valueOf(m.get("count")),
                                String.valueOf(m.get("amount"))
                        });
                    }
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return rows;
    }

    private int parseDays(String dateRange) {
        if (dateRange == null) return 30;
        return switch (dateRange) {
            case "today" -> 1;
            case "last_7_days" -> 7;
            case "last_30_days" -> 30;
            case "last_quarter" -> 90;
            default -> 30;
        };
    }
}