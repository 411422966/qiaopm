package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.LevelBenefit;
import com.meiyetec.entity.MemberLevel;
import com.meiyetec.service.LevelBenefitService;
import com.meiyetec.service.MemberLevelService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/technician/level/benefit")
@RequiredArgsConstructor
@SysOpLog(module = "Level Benefit")
public class LevelBenefitController {

    private final LevelBenefitService levelBenefitService;
    private final MemberLevelService memberLevelService;

    private Long tenantId() {
        return com.meiyetec.security.TenantContextHolder.get().getTenantId();
    }

    private LevelBenefit requireBenefit(Long id) {
        LevelBenefit b = levelBenefitService.getById(id);
        if (b == null) return null;
        Long tid = tenantId();
        if (tid != null && !tid.equals(b.getTenantId())) return null;
        return b;
    }

    private void setTenantId(LevelBenefit b) {
        Long tid = tenantId();
        if (b != null) b.setTenantId(tid);
    }

    @GetMapping("/list")
    @RequirePermission("employee:level:benefit")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long levelId) {
        Long tid = tenantId();
        if (tid != null && levelId != null) {
            MemberLevel level = memberLevelService.getById(levelId);
            if (level == null || !tid.equals(level.getTenantId())) {
                return Result.success(List.of());
            }
        }
        return Result.success(levelBenefitService.getWithLevel(levelId));
    }

    @GetMapping("/by-level/{levelId}")
    @RequirePermission("employee:level:benefit")
    public Result<List<LevelBenefit>> getByLevelId(@PathVariable Long levelId) {
        Long tid = tenantId();
        if (tid != null && levelId != null) {
            MemberLevel level = memberLevelService.getById(levelId);
            if (level == null || !tid.equals(level.getTenantId())) {
                return Result.success(List.of());
            }
        }
        return Result.success(levelBenefitService.getByLevelId(levelId));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("employee:level:benefit")
    public Result<LevelBenefit> getById(@PathVariable Long id) {
        LevelBenefit b = requireBenefit(id);
        if (b == null) return Result.error("权益不存在或无权访问");
        return Result.success(b);
    }

    @PostMapping
    @RequirePermission("employee:level:benefit")
    public Result<Void> create(@RequestBody LevelBenefit benefit) {
        Long tid = tenantId();
        if (tid != null && benefit.getLevelId() != null) {
            MemberLevel level = memberLevelService.getById(benefit.getLevelId());
            if (level == null || !tid.equals(level.getTenantId())) {
                return Result.error("等级不存在或无权访问");
            }
        }
        setTenantId(benefit);
        levelBenefitService.save(benefit);
        return Result.success("创建成功", null);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("employee:level:benefit")
    public Result<Void> update(@PathVariable Long id, @RequestBody LevelBenefit benefit) {
        LevelBenefit existing = requireBenefit(id);
        if (existing == null) return Result.error("权益不存在或无权访问");
        benefit.setId(id);
        setTenantId(benefit);
        levelBenefitService.updateById(benefit);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("employee:level:benefit")
    public Result<Void> delete(@PathVariable Long id) {
        LevelBenefit b = requireBenefit(id);
        if (b == null) return Result.error("权益不存在或无权访问");
        levelBenefitService.removeById(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/batch/{levelId}")
    @RequirePermission("employee:level:benefit")
    public Result<Void> batchSave(@PathVariable Long levelId, @RequestBody List<LevelBenefit> benefits) {
        Long tid = tenantId();
        if (tid != null) {
            MemberLevel level = memberLevelService.getById(levelId);
            if (level == null || !tid.equals(level.getTenantId())) {
                return Result.error("等级不存在或无权访问");
            }
        }
        levelBenefitService.batchSave(levelId, benefits, tid);
        return Result.success("保存成功", null);
    }
}