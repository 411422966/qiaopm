package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.IotDeviceAlert;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.IotDeviceAlertService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/iot/alert")
@RequiredArgsConstructor
@SysOpLog(module = "IoT Alert")
public class IotDeviceAlertController {

    private final IotDeviceAlertService alertService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    @GetMapping("/list")
    public Result<List<IotDeviceAlert>> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId) {
        return Result.success(alertService.queryList(tid(), deviceId, alertLevel, status, storeId));
    }

    @GetMapping("/{id:\\d+}")
    public Result<IotDeviceAlert> getById(@PathVariable Long id) {
        IotDeviceAlert a = alertService.getById(id);
        if (a == null || !tid().equals(a.getTenantId())) return Result.error(BizCode.NOT_FOUND, "告警不存在");
        return Result.success(a);
    }

    @PostMapping
    public Result<IotDeviceAlert> create(@RequestBody IotDeviceAlert alert) {
        alert.setTenantId(tid());
        if (alert.getStoreId() == null) alert.setStoreId(1L);
        alertService.save(alert);
        return Result.success("已创建", alert);
    }

    @PutMapping("/{id:\\d+}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody IotDeviceAlert params) {
        IotDeviceAlert alert = alertService.getById(id);
        if (alert == null || !tid().equals(alert.getTenantId())) return Result.error(BizCode.NOT_FOUND, "告警不存在");
        alert.setStatus(params.getStatus());
        alert.setHandlerId(params.getHandlerId());
        alert.setHandlerName(params.getHandlerName());
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleNote(params.getHandleNote());
        alertService.updateById(alert);
        return Result.success("处理成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        IotDeviceAlert exist = alertService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "告警不存在");
        alertService.removeById(id);
        return Result.success("已删除", null);
    }
}