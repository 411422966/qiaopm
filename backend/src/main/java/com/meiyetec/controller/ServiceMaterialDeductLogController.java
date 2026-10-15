package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ServiceMaterialDeductLog;
import com.meiyetec.service.ServiceMaterialDeductLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stock/service-material/deduct-log")
@RequiredArgsConstructor
@SysOpLog(module = "Deduct Log")
public class ServiceMaterialDeductLogController {

    private final ServiceMaterialDeductLogService serviceMaterialDeductLogService;

    @GetMapping("/list")
    public Result<List<ServiceMaterialDeductLog>> list(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId) {
        return Result.success(serviceMaterialDeductLogService.queryList(orderId, status, storeId));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(serviceMaterialDeductLogService.statistics(storeId, startDate, endDate));
    }

    @GetMapping("/success-rate")
    public Result<Double> successRate(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        return Result.success(serviceMaterialDeductLogService.successRate(storeId, date));
    }
}
