package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.JourneyTriggerLog;
import com.meiyetec.entity.JourneyTriggerRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.JourneyTriggerService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * M16 营销旅程事件触发器 REST API
 */
@RestController
@RequestMapping("/mkt/journey/trigger")
@RequiredArgsConstructor
@SysOpLog(module = "JourneyTrigger")
public class JourneyTriggerController {

    private final JourneyTriggerService triggerService;

    @GetMapping("/page")
    @RequirePermission("mkt:journey:trigger:list")
    public Result<Page<JourneyTriggerRule>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        return triggerService.page(pageNum, pageSize, eventType, status);
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("mkt:journey:trigger:list")
    public Result<JourneyTriggerRule> getById(@PathVariable Long id) {
        JourneyTriggerRule r = triggerService.getById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "触发器不存在");
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null && r.getTenantId() != null && !r.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问");
        return Result.success(r);
    }

    @PostMapping
    @RequirePermission("mkt:journey:trigger:edit")
    public Result<JourneyTriggerRule> create(@RequestBody JourneyTriggerRule rule) {
        rule.setId(null);
        return triggerService.save(rule);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("mkt:journey:trigger:edit")
    public Result<JourneyTriggerRule> update(@PathVariable Long id, @RequestBody JourneyTriggerRule rule) {
        rule.setId(id);
        return triggerService.save(rule);
    }

    @PostMapping("/{id:\\d+}/toggle")
    @RequirePermission("mkt:journey:trigger:edit")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean enable) {
        return triggerService.toggle(id, enable);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("mkt:journey:trigger:edit")
    public Result<Void> delete(@PathVariable Long id) {
        return triggerService.delete(id);
    }

    @GetMapping("/{id:\\d+}/logs")
    @RequirePermission("mkt:journey:trigger:list")
    public Result<List<JourneyTriggerLog>> logs(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return triggerService.logs(id, status, limit);
    }

    @PostMapping("/{id:\\d+}/run")
    @RequirePermission("mkt:journey:trigger:edit")
    public Result<Map<String, Object>> triggerRule(@PathVariable Long id) {
        return triggerService.triggerRule(id);
    }

    @GetMapping("/stats")
    @RequirePermission("mkt:journey:trigger:list")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return triggerService.stats(from, to);
    }
}