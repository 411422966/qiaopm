package com.meiyetec.controller;

import com.meiyetec.entity.MemberLifecycleLog;
import com.meiyetec.entity.MemberLifecycleRule;
import com.meiyetec.service.MemberLifecycleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * P4-02 · QPM-006: 会员生命周期营销 Controller
 *
 * 端点（/lifecycle 前缀）：
 * - GET  /dashboard            总览看板
 * - GET  /funnel               转化漏斗
 * - GET  /rule/page            规则分页
 * - POST /rule                 创建规则
 * - PUT  /rule/{id}            更新规则
 * - POST /rule/{id}/toggle     启用/禁用
 * - DELETE /rule/{id}          删除规则
 * - POST /rule/init-default    初始化默认规则
 * - GET  /log/page             触发日志
 * - POST /trigger/{ruleId}     手动触发规则
 */
@RestController
@RequestMapping("/lifecycle")
@RequiredArgsConstructor
public class MemberLifecycleController {

    private final MemberLifecycleService service;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.dashboard(startDate, endDate);
    }

    @GetMapping("/funnel")
    public Result<Map<String, Object>> funnel(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.funnel(ruleType, startDate, endDate);
    }

    @GetMapping("/rule/page")
    public Result<?> rulePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String status) {
        return service.rulePage(pageNum, pageSize, ruleType, status);
    }

    @PostMapping("/rule")
    public Result<MemberLifecycleRule> saveRule(@RequestBody MemberLifecycleRule rule) {
        return service.saveRule(rule);
    }

    @PutMapping("/rule/{id}")
    public Result<MemberLifecycleRule> updateRule(@PathVariable Long id, @RequestBody MemberLifecycleRule rule) {
        rule.setId(id);
        return service.saveRule(rule);
    }

    @PostMapping("/rule/{id}/toggle")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestParam Boolean enable) {
        return service.toggleRule(id, Boolean.TRUE.equals(enable));
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        return service.deleteRule(id);
    }

    @PostMapping("/rule/init-default")
    public Result<Integer> initDefault() {
        return service.initDefaultRules();
    }

    @GetMapping("/log/page")
    public Result<?> logPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.logPage(pageNum, pageSize, ruleType, status, startDate, endDate);
    }

    @PostMapping("/trigger/{ruleId}")
    public Result<Map<String, Object>> trigger(@PathVariable Long ruleId) {
        return service.triggerRule(ruleId);
    }
}