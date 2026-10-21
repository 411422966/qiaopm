package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * M11-03 复购率报表（真实业务口径，2026-09-04 定稿）
 *
 * <p>核心公式（严格口径，与 PRD 5.1 一致）：
 * <pre>
 *   复购率 = (30 天内有 ≥2 次消费订单的会员数) / (30 天内有 ≥1 次消费订单的活跃会员数) × 100%
 * </pre>
 *
 * <p>口径要点：
 * <ul>
 *   <li><b>窗口</b>：滚动 30 天 [endDate-29, endDate]，按 pay_time 判定</li>
 *   <li><b>消费订单</b>：pos_order.type = 1（消费）且 status = 2（已支付）且 deleted = 0</li>
 *   <li><b>排除</b>：充值（type=2/3）、退款（status=3）、进行中（status=1）、撤销（status=4）</li>
 *   <li><b>分母</b>：窗口内活跃会员数（有 ≥1 次消费订单的非空 member_id）</li>
 *   <li><b>分子</b>：其中有 ≥2 次消费订单的会员数</li>
 *   <li><b>维度</b>：默认 tenant，可选 storeId</li>
 *   <li><b>目标</b>：> 40%（PRD 5.1）</li>
 * </ul>
 */
@RestController
@RequestMapping("/intel/repurchase")
@RequiredArgsConstructor
public class RepurchaseRateController {

    @Autowired
    private JdbcTemplate jdbc;

    private static final int WINDOW_DAYS = 30;

    // ================================================================
    // 1. 总览：当前 30 天复购率
    // ================================================================
    @GetMapping("/overview")
    @RequirePermission("report:repurchase:view")
    public Result<Map<String, Object>> overview(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate end = (endDate == null || endDate.isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE);
        LocalDate start = end.minusDays(WINDOW_DAYS - 1L);

        Map<String, Object> data = computeRate(tenantId, storeId, start, end);
        return Result.success(data);
    }

    // ================================================================
    // 2. 月度趋势：最近 N 个月（每月口径 = 当月自然月内复购率）
    // ================================================================
    @GetMapping("/trend")
    @RequirePermission("report:repurchase:view")
    public Result<List<Map<String, Object>>> trend(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "6") int months) {
        if (months < 1 || months > 24) months = 6;
        Long tenantId = TenantContextHolder.get().getTenantId();

        List<Map<String, Object>> rows = new ArrayList<>();
        DateTimeFormatter ymf = DateTimeFormatter.ofPattern("yyyy-MM");
        YearMonthCursor cursor = new YearMonthCursor(LocalDate.now());
        for (int i = months - 1; i >= 0; i--) {
            LocalDate monthStart = cursor.minus(i).firstOfMonth();
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            // 自然月复购率
            Map<String, Object> m = computeRate(tenantId, storeId, monthStart, monthEnd);
            Map<String, Object> row = new HashMap<>();
            row.put("ym", monthStart.format(ymf));
            row.put("activeMembers", m.get("activeMembers"));
            row.put("repurchasedMembers", m.get("repurchasedMembers"));
            row.put("reorderMembers3plus", m.get("reorderMembers3plus"));
            row.put("repurchaseRate", m.get("repurchaseRate"));
            row.put("avgOrderCount", m.get("avgOrderCount"));
            rows.add(row);
        }
        return Result.success(rows);
    }

    // ================================================================
    // 3. 分桶：消费层级（L1/L2/L3/L4）复购率分布
    //    L1 = 仅 1 次（潜在复购），L2 = 2 次（已复购），L3 = 3-4 次（高频），L4 = 5+ 次（忠诚）
    // ================================================================
    @GetMapping("/tier")
    @RequirePermission("report:repurchase:view")
    public Result<Map<String, Object>> tier(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate end = (endDate == null || endDate.isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE);
        LocalDate start = end.minusDays(WINDOW_DAYS - 1L);

        StringBuilder sql = new StringBuilder()
            .append("SELECT ")
            .append("  CASE ")
            .append("    WHEN cnt = 1 THEN 'L1_POTENTIAL' ")
            .append("    WHEN cnt = 2 THEN 'L2_REPURCHASED' ")
            .append("    WHEN cnt BETWEEN 3 AND 4 THEN 'L3_HIGH' ")
            .append("    ELSE 'L4_LOYAL' END AS tier, ")
            .append("  COUNT(*) AS member_count, ")
            .append("  SUM(amount) AS total_amount ")
            .append("FROM ( ")
            .append("  SELECT o.member_id, COUNT(*) AS cnt, SUM(o.paid_amount) AS amount ")
            .append("  FROM pos_order o ")
            .append("  WHERE o.deleted = 0 AND o.type = 1 AND o.status = 2 ")
            .append("    AND o.pay_time >= ? AND o.pay_time < DATE_ADD(?, INTERVAL 1 DAY) ")
            .append("    AND o.tenant_id = ? AND o.member_id IS NOT NULL ");
        if (storeId != null) sql.append("    AND o.store_id = ? ");
        sql.append("  GROUP BY o.member_id) t GROUP BY tier ORDER BY tier");

        List<Object> args = new ArrayList<>();
        args.add(start);
        args.add(end);
        args.add(tenantId);
        if (storeId != null) args.add(storeId);

        List<Map<String, Object>> dist = jdbc.queryForList(sql.toString(), args.toArray());
        long totalMembers = dist.stream().mapToLong(r -> ((Number) r.get("member_count")).longValue()).sum();

        // 补 0 行
        Map<String, Long> memberMap = new HashMap<>();
        Map<String, BigDecimal> amountMap = new HashMap<>();
        for (Map<String, Object> r : dist) {
            memberMap.put((String) r.get("tier"), ((Number) r.get("member_count")).longValue());
            amountMap.put((String) r.get("tier"),
                    r.get("total_amount") == null ? BigDecimal.ZERO
                            : new BigDecimal(r.get("total_amount").toString()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("windowStart", start.toString());
        data.put("windowEnd", end.toString());
        data.put("totalMembers", totalMembers);
        for (String tier : new String[]{"L1_POTENTIAL", "L2_REPURCHASED", "L3_HIGH", "L4_LOYAL"}) {
            long cnt = memberMap.getOrDefault(tier, 0L);
            BigDecimal ratio = totalMembers == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(cnt).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalMembers), 2, RoundingMode.HALF_UP);
            Map<String, Object> tierData = new HashMap<>();
            tierData.put("memberCount", cnt);
            tierData.put("totalAmount", amountMap.getOrDefault(tier, BigDecimal.ZERO));
            tierData.put("ratio", ratio);
            data.put(tier, tierData);
        }
        return Result.success(data);
    }

    // ================================================================
    // 核心计算
    // ================================================================
    private Map<String, Object> computeRate(Long tenantId, Long storeId, LocalDate start, LocalDate end) {
        StringBuilder sql = new StringBuilder()
            .append("SELECT member_id, COUNT(*) AS order_count, SUM(paid_amount) AS amount ")
            .append("FROM pos_order ")
            .append("WHERE deleted = 0 AND type = 1 AND status = 2 ")
            .append("  AND pay_time >= ? AND pay_time < DATE_ADD(?, INTERVAL 1 DAY) ")
            .append("  AND tenant_id = ? AND member_id IS NOT NULL ");
        if (storeId != null) sql.append("  AND store_id = ? ");
        sql.append("GROUP BY member_id");

        List<Object> args = new ArrayList<>();
        args.add(start);
        args.add(end);
        args.add(tenantId);
        if (storeId != null) args.add(storeId);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        long activeMembers = rows.size();
        long repurchased = rows.stream().filter(r -> ((Number) r.get("order_count")).intValue() >= 2).count();
        long reorder3plus = rows.stream().filter(r -> ((Number) r.get("order_count")).intValue() >= 3).count();
        long totalOrders = rows.stream().mapToLong(r -> ((Number) r.get("order_count")).longValue()).sum();
        BigDecimal totalAmount = rows.stream()
                .map(r -> r.get("amount") == null ? BigDecimal.ZERO : new BigDecimal(r.get("amount").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rate = activeMembers == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(repurchased).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(activeMembers), 2, RoundingMode.HALF_UP);
        BigDecimal avgOrders = activeMembers == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalOrders).divide(BigDecimal.valueOf(activeMembers), 2, RoundingMode.HALF_UP);
        BigDecimal avgRepurchaseOrders = repurchased == 0
                ? BigDecimal.ZERO
                : rows.stream().filter(r -> ((Number) r.get("order_count")).intValue() >= 2)
                        .map(r -> BigDecimal.valueOf(((Number) r.get("order_count")).longValue()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(repurchased), 2, RoundingMode.HALF_UP);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("windowStart", start.toString());
        data.put("windowEnd", end.toString());
        data.put("windowDays", WINDOW_DAYS);
        data.put("activeMembers", activeMembers);                      // 分母
        data.put("repurchasedMembers", repurchased);                  // 分子
        data.put("reorderMembers3plus", reorder3plus);                // 高频复购（≥3 次）
        data.put("repurchaseRate", rate);                             // 复购率 %
        data.put("avgOrderCount", avgOrders);                         // 人均订单数
        data.put("avgRepurchaseOrders", avgRepurchaseOrders);         // 复购会员人均订单数
        data.put("totalOrders", totalOrders);
        data.put("totalAmount", totalAmount);
        return data;
    }

    // 简单 YearMonth 游标（不依赖 java.time.YearMonth 的版本兼容性）
    private static class YearMonthCursor {
        private final int year;
        private final int month;
        YearMonthCursor(LocalDate d) { this.year = d.getYear(); this.month = d.getMonthValue(); }
        YearMonthCursor minus(int i) {
            int m = month - i;
            int y = year;
            while (m <= 0) { m += 12; y--; }
            return new YearMonthCursor(y, m);
        }
        YearMonthCursor(int y, int m) { this.year = y; this.month = m; }
        LocalDate firstOfMonth() { return LocalDate.of(year, month, 1); }
    }
}