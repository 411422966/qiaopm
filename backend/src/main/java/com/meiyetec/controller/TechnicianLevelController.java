package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.TechnicianLevel;
import com.meiyetec.service.TechnicianLevelService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/technician/level")
@RequiredArgsConstructor
@SysOpLog(module = "Technician Level")
public class TechnicianLevelController {

    private final TechnicianLevelService technicianLevelService;

    @GetMapping("/list")
    public Result<List<TechnicianLevel>> list() {
        LambdaQueryWrapper<TechnicianLevel> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(TechnicianLevel::getSortOrder);
        return Result.success(technicianLevelService.list(wrapper));
    }

    @PostMapping("/page")
    public Result<PageResult<TechnicianLevel>> page(@RequestBody Map<String, Object> params) {
        int pageNum = getInt(params, "pageNum", 1);
        int pageSize = getInt(params, "pageSize", 20);
        String keyword = (String) params.get("keyword");
        Integer status = params.get("status") == null ? null : Integer.valueOf(params.get("status").toString());
        Page<TechnicianLevel> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TechnicianLevel> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(TechnicianLevel::getLevelName, keyword)
                    .or().like(TechnicianLevel::getLevelCode, keyword);
        }
        if (status != null) {
            wrapper.eq(TechnicianLevel::getStatus, status);
        }
        wrapper.orderByAsc(TechnicianLevel::getSortOrder);
        return Result.success(PageResult.of(technicianLevelService.page(page, wrapper)));
    }

    @GetMapping("/active")
    public Result<List<TechnicianLevel>> activeList() {
        return Result.success(technicianLevelService.getActiveLevels());
    }

    @GetMapping("/{id:\\d+}")
    public Result<TechnicianLevel> getById(@PathVariable Long id) {
        return Result.success(technicianLevelService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody TechnicianLevel level) {
        technicianLevelService.save(level);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TechnicianLevel level) {
        level.setId(id);
        technicianLevelService.updateById(level);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        technicianLevelService.removeById(id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id:\\d+}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Integer status = Integer.valueOf(params.get("status").toString());
        technicianLevelService.toggleStatus(id, status);
        return Result.success("操作成功", null);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("levelDistribution", technicianLevelService.countEmployeesByLevel());
        return Result.success(result);
    }

    /**
     * M08 晋升评估：评估指定员工是否满足晋升到下一等级的条件
     */
    @GetMapping("/promote/evaluate")
    public Result<Map<String, Object>> evaluatePromotion(@RequestParam Long employeeId) {
        return Result.success(technicianLevelService.evaluatePromotion(employeeId));
    }

    /**
     * M08 执行晋升：将员工晋升到下一等级，并联动重算提成
     */
    @PostMapping("/promote")
    public Result<Map<String, Object>> promote(@RequestBody Map<String, Object> body) {
        Long employeeId = ((Number) body.get("employeeId")).longValue();
        Long operatorId = body.get("operatorId") == null ? null : ((Number) body.get("operatorId")).longValue();
        String note = (String) body.get("note");
        Map<String, Object> result = technicianLevelService.promote(employeeId, operatorId, note);
        return Result.success("晋升成功", result);
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        return defaultValue;
    }
}
