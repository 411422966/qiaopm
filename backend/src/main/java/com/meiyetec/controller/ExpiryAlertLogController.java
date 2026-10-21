package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ExpiryAlertLog;
import com.meiyetec.service.ExpiryAlertLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock/expiry/alert")
@RequiredArgsConstructor
@SysOpLog(module = "ExpiryAlertLog")
public class ExpiryAlertLogController {

    private final ExpiryAlertLogService expiryAlertLogService;

    @GetMapping("/list")
    @RequirePermission("stock:expiry:view")
    public Result<List<ExpiryAlertLog>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) Long storeId) {
        return Result.success(expiryAlertLogService.queryList(status, alertLevel, storeId));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("stock:expiry:view")
    public Result<ExpiryAlertLog> getById(@PathVariable Long id) {
        return Result.success(expiryAlertLogService.getById(id));
    }

    @PutMapping("/{id:\\d+}/handle")
    @RequirePermission("stock:expiry:edit")
    public Result<Void> handle(@PathVariable Long id, @RequestBody Map<String, String> params) {
        expiryAlertLogService.handleAlert(
                id,
                params.get("handleMethod"),
                params.get("handlerName"),
                params.get("handleRemark"));
        return Result.success("处理成功", null);
    }

    @PutMapping("/{id:\\d+}/ignore")
    @RequirePermission("stock:expiry:edit")
    public Result<Void> ignore(@PathVariable Long id) {
        expiryAlertLogService.markIgnored(id);
        return Result.success("已忽略", null);
    }

    @GetMapping("/dashboard")
    @RequirePermission("stock:expiry:view")
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) Long storeId) {
        return Result.success(expiryAlertLogService.dashboardStatistics(storeId));
    }
}