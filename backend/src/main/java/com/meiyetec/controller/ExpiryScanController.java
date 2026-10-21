package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.StockInventory;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.ExpiryScanService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock/expiry/scan")
@RequiredArgsConstructor
@SysOpLog(module = "ExpiryScan")
public class ExpiryScanController {

    private final ExpiryScanService expiryScanService;

    @PostMapping("/trigger")
    @RequirePermission("stock:expiry:edit")
    public Result<Map<String, Object>> trigger(@RequestParam(required = false) Long storeId) {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        Map<String, Object> result = expiryScanService.scan(storeId);
        return Result.success("扫描完成", result);
    }

    @GetMapping("/near-expiry")
    @RequirePermission("stock:expiry:view")
    public Result<List<StockInventory>> nearExpiry(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "30") Integer withinDays) {
        Long tid = TenantContextHolder.get().getTenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        return Result.success(expiryScanService.findNearExpiry(storeId, withinDays));
    }
}