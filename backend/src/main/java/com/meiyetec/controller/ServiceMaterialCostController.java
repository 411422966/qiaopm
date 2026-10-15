package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.service.ServiceMaterialCostService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/material/cost")
@RequiredArgsConstructor
@SysOpLog(module = "MaterialCost")
public class ServiceMaterialCostController {

    private final ServiceMaterialCostService serviceMaterialCostService;

    @GetMapping("/analysis")
    public Result<List<Map<String, Object>>> analysis() {
        return Result.success(serviceMaterialCostService.getServiceCostAnalysis());
    }

    @GetMapping("/by-service/{serviceId}")
    public Result<Map<String, Object>> getServiceCost(@PathVariable Long serviceId) {
        return Result.success(serviceMaterialCostService.getServiceCost(serviceId));
    }
}
