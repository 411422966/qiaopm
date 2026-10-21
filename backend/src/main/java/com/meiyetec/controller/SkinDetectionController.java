package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.SkinDetection;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.SkinDetectionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/detection/skin")
@RequiredArgsConstructor
@SysOpLog(module = "SkinDetection")
public class SkinDetectionController {

    private final SkinDetectionService skinDetectionService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    @RequirePermission("crm:detection:skin:list")
    public Result<List<SkinDetection>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String skinType) {
        return Result.success(skinDetectionService.queryList(tid(), memberId, storeId, skinType));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("crm:detection:skin:list")
    public Result<SkinDetection> getById(@PathVariable Long id) {
        SkinDetection s = skinDetectionService.getById(id);
        if (s == null || !tid().equals(s.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        return Result.success(s);
    }

    @GetMapping("/latest/{memberId}")
    @RequirePermission("crm:detection:skin:list")
    public Result<SkinDetection> getLatest(@PathVariable Long memberId) {
        return Result.success(skinDetectionService.findLatestByMember(tid(), memberId));
    }

    @PostMapping
    @RequirePermission("crm:detection:skin:edit")
    public Result<SkinDetection> create(@RequestBody SkinDetection detection) {
        if (detection.getDetectionNo() == null) {
            detection.setDetectionNo("SK" + System.currentTimeMillis());
        }
        detection.setId(null);
        detection.setTenantId(tid());
        skinDetectionService.save(detection);
        return Result.success("创建成功", detection);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("crm:detection:skin:edit")
    public Result<Void> update(@PathVariable Long id, @RequestBody SkinDetection detection) {
        SkinDetection exist = skinDetectionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        detection.setId(id);
        detection.setTenantId(tid());
        skinDetectionService.updateById(detection);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("crm:detection:skin:edit")
    public Result<Void> delete(@PathVariable Long id) {
        SkinDetection exist = skinDetectionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        skinDetectionService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/statistics/type")
    @RequirePermission("crm:detection:skin:list")
    public Result<List<Map<String, Object>>> statsByType(@RequestParam(required = false) Long storeId) {
        return Result.success(skinDetectionService.statisticsByType(tid(), storeId));
    }
}