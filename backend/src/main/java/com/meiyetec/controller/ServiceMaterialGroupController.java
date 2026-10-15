package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ServiceMaterialGroup;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ServiceMaterialGroupService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/material/group")
@RequiredArgsConstructor
@SysOpLog(module = "Service Material Group")
public class ServiceMaterialGroupController {

    private final ServiceMaterialGroupService serviceMaterialGroupService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/list")
    public Result<List<ServiceMaterialGroup>> list(@RequestParam(required = false) Long serviceId) {
        Long t = tid();
        LambdaQueryWrapper<ServiceMaterialGroup> w = new LambdaQueryWrapper<>();
        w.eq(ServiceMaterialGroup::getTenantId, t);
        if (serviceId != null) w.eq(ServiceMaterialGroup::getServiceId, serviceId);
        w.orderByDesc(ServiceMaterialGroup::getId);
        return Result.success(serviceMaterialGroupService.list(w));
    }

    @GetMapping("/{id:\\d+}")
    public Result<ServiceMaterialGroup> getById(@PathVariable Long id) {
        Long t = tid();
        ServiceMaterialGroup g = serviceMaterialGroupService.getById(id);
        if (g == null || !t.equals(g.getTenantId())) return Result.error(BizCode.NOT_FOUND, "分组不存在");
        return Result.success(g);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<ServiceMaterialGroup> create(@RequestBody ServiceMaterialGroup group) {
        Long t = tid();
        if (group.getServiceId() == null) return Result.error(BizCode.BAD_REQUEST, "serviceId 必填");
        group.setId(null);
        group.setTenantId(t);
        serviceMaterialGroupService.save(group);
        return Result.success("创建成功", group);
    }

    @PutMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    public Result<ServiceMaterialGroup> update(@PathVariable Long id, @RequestBody ServiceMaterialGroup group) {
        Long t = tid();
        ServiceMaterialGroup old = serviceMaterialGroupService.getById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "分组不存在");
        group.setId(id);
        group.setTenantId(t);
        serviceMaterialGroupService.updateById(group);
        return Result.success("更新成功", group);
    }

    @DeleteMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        Long t = tid();
        ServiceMaterialGroup old = serviceMaterialGroupService.getById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "分组不存在");
        serviceMaterialGroupService.removeById(id);
        return Result.success("删除成功", null);
    }
}
