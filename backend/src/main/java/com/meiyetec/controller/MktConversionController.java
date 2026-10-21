package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MarketingAutomationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 营销转化效果 controller（前端 /mkt/conversion/* 实际对应的端点）
 *  - conversion.vue 实际使用的后端
 *  - 对应 marketing_touch_conversion 表 + touch_log 聚合
 *
 * 路由清单（与前端 conversion.vue 完全对齐）：
 *  GET    /mkt/conversion/funnel       （漏斗：触达→发送→已读→转化）
 *  GET    /mkt/conversion/roi          （ROI 统计）
 *  GET    /mkt/conversion/by-channel   （按渠道统计）
 *  POST   /mkt/conversion/track        （记录转化 — 来自业务回调）
 *  POST   /mkt/conversion/mark-read    （标记已读 — 来自渠道回调）
 */
@RestController
@RequestMapping("/mkt/conversion")
@RequiredArgsConstructor
@SysOpLog(module = "营销转化")
public class MktConversionController {

    private final MarketingAutomationService marketingAutomationService;

    @GetMapping("/funnel")
    @RequirePermission("mkt:conversion:view")
    public Result<Map<String, Object>> funnel(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return marketingAutomationService.conversionFunnel(startDate, endDate);
    }

    @GetMapping("/roi")
    @RequirePermission("mkt:conversion:view")
    public Result<Map<String, Object>> roi(@RequestParam(required = false) Long taskId) {
        return marketingAutomationService.conversionRoi(taskId);
    }

    @GetMapping("/by-channel")
    @RequirePermission("mkt:conversion:view")
    public Result<List<Map<String, Object>>> byChannel() {
        return marketingAutomationService.byChannel();
    }

    @PostMapping("/track")
    @RequirePermission("mkt:conversion:track")
    public Result<Void> track(@RequestBody Map<String, Object> body) {
        return marketingAutomationService.trackConversion(body);
    }

    /**
     * 标记已读 — 注意：前端使用 `id` 作为触达 log id（非 task id）
     */
    @PostMapping("/mark-read")
    @RequirePermission("mkt:conversion:track")
    public Result<Void> markRead(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : ((Number) body.get("id")).longValue();
        if (id == null) {
            // 兼容旧 path-variable 风格
            id = body.get("touchLogId") == null ? null : ((Number) body.get("touchLogId")).longValue();
        }
        if (id == null) return com.meiyetec.util.Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "id 必填");
        return marketingAutomationService.markRead(id);
    }
}