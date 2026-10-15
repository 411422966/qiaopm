package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ResourceMatchRule;
import com.meiyetec.mapper.ResourceMatchRuleMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resource/match-rule")
@RequiredArgsConstructor
@SysOpLog(module = "资源匹配规则")
public class ResourceMatchRuleController {

    private final ResourceMatchRuleMapper resourceMatchRuleMapper;

    @GetMapping("/list")
    public Result<List<ResourceMatchRule>> list() {
        return Result.success(resourceMatchRuleMapper.selectList(null));
    }

    @GetMapping("/{id:\\d+}")
    public Result<ResourceMatchRule> getById(@PathVariable Long id) {
        return Result.success(resourceMatchRuleMapper.selectById(id));
    }

    @GetMapping("/service/{serviceId}")
    public Result<ResourceMatchRule> getByService(@PathVariable Long serviceId) {
        return Result.success(resourceMatchRuleMapper.findByServiceId(serviceId));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ResourceMatchRule rule) {
        resourceMatchRuleMapper.insert(rule);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ResourceMatchRule rule) {
        rule.setId(id);
        resourceMatchRuleMapper.updateById(rule);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        resourceMatchRuleMapper.deleteById(id);
        return Result.success("删除成功", null);
    }
}
