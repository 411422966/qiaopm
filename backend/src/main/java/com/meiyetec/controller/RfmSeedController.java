package com.meiyetec.controller;

import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.RfmBatchExecutor;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RFM 示例数据生成器（前端"生成示例数据"按钮使用）。
 *
 * 设计：
 *   1. 同步部分：仅 INSERT 80 个示例会员 + ~250 笔历史订单（毫秒~秒级，可接受）
 *   2. 异步部分：把"对 N 位会员跑 RFM 重算"扔到 RfmBatchExecutor，taskId 返回给前端
 *      前端轮询 /intel/rfm/batch/progress/{taskId} 看进度
 *
 * 三种模式：
 *   - soft：仅重算已有会员（不造新数据）→ 直接走 /seed/run 异步
 *   - full：造 80 个模拟会员 + 250 笔历史订单 + 异步触发 RFM 重算
 *   - reset：先删旧快照，再按 full
 */
@Slf4j
@RestController
@RequestMapping("/intel/rfm/seed")
@RequiredArgsConstructor
public class RfmSeedController {

    private final JdbcTemplate jdbc;
    private final RfmBatchExecutor executor;

    @PostMapping
    public Result<Map<String, Object>> seed(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.BAD_REQUEST, "未识别租户");

        String mode = body == null || body.get("mode") == null
                ? "soft" : body.get("mode").toString().toLowerCase();
        Integer limit = body == null || body.get("limit") == null ? null
                : ((Number) body.get("limit")).intValue();
        String operator = body == null ? null : (String) body.get("operatorName");
        if (limit == null || limit <= 0) limit = 5000;

        Map<String, Object> data = new HashMap<>();

        if ("full".equals(mode) || "reset".equals(mode)) {
            if ("reset".equals(mode)) {
                jdbc.update("DELETE FROM crm_rfm_snapshot WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM crm_member_tag_value WHERE tenant_id = ?", tenantId);
                data.put("clearedOldSnapshots", true);
            }
            int[] counts = generateDemoData(tenantId);
            data.put("membersInserted", counts[0]);
            data.put("ordersInserted", counts[1]);
        }

        // 异步 RFM 重算（soft / full / reset 共用）
        long taskId = executor.createTask(tenantId, "SEED", null, operator);
        executor.runSeed(taskId, tenantId, limit, operator);
        data.put("taskId", taskId);
        data.put("action", "SEED");
        data.put("asyncProgressUrl", "/intel/rfm/batch/progress/" + taskId);

        // 标记本次 seed 生成的快照为 SEED 模式，便于后续 RfmService.clearSeed 精准清理
        int marked = jdbc.update(
            "UPDATE crm_rfm_snapshot SET calc_mode = 'SEED' " +
            "WHERE tenant_id = ? AND snapshot_date = CURDATE()", tenantId);
        data.put("seedMarked", marked);

        return Result.success("示例数据生成中", data);
    }

    // -----------------------------------------------------------
    // 真实生成示例数据（同步 INSERT 部分）
    // -----------------------------------------------------------
    private int[] generateDemoData(Long tenantId) {
        DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String[][] rfmArch = {
            {"CHAMPION", "30,15,80", "12,18", "30000,50000,20000"},
            {"LOYAL",    "60,30,120", "8,12", "15000,25000,8000"},
            {"POTENTIAL","10,7,20",   "3,5",   "15000,30000,8000"},
            {"NEW",      "5,3,10",    "1,2",   "500,1500,200"},
            {"LOST",     "120,80,200","1,2",   "15000,25000,8000"},
            {"AT_RISK",  "60,45,90",  "3,5",   "6000,10000,3000"},
            {"CANT_LOSE","100,60,200","8,14",  "20000,35000,12000"},
            {"HIBERNATING","100,60,180","2,3", "3000,6000,1000"}
        };
        int membersInserted = 0;
        int ordersInserted = 0;
        Random rnd = new Random(42);
        long now = System.currentTimeMillis();

        for (String[] arch : rfmArch) {
            String archCode = arch[0];
            int[] recencyRange = parseIntPair(arch[1]);
            int[] freqRange    = parseIntPair(arch[2]);
            int[] monetaryRange= parseIntPair(arch[3]);

            for (int i = 0; i < 10; i++) {
                String phone = "139" + String.format("%08d", rnd.nextInt(100000000));
                String name  = archCode.toLowerCase().charAt(0) + i + "号";
                List<Long> exists = jdbc.queryForList(
                    "SELECT id FROM crm_member WHERE phone = ? AND deleted = 0",
                    Long.class, phone);
                if (!exists.isEmpty()) continue;

                Long mid;
                try {
                    String memberNo = "M" + phone;
                    jdbc.update(
                        "INSERT INTO crm_member(tenant_id, member_no, name, phone, gender, status, deleted, create_time, update_time) " +
                        "VALUES(?,?,?,?,?,1,0,NOW(),NOW())",
                        tenantId, memberNo, name, phone, rnd.nextBoolean() ? 1 : 2);
                    mid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                } catch (Exception e) {
                    log.warn("[RFM-seed] member insert fail, name={}, err={}", name, e.getMessage());
                    continue;
                }
                if (mid == null) continue;
                membersInserted++;

                int freq = rnd.nextInt(freqRange[1] - freqRange[0] + 1) + freqRange[0];
                int recentR = rnd.nextInt(recencyRange[1] - recencyRange[0] + 1) + recencyRange[0];
                for (int k = 0; k < freq; k++) {
                    int daysAgo = recentR + k * 12 + rnd.nextInt(20);
                    if (daysAgo > 365) daysAgo = 365;
                    LocalDateTime payTime = LocalDateTime.now().minusDays(daysAgo).minusHours(rnd.nextInt(8));
                    BigDecimal amount = BigDecimal.valueOf(rnd.nextInt(monetaryRange[1] - monetaryRange[0]) + monetaryRange[0]);
                    int status = (rnd.nextInt(10) < 9) ? 2 : 3;
                    String orderNo = "OF" + now + k + mid;
                    try {
                        jdbc.update(
                            "INSERT INTO pos_order(tenant_id, store_id, order_no, member_id, member_name, member_phone, " +
                            "cashier_id, cashier_name, " +
                            "type, total_amount, final_amount, paid_amount, status, pay_time, deleted, create_time, update_time) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,NOW(),NOW())",
                            tenantId, 1L, orderNo, mid, name, phone,
                            1L, "system",
                            1, amount, amount, amount, status, payTime);
                        ordersInserted++;
                    } catch (Exception e) {
                        if (ordersInserted < 3) log.warn("[RFM-seed] order insert fail mid={}, err={}", mid, e.getMessage());
                    }
                }
            }
        }
        return new int[]{membersInserted, ordersInserted};
    }

    private static int[] parseIntPair(String s) {
        if (s == null) return new int[]{0, 0};
        String[] parts = s.split(",");
        int a = Integer.parseInt(parts[0].trim());
        int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : a;
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }
}
