package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CommissionSimulation;
import com.meiyetec.service.CommissionSimulationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/commission/simulation")
@RequiredArgsConstructor
@SysOpLog(module = "Commission Simulation")
public class CommissionSimulationController {

    private final CommissionSimulationService commissionSimulationService;

    @PostMapping
    public Result<Map<String, Object>> simulate(@RequestBody Map<String, Object> params) {
        return Result.success("计算完成", commissionSimulationService.simulate(params));
    }

    @GetMapping("/recent/{employeeId}")
    public Result<List<CommissionSimulation>> recent(@PathVariable Long employeeId) {
        return Result.success(commissionSimulationService.getRecentByEmployee(employeeId));
    }
}
