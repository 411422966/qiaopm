package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.IotTriggerRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.IotTriggerRuleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/iot/rule")
@RequiredArgsConstructor
@SysOpLog(module = "IoT Trigger Rule")
public class IotTriggerRuleController {

    private final IotTriggerRuleService ruleService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    @GetMapping("/list")
    public Result<List<IotTriggerRule>> list(@RequestParam(required = false) Integer enabled) {
        LambdaQueryWrapper<IotTriggerRule> qw = new LambdaQueryWrapper<>();
        qw.eq(IotTriggerRule::getTenantId, tid());
        if (enabled != null) qw.eq(IotTriggerRule::getEnabled, enabled);
        qw.orderByDesc(IotTriggerRule::getId);
        return Result.success(ruleService.list(qw));
    }
    @GetMapping("/{id:\\d+}")
    public Result<IotTriggerRule> getById(@PathVariable Long id) {
        IotTriggerRule r = ruleService.getById(id);
        if (r == null || !tid().equals(r.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        return Result.success(r);
    }

    @PostMapping
    public Result<IotTriggerRule> create(@RequestBody IotTriggerRule rule) {
        rule.setTenantId(tid());
        if (rule.getStoreId() == null) rule.setStoreId(1L);
        if (rule.getEnabled() == null) rule.setEnabled(1);
        ruleService.save(rule);
        return Result.success("创建成功", rule);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IotTriggerRule rule) {
        IotTriggerRule exist = ruleService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        rule.setId(id);
        rule.setTenantId(tid());
        ruleService.updateById(rule);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        IotTriggerRule exist = ruleService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        ruleService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/active")
    public Result<List<IotTriggerRule>> active(
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String dataType) {
        return Result.success(ruleService.findActive(tid(), deviceType, dataType));
    }
}