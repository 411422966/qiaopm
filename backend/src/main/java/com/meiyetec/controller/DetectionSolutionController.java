package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.DetectionSolution;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.DetectionSolutionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crm/detection/solution")
@RequiredArgsConstructor
@SysOpLog(module = "Detection Solution")
public class DetectionSolutionController {

    private final DetectionSolutionService detectionSolutionService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    @RequirePermission("crm:detection:solution:list")
    public Result<List<DetectionSolution>> list(
            @RequestParam(required = false) String applyType,
            @RequestParam(required = false) Integer status) {
        return Result.success(detectionSolutionService.queryList(tid(), applyType, status));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("crm:detection:solution:list")
    public Result<DetectionSolution> getById(@PathVariable Long id) {
        DetectionSolution s = detectionSolutionService.getById(id);
        if (s == null || !tid().equals(s.getTenantId())) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        return Result.success(s);
    }

    @PostMapping
    @RequirePermission("crm:detection:solution:edit")
    public Result<DetectionSolution> create(@RequestBody DetectionSolution solution) {
        solution.setId(null);
        solution.setTenantId(tid());
        detectionSolutionService.save(solution);
        return Result.success("创建成功", solution);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("crm:detection:solution:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody DetectionSolution solution) {
        DetectionSolution exist = detectionSolutionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        solution.setId(id);
        solution.setTenantId(tid());
        detectionSolutionService.updateById(solution);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("crm:detection:solution:edit")
    public Result<Void> delete(@PathVariable Long id) {
        DetectionSolution exist = detectionSolutionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        detectionSolutionService.removeById(id);
        return Result.success("删除成功", null);
    }
}