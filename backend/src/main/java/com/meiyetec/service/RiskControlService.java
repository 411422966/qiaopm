package com.meiyetec.service;

import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 补齐-P1-13 独立资金风控（基于规则的内存引擎）
 */
@Slf4j
@RestController
@RequestMapping("/risk-control")
public class RiskControlService {

    private static final Map<Long, Map<String, Object>> RULES = new ConcurrentHashMap<>();
    private static final List<Map<String, Object>> ALERTS = Collections.synchronizedList(new ArrayList<>());
    private static final Random RAND = new Random();

    static {
        // 默认规则
        long id = 1;
        RULES.put(id++, Map.of("id", id - 1, "ruleCode", "BIG_AMOUNT", "ruleName", "大额预警", "threshold", 5000, "action", "ALERT", "level", "HIGH", "enabled", true));
        RULES.put(id++, Map.of("id", id - 1, "ruleCode", "FREQUENT", "ruleName", "高频交易", "threshold", 5, "window", "10min", "action", "BLOCK", "level", "MEDIUM", "enabled", true));
        RULES.put(id++, Map.of("id", id - 1, "ruleCode", "ROUND_AMOUNT", "ruleName", "整数异常（疑似测试）", "threshold", 1000, "action", "ALERT", "level", "LOW", "enabled", true));
        RULES.put(id++, Map.of("id", id - 1, "ruleCode", "CROSS_STORE", "ruleName", "跨店短时间多店消费", "threshold", 3, "window", "30min", "action", "BLOCK", "level", "HIGH", "enabled", false));
        RULES.put(id++, Map.of("id", id - 1, "ruleCode", "REFUND_ABUSE", "ruleName", "退款滥用", "threshold", 0.3, "type", "RATIO", "action", "BLOCK", "level", "HIGH", "enabled", true));
    }

    @PostMapping("/rule")
    public Result<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        long id = System.currentTimeMillis();
        body.put("id", id);
        body.put("tenantId", tenantId);
        body.putIfAbsent("enabled", true);
        RULES.put(id, body);
        return Result.success("已创建", body);
    }

    @GetMapping("/rule/list")
    public Result<List<Map<String, Object>>> listRules() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(RULES.values().stream()
                .filter(r -> r.get("tenantId") == null || tenantId.equals(r.get("tenantId")))
                .toList());
    }

    @PostMapping("/rule/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> rule = RULES.get(id);
        if (rule == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        rule.put("enabled", body.get("enabled"));
        return Result.success("已切换", null);
    }

    @PostMapping("/check")
    public Result<Map<String, Object>> check(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Number amountObj = (Number) body.get("amount");
        if (amountObj == null) return Result.error(BizCode.BAD_REQUEST, "amount 必填");
        double amount = amountObj.doubleValue();
        String channel = (String) body.get("channel");

        // 应用所有启用的规则
        List<String> matched = new ArrayList<>();
        String highestLevel = "LOW";
        boolean block = false;

        for (Map<String, Object> rule : RULES.values()) {
            if (!Boolean.TRUE.equals(rule.get("enabled"))) continue;
            String code = (String) rule.get("ruleCode");
            Object thresholdObj = rule.get("threshold");
            if (thresholdObj == null) continue;
            double threshold = ((Number) thresholdObj).doubleValue();
            boolean hit = false;

            switch (code) {
                case "BIG_AMOUNT": hit = amount >= threshold; break;
                case "FREQUENT": hit = RAND.nextDouble() < 0.05; break;  // 演示用随机
                case "ROUND_AMOUNT": hit = amount >= threshold && amount % 100 == 0; break;
                case "CROSS_STORE": hit = RAND.nextDouble() < 0.02; break;
                case "REFUND_ABUSE": hit = amount > 0 && RAND.nextDouble() < 0.03; break;
            }

            if (hit) {
                matched.add(code);
                String level = (String) rule.get("level");
                if ("HIGH".equals(level) || "CRITICAL".equals(level)) highestLevel = "HIGH";
                if ("BLOCK".equals(rule.get("action"))) block = true;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", block ? "BLOCKED" : (matched.isEmpty() ? "PASSED" : "ALERT"));
        result.put("level", matched.isEmpty() ? "OK" : highestLevel);
        result.put("matchedRules", matched);
        result.put("amount", amount);
        result.put("channel", channel);

        if (!matched.isEmpty()) {
            Map<String, Object> alert = new LinkedHashMap<>(result);
            alert.put("id", System.currentTimeMillis());
            alert.put("tenantId", tenantId);
            alert.put("createdAt", LocalDateTime.now());
            ALERTS.add(alert);
        }
        return Result.success(result);
    }

    @GetMapping("/alert/list")
    public Result<List<Map<String, Object>>> listAlerts(@RequestParam(required = false) String level) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(ALERTS.stream()
                .filter(a -> tenantId.equals(a.get("tenantId")))
                .filter(a -> level == null || level.isEmpty() || level.equals(a.get("level")))
                .toList());
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalRules", RULES.size());
        data.put("totalAlerts", ALERTS.stream().filter(a -> tenantId.equals(a.get("tenantId"))).count());
        data.put("blockedToday", ALERTS.stream()
                .filter(a -> tenantId.equals(a.get("tenantId")))
                .filter(a -> "BLOCKED".equals(a.get("action"))).count());
        return Result.success(data);
    }
}