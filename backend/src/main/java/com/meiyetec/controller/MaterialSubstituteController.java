package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MaterialSubstitute;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.MaterialSubstituteService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/material/substitute")
@RequiredArgsConstructor
@SysOpLog(module = "MaterialSubstitute")
public class MaterialSubstituteController {

    private final MaterialSubstituteService materialSubstituteService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/list")
    public Result<List<MaterialSubstitute>> list() {
        Long t = tid();
        return Result.success(materialSubstituteService.findAllWithDetails(t));
    }

    @GetMapping("/by-main/{mainProductId}")
    public Result<List<MaterialSubstitute>> findByMain(@PathVariable Long mainProductId) {
        Long t = tid();
        return Result.success(materialSubstituteService.findByMainProduct(mainProductId, t));
    }

    @GetMapping("/{id:\\d+}")
    public Result<MaterialSubstitute> getById(@PathVariable Long id) {
        Long t = tid();
        MaterialSubstitute m = materialSubstituteService.getById(id);
        if (m == null || !t.equals(m.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        return Result.success(m);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<MaterialSubstitute> create(@RequestBody MaterialSubstitute substitute) {
        Long t = tid();
        if (substitute.getMainProductId() == null) return Result.error(BizCode.BAD_REQUEST, "mainProductId 必填");
        substitute.setId(null);
        substitute.setTenantId(t);
        if (substitute.getStatus() == null) substitute.setStatus(1);
        materialSubstituteService.save(substitute);
        return Result.success("创建成功", substitute);
    }

    @PutMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    public Result<MaterialSubstitute> update(@PathVariable Long id, @RequestBody MaterialSubstitute substitute) {
        Long t = tid();
        MaterialSubstitute old = materialSubstituteService.getById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        substitute.setId(id);
        substitute.setTenantId(t);
        materialSubstituteService.updateById(substitute);
        return Result.success("更新成功", substitute);
    }

    @DeleteMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete(@PathVariable Long id) {
        Long t = tid();
        MaterialSubstitute old = materialSubstituteService.getById(id);
        if (old == null || !t.equals(old.getTenantId())) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        materialSubstituteService.removeById(id);
        return Result.success("删除成功", null);
    }
}