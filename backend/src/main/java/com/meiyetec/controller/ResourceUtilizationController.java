package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.ResourceUtilization;
import com.meiyetec.service.ResourceUtilizationService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * M11 技师/房间利用率 — 按 PRD (2026-09-04 确认版) 计算
 * <p>技师利用率 = 实际服务时长 / 班表工作时长 × 100% (PRD 目标 > 75%)<br>
 * 房间占用率 = 已完成服务时长 / 营业时长 × 100% (PRD 目标 > 80%)
 */
@RestController
@RequestMapping("/resource/utilization")
@RequiredArgsConstructor
@SysOpLog(module = "ResourceUtilization")
public class ResourceUtilizationController {

    private final ResourceUtilizationService resourceUtilizationService;

    /** 列表查询 */
    @GetMapping("/list")
    public Result<List<ResourceUtilization>> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(resourceUtilizationService.queryList(storeId, resourceType, resourceId, startDate, endDate));
    }

    /** TOP 排行（按利用率 AVG 排序） */
    @GetMapping("/top")
    public Result<List<Map<String, Object>>> top(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(resourceUtilizationService.topUtilization(storeId, startDate, endDate));
    }

    /** 重算某日某门店利用率 */
    @PostMapping("/recalculate")
    public Result<Void> recalculate(@RequestBody Map<String, Object> params) {
        Long storeId = ((Number) params.get("storeId")).longValue();
        LocalDate date = LocalDate.parse((String) params.get("date"));
        resourceUtilizationService.recalculate(storeId, date);
        return Result.success("重算完成", null);
    }
}
