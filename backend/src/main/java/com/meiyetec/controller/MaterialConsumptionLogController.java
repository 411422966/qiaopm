package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MaterialConsumptionLog;
import com.meiyetec.service.MaterialConsumptionLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/material/consumption")
@RequiredArgsConstructor
@SysOpLog(module = "Material Consumption Log")
public class MaterialConsumptionLogController {

    private final MaterialConsumptionLogService materialConsumptionLogService;

    @GetMapping("/by-order/{orderId}")
    public Result<List<MaterialConsumptionLog>> findByOrderId(@PathVariable Long orderId) {
        return Result.success(materialConsumptionLogService.findByOrderId(orderId));
    }

    @GetMapping("/statistics/by-product")
    public Result<List<Map<String, Object>>> statisticsByProduct(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(materialConsumptionLogService.statisticsByProduct(storeId, startDate, endDate));
    }

    @GetMapping("/statistics/by-service")
    public Result<List<Map<String, Object>>> statisticsByService(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(materialConsumptionLogService.statisticsByService(storeId, startDate, endDate));
    }
}
