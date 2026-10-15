package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CareSuggestionRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionRuleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crm/care/suggestion/rule")
@RequiredArgsConstructor
@SysOpLog(module = "Care Suggestion Rule")
public class CareSuggestionRuleController {

    private final CareSuggestionRuleService careSuggestionRuleService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    public Result<List<CareSuggestionRule>> list() {
        return Result.success(careSuggestionRuleService.getAllWithTemplate());
    }

    @GetMapping("/active")
    public Result<List<CareSuggestionRule>> activeList() {
        return Result.success(careSuggestionRuleService.getActiveRules());
    }

    @GetMapping("/{id:\\d+}")
    public Result<CareSuggestionRule> getById(@PathVariable Long id) {
        CareSuggestionRule r = careSuggestionRuleService.getById(id);
        if (r == null || !tid().equals(r.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        return Result.success(r);
    }

    @PostMapping
    public Result<CareSuggestionRule> create(@RequestBody CareSuggestionRule rule) {
        rule.setId(null);
        rule.setTenantId(tid());
        careSuggestionRuleService.save(rule);
        return Result.success("创建成功", rule);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CareSuggestionRule rule) {
        CareSuggestionRule exist = careSuggestionRuleService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        rule.setId(id);
        rule.setTenantId(tid());
        careSuggestionRuleService.updateById(rule);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        CareSuggestionRule exist = careSuggestionRuleService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        careSuggestionRuleService.removeById(id);
        return Result.success("删除成功", null);
    }
}
