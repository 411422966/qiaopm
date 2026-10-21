package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.PricingExperiment;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.PricingExperimentMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.PricingExperimentService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M14 动态定价 A/B 实验 REST API
 */
@Slf4j
@RestController
@RequestMapping("/mkt/pricing/experiment")
@RequiredArgsConstructor
public class PricingExperimentController {

    private final PricingExperimentService experimentService;
    private final PricingExperimentMapper expMapper;

    @GetMapping("/page")
    @RequirePermission("mkt:pricing:experiment:list")
    public Result<Page<PricingExperiment>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        return experimentService.page(pageNum, pageSize, status, keyword);
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("mkt:pricing:experiment:list")
    public Result<PricingExperiment> getById(@PathVariable Long id) {
        PricingExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        return Result.success(exp);
    }

    @PostMapping
    @RequirePermission("mkt:pricing:experiment:edit")
    public Result<PricingExperiment> create(@RequestBody PricingExperiment exp) {
        exp.setId(null);
        return experimentService.save(exp);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("mkt:pricing:experiment:edit")
    public Result<PricingExperiment> update(@PathVariable Long id, @RequestBody PricingExperiment exp) {
        exp.setId(id);
        return experimentService.save(exp);
    }

    @PostMapping("/{id:\\d+}/start")
    @RequirePermission("mkt:pricing:experiment:edit")
    public Result<Void> start(@PathVariable Long id) {
        return experimentService.start(id);
    }

    @PostMapping("/{id:\\d+}/pause")
    @RequirePermission("mkt:pricing:experiment:edit")
    public Result<Void> pause(@PathVariable Long id) {
        return experimentService.pause(id);
    }

    @PostMapping("/{id:\\d+}/complete")
    @RequirePermission("mkt:pricing:experiment:edit")
    public Result<Void> complete(@PathVariable Long id) {
        return experimentService.complete(id);
    }

    @GetMapping("/{id:\\d+}/stats")
    @RequirePermission("mkt:pricing:experiment:list")
    public Result<Map<String, Object>> stats(@PathVariable Long id) {
        return experimentService.stats(id);
    }

    @GetMapping("/{id:\\d+}/distribution")
    @RequirePermission("mkt:pricing:experiment:list")
    public Result<List<Map<String, Object>>> distribution(@PathVariable Long id) {
        return experimentService.resultDistribution(id);
    }

    @PostMapping("/{id:\\d+}/recompute")
    @RequirePermission("mkt:pricing:experiment:edit")
    public Result<Map<String, Object>> recompute(@PathVariable Long id) {
        experimentService.computeSignificance(id);
        return experimentService.stats(id);
    }
}