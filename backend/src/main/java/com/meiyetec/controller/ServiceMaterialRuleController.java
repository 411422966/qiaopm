package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ServiceMaterialRule;
import com.meiyetec.service.ServiceMaterialRuleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/stock/service-material/rule")
@RequiredArgsConstructor
@SysOpLog(module = "Service Material Rule")
public class ServiceMaterialRuleController {

    private final ServiceMaterialRuleService serviceMaterialRuleService;

    @GetMapping("/list")
    public Result<List<ServiceMaterialRule>> list(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long productId) {
        return Result.success(serviceMaterialRuleService.queryList(serviceId, productId));
    }

    @GetMapping("/service/{serviceId}")
    public Result<List<ServiceMaterialRule>> getByService(@PathVariable Long serviceId) {
        return Result.success(serviceMaterialRuleService.findByServiceId(serviceId));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ServiceMaterialRule rule) {
        serviceMaterialRuleService.save(rule);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ServiceMaterialRule rule) {
        rule.setId(id);
        serviceMaterialRuleService.updateById(rule);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        serviceMaterialRuleService.removeById(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/service/{serviceId}/replace")
    public Result<Void> replaceBindings(@PathVariable Long serviceId, @RequestBody List<ServiceMaterialRule> rules) {
        serviceMaterialRuleService.replaceServiceBindings(serviceId, rules);
        return Result.success("替换成功", null);
    }
}
