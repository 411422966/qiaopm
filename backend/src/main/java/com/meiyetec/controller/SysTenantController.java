package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.dto.TenantStatsDTO;
import com.meiyetec.entity.SysTenant;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.SysTenantConfigService;
import com.meiyetec.service.SysTenantService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/sys/tenant")
@RequiredArgsConstructor
@SysOpLog(module = "租户")
public class SysTenantController {

    private final SysTenantService service;
    private final SysTenantConfigService configService;

    @GetMapping("/page")
    @RequirePermission("sys:tenant:list")
    public Result<Page<SysTenant>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        QueryWrapper<SysTenant> w = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            w.like("tenant_name", keyword).or().like("tenant_code", keyword);
        }
        w.orderByDesc("created_at");
        return Result.success(service.page(new Page<>(pageNum, pageSize), w));
    }

    // /list must come BEFORE /{id} to avoid "list" being captured as id
    @GetMapping("/list")
    @RequirePermission("sys:tenant:list")
    public Result<?> list() {
        // alias for /page
        return page(1, 100, null);
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:tenant:list")
    public Result<SysTenant> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @PostMapping
    @RequirePermission("sys:tenant:add")
    public Result<Void> create(@RequestBody SysTenant t) {
        service.save(t);
        return Result.success("创建成功", null);
    }

    @PutMapping
    @RequirePermission("sys:tenant:edit")
    public Result<Void> update(@RequestBody SysTenant t) {
        service.updateById(t);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("sys:tenant:delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.removeById(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/status/{status}")
    @RequirePermission("sys:tenant:freeze")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        SysTenant t = service.getById(id);
        if (t == null) {
            return Result.error("租户不存在");
        }
        // 启用时校验是否已过期
        if (status == 1 && t.getExpireAt() != null && !t.getExpireAt().isEmpty()) {
            try {
                LocalDateTime expire = LocalDateTime.parse(t.getExpireAt(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (expire.isBefore(LocalDateTime.now())) {
                    return Result.error("租户已过期，无法启用，请先延长期限");
                }
            } catch (Exception ignored) {
                // 时间格式异常时不阻塞
            }
        }
        t.setStatus(status);
        service.updateById(t);
        return Result.success("状态更新成功", null);
    }

    @GetMapping("/{id}/stats")
    @RequirePermission("sys:tenant:list")
    public Result<TenantStatsDTO> stats(@PathVariable Long id) {
        TenantStatsDTO dto = service.getStats(id);
        if (dto == null) return Result.error("租户不存在");
        return Result.success(dto);
    }

    @GetMapping("/{id}/config")
    @RequirePermission("sys:tenant:list")
    public Result<Map<String, String>> getConfig(@PathVariable Long id) {
        return Result.success(configService.getConfig(id));
    }

    @PutMapping("/{id}/config")
    @RequirePermission("sys:tenant:edit")
    public Result<Void> saveConfig(@PathVariable Long id, @RequestBody Map<String, String> configs) {
        configService.saveConfig(id, configs);
        return Result.success("配置保存成功", null);
    }
}


