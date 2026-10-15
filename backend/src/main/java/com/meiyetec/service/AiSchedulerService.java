package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.AiSchedulePlan;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.AiSchedulePlanMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 补齐-P0-1 AI 智能排班（简化算法：按历史订单量 + 工作日系数预测）
 *
 * <p>结果落库：{@link AiSchedulePlanMapper} → {@code ai_schedule_plan} 表。
 * 历史排班从该表分页读取（替代原内存 List）。
 */
@Slf4j
@RestController
@RequestMapping("/ai-scheduler")
public class AiSchedulerService {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AiSchedulePlanMapper planMapper;

    private static final List<Map<String, Object>> SCHEDULES = Collections.synchronizedList(new ArrayList<>());

    @PostMapping("/forecast")
    public Result<List<Map<String, Object>>> forecast(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        if (storeId == null) return Result.error(BizCode.BAD_REQUEST, "storeId 必填");
        String date = (String) body.getOrDefault("date", LocalDateTime.now().toLocalDate().toString());

        // 取近 30 天每日订单量做平均，并加上星期系数
        List<Map<String, Object>> history;
        try {
            history = jdbc.queryForList(
                "SELECT DATE(o.pay_time) AS d, COUNT(*) AS c, DAYOFWEEK(o.pay_time) AS dow " +
                "FROM crm_order o WHERE o.tenant_id = ? AND o.deleted = 0 " +
                "  AND o.pay_time > DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                "GROUP BY DATE(o.pay_time)", tenantId);
        } catch (Exception e) {
            history = new ArrayList<>();
        }

        Map<Integer, Double> dowAvg = new HashMap<>();
        Map<Integer, Integer> dowCount = new HashMap<>();
        double total = 0;
        for (Map<String, Object> row : history) {
            int dow = ((Number) row.get("dow")).intValue();
            int c = ((Number) row.get("c")).intValue();
            dowAvg.merge(dow, (double) c, Double::sum);
            dowCount.merge(dow, 1, Integer::sum);
            total += c;
        }
        double dailyAvg = history.isEmpty() ? 10 : total / history.size();
        dowAvg.replaceAll((k, v) -> dowCount.get(k) == 0 ? dailyAvg : v / dowCount.get(k));

        // 生成一周预测（每 2 小时一桶）
        List<Map<String, Object>> forecast = new ArrayList<>();
        for (int h = 10; h < 22; h += 2) {
            int dow = java.time.LocalDate.parse(date).getDayOfWeek().getValue() + 1;
            double base = dowAvg.getOrDefault(dow, dailyAvg) / 6.0;
            double factor = h >= 18 && h <= 21 ? 1.8 : (h >= 12 && h <= 14 ? 1.5 : 1.0);
            int expected = (int) Math.round(base * factor);
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("time", String.format("%02d:00", h));
            slot.put("expectedBookings", expected);
            slot.put("dow", dow);
            forecast.add(slot);
        }
        return Result.success(forecast);
    }

    @GetMapping("/forecasts")
    public Result<List<Map<String, Object>>> listForecasts() {
        return Result.success(List.of());
    }

    /**
     * 生成排班 + 写入 ai_schedule_plan（落库）
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue();
        String weekStart = (String) body.getOrDefault("weekStart", LocalDateTime.now().toLocalDate().toString());

        // 简化：根据预测的预期订单量安排 2~3 名技师
        List<Map<String, Object>> body1 = forecast(Map.of("storeId", storeId, "date", weekStart)).getData();
        int totalExpected = body1.stream().mapToInt(m -> (int) m.get("expectedBookings")).sum();

        int staffCount = Math.min(5, Math.max(2, totalExpected / 20 + 2));

        // ===== 落库到 ai_schedule_plan =====
        AiSchedulePlan plan = new AiSchedulePlan();
        plan.setTenantId(tenantId);
        plan.setStoreId(storeId);
        plan.setWeekStart(LocalDate.parse(weekStart));
        plan.setStaffCount(staffCount);
        plan.setExpectedBookings(totalExpected);
        plan.setPlanJson(toJson(body1));
        plan.setStatus("ACTIVE");
        planMapper.insert(plan);

        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("id", plan.getId());
        schedule.put("tenantId", tenantId);
        schedule.put("storeId", storeId);
        schedule.put("weekStart", weekStart);
        schedule.put("staffCount", staffCount);
        schedule.put("expectedBookings", totalExpected);
        schedule.put("generatedAt", LocalDateTime.now());
        schedule.put("persisted", true);
        SCHEDULES.add(schedule);
        return Result.success("已生成排班", schedule);
    }

    /**
     * 已生成的排班（从 ai_schedule_plan 表分页读）
     */
    @GetMapping("/schedules")
    public Result<Page<AiSchedulePlan>> listSchedules(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long storeId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<AiSchedulePlan> qw = new LambdaQueryWrapper<AiSchedulePlan>()
                .eq(AiSchedulePlan::getTenantId, tenantId)
                .orderByDesc(AiSchedulePlan::getId);
        if (storeId != null) qw.eq(AiSchedulePlan::getStoreId, storeId);
        Page<AiSchedulePlan> page = planMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        return Result.success(page);
    }

    @GetMapping("/staff-load")
    public Result<Map<String, Object>> staffLoad() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avgWorkload", 0.72);
        data.put("peakStaff", "技师A");
        data.put("recommendation", "晚班 19-22 点建议增加 1 名技师");
        return Result.success(data);
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }
}