package com.meiyetec.controller;

import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * LTV 示例数据生成器
 *
 * 生成内容：
 *   1. 100 个模拟会员（crm_member）
 *   2. ~1000 笔历史订单（crm_order），覆盖 12 个月内
 *   3. 最新 LTV 快照（crm_ltv_snapshot），含历史 + 预测值
 *   4. 12 个月的 LTV 趋势快照（每月一条）
 *
 * 三种模式：
 *   - full：造新数据（默认）
 *   - reset：先清空再重建
 *   - soft：仅生成快照（假设会员和订单已存在）
 */
@Slf4j
@RestController
@RequestMapping("/intel/ltv/seed")
@RequiredArgsConstructor
public class LtvSeedController {

    private final JdbcTemplate jdbc;

    @PostMapping
    public Result<Map<String, Object>> seed(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "未识别租户");

        String mode = body == null || body.get("mode") == null
                ? "full" : body.get("mode").toString().toLowerCase();
        String operator = body == null ? "seed" : (String) body.get("operatorName");

        Map<String, Object> data = new HashMap<>();

        if ("reset".equals(mode)) {
            // 清理旧数据
            int snapDel = jdbc.update("DELETE FROM crm_ltv_snapshot WHERE tenant_id = ?", tenantId);
            int orderDel = jdbc.update("DELETE FROM crm_order WHERE tenant_id = ?", tenantId);
            int memberDel = jdbc.update("DELETE FROM crm_member WHERE tenant_id = ? AND deleted = 0", tenantId);
            data.put("clearedSnapshots", snapDel);
            data.put("clearedOrders", orderDel);
            data.put("clearedMembers", memberDel);
        }

        if ("full".equals(mode) || "reset".equals(mode)) {
            // 生成会员 + 订单
            int[] counts = generateDemoMembersAndOrders(tenantId);
            data.put("membersInserted", counts[0]);
            data.put("ordersInserted", counts[1]);
        }

        // 生成 LTV 快照
        int snapshots = generateLtvSnapshots(tenantId, operator);
        data.put("snapshotsInserted", snapshots);
        data.put("mode", mode);

        return Result.success("LTV 示例数据已生成", data);
    }

    // ================================================================
    // 会员 + 订单生成
    // ================================================================

    private int[] generateDemoMembersAndOrders(Long tenantId) {
        // LTV 分桶：VIP / 高价值 / 中价值 / 低价值 / 新客
        String[][] archetypes = {
            // name,    minOrders, maxOrders, minAmount, maxAmount, recencyDays
            {"VIP客户",     "15", "25", "500",  "2000", "5,30"},
            {"高价值会员",  "8",  "15", "300",  "1500", "10,60"},
            {"中价值会员",  "4",  "8",  "150",  "800",  "30,120"},
            {"低价值会员",  "1",  "4",  "80",   "400",  "60,200"},
            {"新客",        "1",  "2",  "100",  "500",  "5,20"}
        };

        int totalMembers = 0;
        int totalOrders = 0;
        Random rnd = new Random(2026);

        for (String[] arch : archetypes) {
            String name = arch[0];
            int minOrders = Integer.parseInt(arch[1]);
            int maxOrders = Integer.parseInt(arch[2]);
            int minAmt = Integer.parseInt(arch[3]);
            int maxAmt = Integer.parseInt(arch[4]);
            int[] recencyRange = parseIntPair(arch[5]);

            // 每类生成 20 个会员
            for (int i = 0; i < 20; i++) {
                String phone = "139" + String.format("%08d", rnd.nextInt(100_000_000));
                String memberName = name + "-" + (i + 1);
                String memberNo = "LTV" + System.currentTimeMillis() + rnd.nextInt(10000);

                // 检查是否已存在
                List<Long> exists = jdbc.queryForList(
                    "SELECT id FROM crm_member WHERE phone = ? AND deleted = 0",
                    Long.class, phone);
                if (!exists.isEmpty()) continue;

                // 入会时间
                int joinDaysAgo = 30 + rnd.nextInt(365);
                LocalDateTime joinTime = LocalDateTime.now().minusDays(joinDaysAgo);

                try {
                    jdbc.update(
                        "INSERT INTO crm_member(tenant_id, member_no, name, phone, gender, status, deleted, create_time, update_time, join_time) " +
                        "VALUES(?,?,?,?,?,1,0,NOW(),NOW(),?)",
                        tenantId, memberNo, memberName, phone, rnd.nextBoolean() ? 1 : 2, joinTime);
                } catch (Exception e) {
                    log.warn("[LTV-seed] member insert fail: {}", e.getMessage());
                    continue;
                }

                Long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                if (memberId == null) continue;
                totalMembers++;

                // 生成订单
                int orderCount = minOrders + rnd.nextInt(maxOrders - minOrders + 1);
                for (int k = 0; k < orderCount; k++) {
                    int daysAgo = recencyRange[0] + rnd.nextInt(recencyRange[1] - recencyRange[0] + 1);
                    daysAgo += k * (365 / Math.max(orderCount, 1)); // 分散到不同时间
                    if (daysAgo > joinDaysAgo) daysAgo = joinDaysAgo;

                    LocalDateTime payTime = LocalDateTime.now().minusDays(daysAgo)
                            .minusHours(rnd.nextInt(12));
                    BigDecimal payAmount = BigDecimal.valueOf(
                            minAmt + rnd.nextInt(maxAmt - minAmt + 1));
                    // 大部分已完成(status=2)，少量退款(status=3)
                    int status = rnd.nextInt(10) < 9 ? 2 : 3;
                    String orderNo = "LO" + System.currentTimeMillis() + k + memberId;

                    try {
                        jdbc.update(
                            "INSERT INTO pos_order(tenant_id, store_id, order_no, member_id, member_name, member_phone, " +
                            "cashier_id, cashier_name, " +
                            "type, total_amount, final_amount, paid_amount, status, pay_time, deleted, create_time, update_time) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,NOW(),NOW())",
                            tenantId, 1L, orderNo, memberId, memberName, phone,
                            1L, "system",
                            1, payAmount, payAmount, payAmount, status, payTime);
                        totalOrders++;
                    } catch (Exception e) {
                        if (totalOrders < 3) {
                            log.warn("[LTV-seed] order insert fail mid={}: {}", memberId, e.getMessage());
                        }
                    }
                }
            }
        }

        return new int[]{totalMembers, totalOrders};
    }

    // ================================================================
    // LTV 快照生成
    // ================================================================

    private int generateLtvSnapshots(Long tenantId, String operator) {
        // 获取所有有订单的会员
        List<Map<String, Object>> members = jdbc.queryForList(
            "SELECT DISTINCT m.id AS member_id, " +
            "  COUNT(o.id) AS total_orders, " +
            "  COALESCE(SUM(CASE WHEN o.status = 2 THEN o.paid_amount ELSE 0 END), 0) AS historical_ltv, " +
            "  COALESCE(AVG(CASE WHEN o.status = 2 THEN o.paid_amount ELSE NULL END), 0) AS aov, " +
            "  COALESCE(DATEDIFF(NOW(), MAX(CASE WHEN o.status = 2 THEN o.pay_time END)), 9999) AS last_purchase_days, " +
            "  COALESCE(DATEDIFF(NOW(), MIN(o.pay_time)), 0) AS tenure_days " +
            "FROM crm_member m " +
            "LEFT JOIN pos_order o ON o.member_id = m.id AND o.deleted = 0 " +
            "WHERE m.tenant_id = ? AND m.deleted = 0 " +
            "GROUP BY m.id HAVING total_orders > 0",
            tenantId);

        if (members.isEmpty()) {
            log.info("[LTV-seed] no members with orders found");
            return 0;
        }

        Random rnd = new Random(42);
        int inserted = 0;

        // 生成最近 12 个月的快照（用于趋势图）
        LocalDate today = LocalDate.now();
        for (int monthOffset = 11; monthOffset >= 0; monthOffset--) {
            LocalDate snapshotDate = today.minusMonths(monthOffset);
            final LocalDate snapDate = snapshotDate;

            for (Map<String, Object> m : members) {
                Long memberId = ((Number) m.get("member_id")).longValue();
                int totalOrders = ((Number) m.get("total_orders")).intValue();
                BigDecimal historicalLtv = new BigDecimal(m.get("historical_ltv").toString());
                BigDecimal aov = new BigDecimal(m.get("aov").toString());
                int lastPurchaseDays = ((Number) m.get("last_purchase_days")).intValue();
                int tenureDays = ((Number) m.get("tenure_days")).intValue();

                // 简化计算：基于历史数据 + 时间衰减系数
                double months = Math.max(tenureDays / 30.0, 1.0);
                double monthlyFreq = totalOrders / months;
                // 随着月份推移，历史 LTV 会减少（模拟更早的历史）
                double histFactor = 1.0 - (monthOffset * 0.02); // 每月减少 2%
                BigDecimal adjustedHistoricalLtv = historicalLtv.multiply(BigDecimal.valueOf(Math.max(histFactor, 0.5)));

                // 复购概率
                double prob30 = computeRepurchaseProb(monthlyFreq, lastPurchaseDays + monthOffset * 30, 30);
                double prob90 = computeRepurchaseProb(monthlyFreq, lastPurchaseDays + monthOffset * 30, 90);
                double prob180 = computeRepurchaseProb(monthlyFreq, lastPurchaseDays + monthOffset * 30, 180);

                // LTV 预测
                BigDecimal ltv30 = aov.multiply(BigDecimal.valueOf(monthlyFreq))
                        .multiply(BigDecimal.valueOf(prob30))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal ltv90 = aov.multiply(BigDecimal.valueOf(monthlyFreq))
                        .multiply(BigDecimal.valueOf(3))
                        .multiply(BigDecimal.valueOf(prob90))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal ltv365 = aov.multiply(BigDecimal.valueOf(monthlyFreq))
                        .multiply(BigDecimal.valueOf(12))
                        .multiply(BigDecimal.valueOf(prob180))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal ltvLifetime = ltv365.multiply(BigDecimal.valueOf(3));

                try {
                    jdbc.update(
                        "INSERT INTO crm_ltv_snapshot(" +
                        "  tenant_id, member_id, historical_ltv, predicted_ltv_30d, " +
                        "  predicted_ltv_90d, predicted_ltv_365d, predicted_ltv_lifetime, " +
                        "  repurchase_prob_30d, repurchase_prob_90d, repurchase_prob_180d, " +
                        "  model_type, model_version, avg_order_value, purchase_frequency, " +
                        "  last_purchase_days, total_orders, snapshot_date, operator_name, create_time) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())",
                        tenantId, memberId, adjustedHistoricalLtv, ltv30, ltv90, ltv365, ltvLifetime,
                        BigDecimal.valueOf(prob30).setScale(4, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(prob90).setScale(4, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(prob180).setScale(4, RoundingMode.HALF_UP),
                        "BG", "DEMO-V1",
                        aov.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(monthlyFreq).setScale(2, RoundingMode.HALF_UP),
                        lastPurchaseDays + monthOffset * 30, totalOrders, snapDate, operator);
                    inserted++;
                } catch (Exception e) {
                    log.warn("[LTV-seed] snapshot insert fail mid={}: {}", memberId, e.getMessage());
                }
            }
        }

        return inserted;
    }

    private double computeRepurchaseProb(double monthlyFreq, int lastPurchaseDays, int horizonDays) {
        if (lastPurchaseDays >= 9999) return 0.05;
        double decay = Math.exp(-lastPurchaseDays / 60.0);
        double freqFactor = Math.log1p(monthlyFreq) / Math.log(10);
        double baseProb = Math.min(1.0, 0.1 + 0.3 * freqFactor + 0.5 * decay);
        double horizonProb = 1 - Math.pow(1 - baseProb, horizonDays / 30.0);
        return Math.max(0.01, Math.min(0.99, horizonProb));
    }

    private static int[] parseIntPair(String s) {
        if (s == null) return new int[]{0, 0};
        String[] parts = s.split(",");
        int a = Integer.parseInt(parts[0].trim());
        int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : a;
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }
}
