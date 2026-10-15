package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.NailDetection;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.NailDetectionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/detection/nail")
@RequiredArgsConstructor
@SysOpLog(module = "Nail Detection")
public class NailDetectionController {

    private final NailDetectionService nailDetectionService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    public Result<List<NailDetection>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String nailType) {
        return Result.success(nailDetectionService.queryList(tid(), memberId, storeId, nailType));
    }

    @GetMapping("/{id:\\d+}")
    public Result<NailDetection> getById(@PathVariable Long id) {
        NailDetection n = nailDetectionService.getById(id);
        if (n == null || !tid().equals(n.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        return Result.success(n);
    }

    @GetMapping("/latest/{memberId}")
    public Result<NailDetection> getLatest(@PathVariable Long memberId) {
        return Result.success(nailDetectionService.findLatestByMember(tid(), memberId));
    }

    @PostMapping
    public Result<NailDetection> create(@RequestBody NailDetection detection) {
        if (detection.getDetectionNo() == null) {
            detection.setDetectionNo("NL" + System.currentTimeMillis());
        }
        detection.setId(null);
        detection.setTenantId(tid());
        nailDetectionService.save(detection);
        return Result.success("创建成功", detection);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody NailDetection detection) {
        NailDetection exist = nailDetectionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        detection.setId(id);
        detection.setTenantId(tid());
        nailDetectionService.updateById(detection);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        NailDetection exist = nailDetectionService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "检测记录不存在");
        nailDetectionService.removeById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/statistics/type")
    public Result<List<Map<String, Object>>> statsByType(@RequestParam(required = false) Long storeId) {
        return Result.success(nailDetectionService.statisticsByType(tid(), storeId));
    }
}