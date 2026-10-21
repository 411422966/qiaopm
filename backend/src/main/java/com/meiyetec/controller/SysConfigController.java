package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meiyetec.entity.SysConfig;
import com.meiyetec.service.SysConfigService;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/config")
@RequiredArgsConstructor
@SysOpLog(module = "系统")
public class SysConfigController {

    private final SysConfigService service;

    @GetMapping("/list")
    @RequirePermission("sys:config:list")
    public Result<List<SysConfig>> list(@RequestParam(required = false) String group) {
        QueryWrapper<SysConfig> w = new QueryWrapper<>();
        if (group != null && !group.isEmpty()) w.eq("config_group", group);
        w.orderByAsc("sort_order");
        return Result.success(service.list(w));
    }

    @GetMapping("/grouped")
    @RequirePermission("sys:config:list")
    public Result<Map<String, List<SysConfig>>> grouped() {
        List<SysConfig> all = service.list(new QueryWrapper<SysConfig>().orderByAsc("sort_order"));
        return Result.success(all.stream().collect(java.util.stream.Collectors.groupingBy(
            c -> c.getConfigGroup() == null ? "default" : c.getConfigGroup())));
    }

    @GetMapping("/key/{key}")
    @RequirePermission("sys:config:list")
    public Result<SysConfig> getByKey(@PathVariable String key) {
        return Result.success(service.getOne(new QueryWrapper<SysConfig>().eq("config_key", key)));
    }

    @PostMapping
    @RequirePermission("sys:config:edit")
    public Result<Void> create(@RequestBody SysConfig c) {
        service.save(c);
        return Result.success("创建成功", null);
    }

    @PutMapping
    @RequirePermission("sys:config:edit")
    public Result<Void> update(@RequestBody SysConfig c) {
        service.updateById(c);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:config:edit")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/batch")
    @RequirePermission("sys:config:edit")
    public Result<Void> batchUpdate(@RequestBody List<SysConfig> list) {
        for (SysConfig c : list) {
            if (c.getId() != null) service.updateById(c);
        }
        return Result.success("批量更新成功", null);
    }
}


