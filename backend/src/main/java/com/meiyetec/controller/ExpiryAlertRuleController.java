package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ExpiryAlertRule;
import com.meiyetec.service.ExpiryAlertRuleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock/expiry/rule")
@RequiredArgsConstructor
@SysOpLog(module = "ExpiryAlertRule")
public class ExpiryAlertRuleController {

    private final ExpiryAlertRuleService expiryAlertRuleService;

    @GetMapping("/list")
    @RequirePermission("stock:expiry:view")
    public Result<List<ExpiryAlertRule>> list(@RequestParam(required = false) Integer status) {
        return Result.success(expiryAlertRuleService.list(status));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("stock:expiry:view")
    public Result<ExpiryAlertRule> getById(@PathVariable Long id) {
        return Result.success(expiryAlertRuleService.getById(id));
    }

    @PostMapping
    @RequirePermission("stock:expiry:edit")
    public Result<Void> create(@RequestBody ExpiryAlertRule rule) {
        expiryAlertRuleService.save(rule);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("stock:expiry:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody ExpiryAlertRule rule) {
        rule.setId(id);
        expiryAlertRuleService.updateById(rule);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("stock:expiry:edit")
    public Result<Void> delete(@PathVariable Long id) {
        expiryAlertRuleService.removeById(id);
        return Result.success("删除成功", null);
    }
}