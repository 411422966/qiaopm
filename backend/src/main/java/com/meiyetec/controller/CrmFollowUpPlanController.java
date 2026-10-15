package com.meiyetec.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CrmFollowUpPlan;
import com.meiyetec.service.CrmFollowUpPlanService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/care/follow/plan")
@RequiredArgsConstructor
@SysOpLog(module = "Follow-up Plan")
public class CrmFollowUpPlanController {

    private final CrmFollowUpPlanService planService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "20") long pageSize) {
        Page<CrmFollowUpPlan> pg = planService.pagePlan(pageNum, pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", pg.getRecords());
        data.put("total", pg.getTotal());
        return Result.success(data);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(planService.stats());
    }

    @GetMapping("/week-trend")
    public Result<List<Map<String, Object>>> weekTrend() {
        return Result.success(planService.weekTrend());
    }

    @GetMapping("/{id:\\d+}")
    public Result<CrmFollowUpPlan> getById(@PathVariable Long id) {
        return Result.success(planService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody CrmFollowUpPlan plan) {
        if (plan.getEnabled() == null) plan.setEnabled(1);
        planService.save(plan);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CrmFollowUpPlan plan) {
        plan.setId(id);
        planService.updateById(plan);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.removeById(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id:\\d+}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        CrmFollowUpPlan p = new CrmFollowUpPlan();
        p.setId(id);
        p.setEnabled(1);
        planService.updateById(p);
        return Result.success("已启用", null);
    }

    @PostMapping("/{id:\\d+}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        CrmFollowUpPlan p = new CrmFollowUpPlan();
        p.setId(id);
        p.setEnabled(0);
        planService.updateById(p);
        return Result.success("已停用", null);
    }

    @PostMapping("/{id:\\d+}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable Long id) {
        return Result.success(planService.triggerPlan(id));
    }

    @GetMapping("/{id:\\d+}/preview-count")
    public Result<Map<String, Object>> preview(@PathVariable Long id) {
        return Result.success(planService.previewPlan(id));
    }

    @PostMapping("/demo-seed")
    public Result<Map<String, Object>> demoSeed() {
        int count = planService.seedDemo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("insertedCount", count);
        return Result.success("插入演示数据成功", data);
    }
}
