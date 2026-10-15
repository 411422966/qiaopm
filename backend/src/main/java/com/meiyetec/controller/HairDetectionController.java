package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.HairDetection;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.HairDetectionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/detection/hair")
@RequiredArgsConstructor
@SysOpLog(module = "Hair Detection")
public class HairDetectionController {

    private final HairDetectionService hairDetectionService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    public Result<List<HairDetection>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String hairType) {
        return Result.success(hairDetectionService.queryList(tid(), memberId, storeId, hairType));
    }

    @GetMapping("/{id:\\d+}")
    public Result<HairDetection> getById(@PathVariable Long id) {
        HairDetection h = hairDetectionService.getById(id);
        if (h == null || !tid().equals(h.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        return Result.success(h);
    }

    @GetMapping("/latest/{memberId}")
    public Result<HairDetection> getLatest(@PathVariable Long memberId) {
        return Result.success(hairDetectionService.findLatestByMember(tid(), memberId));
    }

    @PostMapping
    public Result<HairDetection> create(@RequestBody HairDetection detection) {
        if (detection.getDetectionNo() == null) {
            detection.setDetectionNo("HR" + System.currentTimeMillis());
        }
        detection.setId(null);
        detection.setTenantId(tid());
        hairDetectionService.save(detection);
        return Result.success("创建成功", detection);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody HairDetection detection) {
        HairDetection exist = hairDetectionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        detection.setId(id);
        detection.setTenantId(tid());
        hairDetectionService.updateById(detection);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        HairDetection exist = hairDetectionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        hairDetectionService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/statistics/type")
    public Result<List<Map<String, Object>>> statsByType(@RequestParam(required = false) Long storeId) {
        return Result.success(hairDetectionService.statisticsByType(tid(), storeId));
    }
}