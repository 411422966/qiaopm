package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.IotDeviceData;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.IotDeviceDataService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/iot/data")
@RequiredArgsConstructor
@SysOpLog(module = "IoT Data")
public class IotDeviceDataController {

    private final IotDeviceDataService dataService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    @GetMapping("/list")
    @RequirePermission("iot:data:list")
    public Result<List<IotDeviceData>> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(dataService.queryList(tid(), deviceId, memberId, dataType, startTime, endTime));
    }

    @GetMapping("/member/{memberId}/recent")
    @RequirePermission("iot:data:list")
    public Result<List<IotDeviceData>> recentByMember(@PathVariable Long memberId) {
        return Result.success(dataService.recentByMember(tid(), memberId));
    }

    @GetMapping("/member/{memberId}/trend")
    @RequirePermission("iot:data:list")
    public Result<List<Map<String, Object>>> trendByMember(
            @PathVariable Long memberId,
            @RequestParam String dataType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(dataService.trendByMember(tid(), memberId, dataType, startTime, endTime));
    }

    @PostMapping
    @RequirePermission("iot:data:edit")
    public Result<IotDeviceData> create(@RequestBody IotDeviceData data) {
        data.setTenantId(tid());
        if (data.getStoreId() == null) data.setStoreId(1L);
        dataService.save(data);
        return Result.success("已记录", data);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("iot:data:edit")
    public Result<Void> delete(@PathVariable Long id) {
        IotDeviceData exist = dataService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "数据不存在");
        dataService.removeById(id);
        return Result.success("已删除", null);
    }
}