package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.AttendanceConfig;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.DataScopeService;
import com.meiyetec.security.StoreScope;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.AttendanceConfigService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/attendance/config")
@RequiredArgsConstructor
@SysOpLog(module = "AttendanceConfig")
public class AttendanceConfigController {

    private final AttendanceConfigService attendanceConfigService;
    private final DataScopeService dataScopeService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/active")
    @RequirePermission("attendance:config:list")
    public Result<AttendanceConfig> getActiveConfig(@RequestParam(required = false) Long storeId) {
        // I186 第 1 批：缺省 storeId 时按有效门店集合取（门店行优先、全局行兜底）
        AttendanceConfig config = attendanceConfigService.getActiveConfigForScope(tid(), storeId);
        return Result.success(config);
    }

    @GetMapping("/list")
    @RequirePermission("attendance:config:list")
    public Result<List<AttendanceConfig>> list(@RequestParam(required = false) Long storeId) {
        Long t = tid();
        LambdaQueryWrapper<AttendanceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceConfig::getTenantId, t);
        // I186 专项 1：OR 分支必须收进 and(...) 嵌套条件（MyBatis-Plus 嵌套条件天然带括号），
        // 否则原 SQL 语义为 (tenant_id=? AND store_id=?) OR (store_id IS NULL)
        // → 第二个分支没有 tenant_id 约束，会把他租户的全局配置行返回本租户。
        // 缺省 storeId 时按当前会话的有效门店集合过滤（不再是租户内全量）。
        StoreScope scope = dataScopeService.resolveEffectiveStoreScope(storeId);
        if (!scope.isAll()) {
            wrapper.and(w -> {
                if (scope.isEmptyScope()) {
                    w.eq(AttendanceConfig::getStoreId, StoreScope.EMPTY_SENTINEL);
                } else {
                    w.in(AttendanceConfig::getStoreId, scope.getStoreIds()).or().isNull(AttendanceConfig::getStoreId);
                }
            });
        }
        wrapper.orderByDesc(AttendanceConfig::getCreateTime);
        return Result.success(attendanceConfigService.list(wrapper));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("attendance:config:list")
    public Result<AttendanceConfig> getById(@PathVariable Long id) {
        AttendanceConfig c = attendanceConfigService.getById(id);
        if (c == null || !tid().equals(c.getTenantId())) return Result.error(BizCode.NOT_FOUND, "配置不存在");
        return Result.success(c);
    }

    @PostMapping
    @RequirePermission("attendance:config:edit")
    public Result<AttendanceConfig> create(@RequestBody AttendanceConfig config) {
        config.setId(null);
        config.setTenantId(tid());
        attendanceConfigService.save(config);
        return Result.success("创建成功", config);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("attendance:config:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody AttendanceConfig config) {
        AttendanceConfig exist = attendanceConfigService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "配置不存在");
        config.setId(id);
        config.setTenantId(tid());
        attendanceConfigService.updateById(config);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("attendance:config:edit")
    public Result<Void> delete(@PathVariable Long id) {
        AttendanceConfig exist = attendanceConfigService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "配置不存在");
        attendanceConfigService.removeById(id);
        return Result.success("删除成功", null);
    }
}