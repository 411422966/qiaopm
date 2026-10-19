package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ExpiryHandleLog;
import com.meiyetec.service.ExpiryHandleLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/stock/expiry/handle")
@RequiredArgsConstructor
@SysOpLog(module = "Expiry Handle Log")
public class ExpiryHandleLogController {

    private final ExpiryHandleLogService expiryHandleLogService;

    @GetMapping("/list")
    public Result<List<ExpiryHandleLog>> list(
            @RequestParam(required = false) String handleMethod,
            @RequestParam(required = false) Long storeId) {
        return Result.success(expiryHandleLogService.queryList(handleMethod, storeId));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ExpiryHandleLog log) {
        expiryHandleLogService.logHandle(log);
        return Result.success("记录成功", null);
    }

    @GetMapping("/statistics/by-method")
    public Result<List<Map<String, Object>>> statisticsByMethod(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(expiryHandleLogService.statisticsByMethod(storeId, startDate, endDate));
    }

    @GetMapping("/total-loss")
    public Result<BigDecimal> totalLoss(@RequestParam(required = false) Long storeId) {
        return Result.success(expiryHandleLogService.totalLoss(storeId));
    }
}
