package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.dto.MemberLevelDTO;
import com.meiyetec.dto.PageQueryDTO;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MemberLevel;
import com.meiyetec.service.MemberLevelService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/member-level")
@RequiredArgsConstructor
@SysOpLog(module = "会员")
public class MemberLevelController {

    private final MemberLevelService memberLevelService;

    private Long tenantId() {
        return com.meiyetec.security.TenantContextHolder.get().getTenantId();
    }

    private MemberLevel requireLevel(Long id) {
        MemberLevel l = memberLevelService.getById(id);
        if (l == null) return null;
        Long tid = tenantId();
        if (tid != null && !tid.equals(l.getTenantId())) return null;
        return l;
    }

    @GetMapping("/list")
    @RequirePermission("member:level:list")
    public Result<List<MemberLevel>> list() {
        Long tid = tenantId();
        LambdaQueryWrapper<MemberLevel> w = new LambdaQueryWrapper<>();
        if (tid != null) w.eq(MemberLevel::getTenantId, tid);
        return Result.success(memberLevelService.list(w.orderByAsc(MemberLevel::getSortOrder)));
    }

    @PostMapping("/page")
    @RequirePermission("member:level:list")
    public Result<PageResult<MemberLevel>> page(@RequestBody PageQueryDTO query) {
        Page<MemberLevel> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<MemberLevel> wrapper = new LambdaQueryWrapper<>();
        Long tid = tenantId();
        if (tid != null) wrapper.eq(MemberLevel::getTenantId, tid);
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(MemberLevel::getLevelName, query.getKeyword())
                    .or().like(MemberLevel::getLevelCode, query.getKeyword()));
        }
        if (query.getStatus() != null) wrapper.eq(MemberLevel::getStatus, query.getStatus());
        wrapper.orderByAsc(MemberLevel::getSortOrder);
        return Result.success(PageResult.of(memberLevelService.page(page, wrapper)));
    }

    @GetMapping("/stats")
    @RequirePermission("member:level:list")
    public Result<Map<String, Object>> stats() {
        Long tid = tenantId();
        LambdaQueryWrapper<MemberLevel> w = new LambdaQueryWrapper<>();
        if (tid != null) w.eq(MemberLevel::getTenantId, tid);
        List<MemberLevel> all = memberLevelService.list(w);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        stats.put("active", all.stream().filter(l -> l.getStatus() != null && l.getStatus() == 1).count());
        stats.put("inactive", all.stream().filter(l -> l.getStatus() != null && l.getStatus() == 0).count());
        Double avgDiscount = all.stream()
                .filter(l -> l.getDiscountRate() != null)
                .mapToDouble(l -> l.getDiscountRate().doubleValue())
                .average().orElse(0);
        stats.put("avgDiscount", Math.round(avgDiscount * 100));
        return Result.success(stats);
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("member:level:list")
    public Result<MemberLevel> getById(@PathVariable Long id) {
        MemberLevel l = requireLevel(id);
        if (l == null) return Result.error("等级不存在或无权访问");
        return Result.success(l);
    }

    @PostMapping
    @RequirePermission("member:level:edit")
    public Result<Void> create(@Valid @RequestBody MemberLevelDTO dto) {
        Long tid = tenantId();
        MemberLevel level = new MemberLevel();
        org.springframework.beans.BeanUtils.copyProperties(dto, level);
        level.setTenantId(tid);
        memberLevelService.save(level);
        return Result.success("创建成功", null);
    }

    @PutMapping
    @RequirePermission("member:level:edit")
    public Result<Void> update(@Valid @RequestBody MemberLevelDTO dto) {
        if (dto.getId() == null) return Result.error("ID不能为空");
        MemberLevel level = requireLevel(dto.getId());
        if (level == null) return Result.error("等级不存在或无权访问");
        org.springframework.beans.BeanUtils.copyProperties(dto, level);
        memberLevelService.updateById(level);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("member:level:edit")
    public Result<Void> delete(@PathVariable Long id) {
        MemberLevel level = requireLevel(id);
        if (level == null) return Result.error("等级不存在或无权访问");
        memberLevelService.removeById(id);
        return Result.success("删除成功", null);
    }
}