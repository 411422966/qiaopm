package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.LevelEvaluation;
import com.meiyetec.service.LevelEvaluationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/technician/level/evaluation")
@RequiredArgsConstructor
@SysOpLog(module = "LevelEvaluation")
public class LevelEvaluationController {

    private final LevelEvaluationService levelEvaluationService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status) {
        return Result.success(levelEvaluationService.queryRecords(employeeId, storeId, status));
    }

    @GetMapping("/by-employee/{employeeId}")
    public Result<List<LevelEvaluation>> getByEmployee(@PathVariable Long employeeId) {
        return Result.success(levelEvaluationService.getByEmployeeId(employeeId));
    }

    @PostMapping("/auto/{employeeId}")
    public Result<LevelEvaluation> autoEvaluate(@PathVariable Long employeeId, @RequestBody Map<String, Object> params) {
        Long operatorId = params.get("operatorId") == null ? null : Long.valueOf(params.get("operatorId").toString());
        String operatorName = (String) params.get("operatorName");
        Long storeId = params.get("storeId") == null ? null : Long.valueOf(params.get("storeId").toString());
        return Result.success("评定完成", levelEvaluationService.evaluate(employeeId, operatorId, operatorName, storeId));
    }

    @PostMapping("/adjust")
    public Result<LevelEvaluation> adjustLevel(@RequestBody Map<String, Object> params) {
        Long employeeId = Long.valueOf(params.get("employeeId").toString());
        Long toLevelId = Long.valueOf(params.get("toLevelId").toString());
        String note = (String) params.get("note");
        Long operatorId = params.get("operatorId") == null ? null : Long.valueOf(params.get("operatorId").toString());
        String operatorName = (String) params.get("operatorName");
        Long storeId = params.get("storeId") == null ? null : Long.valueOf(params.get("storeId").toString());
        return Result.success("调整成功", levelEvaluationService.adjustLevel(employeeId, toLevelId, note, operatorId, operatorName, storeId));
    }

    @PutMapping("/{id:\\d+}/revert")
    public Result<Void> revert(@PathVariable Long id) {
        levelEvaluationService.revert(id);
        return Result.success("撤销成功", null);
    }

    @GetMapping("/metrics/{employeeId}")
    public Result<Map<String, Object>> getMetrics(@PathVariable Long employeeId) {
        return Result.success(levelEvaluationService.calculateEmployeeMetrics(employeeId));
    }

    @GetMapping("/{id:\\d+}")
    public Result<LevelEvaluation> getById(@PathVariable Long id) {
        return Result.success(levelEvaluationService.getById(id));
    }
}
