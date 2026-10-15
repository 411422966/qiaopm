package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.DetectionReport;
import com.meiyetec.entity.DetectionSolution;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.DetectionReportService;
import com.meiyetec.service.DetectionSolutionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/detection/report")
@RequiredArgsConstructor
@SysOpLog(module = "DetectionReport")
public class DetectionReportController {

    private final DetectionReportService detectionReportService;
    private final DetectionSolutionService detectionSolutionService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    public Result<List<DetectionReport>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status) {
        return Result.success(detectionReportService.queryList(tid(), memberId, storeId, reportType, status));
    }

    @GetMapping("/{id:\\d+}")
    public Result<DetectionReport> getById(@PathVariable Long id) {
        DetectionReport r = detectionReportService.getById(id);
        if (r == null || !tid().equals(r.getTenantId())) return Result.error(BizCode.NOT_FOUND, "报告不存在");
        return Result.success(r);
    }

    @PostMapping("/generate")
    public Result<DetectionReport> generate(@RequestBody Map<String, Object> params) {
        Long memberId = params.get("memberId") == null ? null : ((Number) params.get("memberId")).longValue();
        String reportType = (String) params.get("reportType");
        DetectionReport report = detectionReportService.generateReport(tid(), memberId, reportType);
        return Result.success("生成成功", report);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DetectionReport report) {
        DetectionReport exist = detectionReportService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "报告不存在");
        report.setId(id);
        report.setTenantId(tid());
        detectionReportService.updateById(report);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        DetectionReport exist = detectionReportService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "报告不存在");
        detectionReportService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/statistics/type")
    public Result<List<Map<String, Object>>> statsByType(@RequestParam(required = false) Long storeId) {
        return Result.success(detectionReportService.statisticsByType(tid(), storeId));
    }

    @GetMapping("/solution/match")
    public Result<List<DetectionSolution>> matchSolution(
            @RequestParam(required = false) String applyType,
            @RequestParam(required = false) Integer score) {
        return Result.success(detectionSolutionService.matchByScore(tid(), applyType, score == null ? 0 : score));
    }
}