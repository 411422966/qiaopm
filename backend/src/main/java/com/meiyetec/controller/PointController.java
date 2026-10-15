package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.PointService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 积分 controller（/market/point/*）
 *  - 所有端点自动注入 tenantId 做隔离
 *  - 必填参数校验 → BizCode.BAD_REQUEST
 *  - 异常 → BizCode 统一包装
 */
@RestController
@RequestMapping("/market/point")
@RequiredArgsConstructor
@SysOpLog(module = "积分")
public class PointController {

    private final PointService pointService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/{memberId}")
    public Result<Map<String, Object>> getMemberPoints(@PathVariable Long memberId) {
        // 即使未传 tenant 上下文，service 也会兜底
        return Result.success(pointService.getMemberPoints(memberId));
    }

    @GetMapping("/{memberId}/records")
    public Result<Map<String, Object>> getPointRecords(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(pointService.getPointRecords(memberId, pageNum, pageSize));
    }

    @PostMapping("/add")
    public Result<Long> addPoints(@RequestBody Map<String, Object> params) {
        // tid() 让 TenantContextHolder 初始化
        tid();
        if (params.get("memberId") == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        if (params.get("source") == null) return Result.error(BizCode.BAD_REQUEST, "source 必填");
        if (params.get("points") == null) return Result.error(BizCode.BAD_REQUEST, "points 必填");
        Long memberId = ((Number) params.get("memberId")).longValue();
        String source = params.get("source").toString();
        Integer points = ((Number) params.get("points")).intValue();
        if (points <= 0) return Result.error(BizCode.BAD_REQUEST, "points 必须 > 0");
        Long refId = params.get("refId") == null ? null : ((Number) params.get("refId")).longValue();
        String remark = (String) params.get("remark");
        Long id = pointService.addPointsReturningId(memberId, source, points, refId, remark);
        return Result.success("增加成功", id);
    }

    @PostMapping("/consume")
    public Result<Void> consumePoints(@RequestBody Map<String, Object> params) {
        tid();
        if (params.get("memberId") == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        if (params.get("source") == null) return Result.error(BizCode.BAD_REQUEST, "source 必填");
        if (params.get("points") == null) return Result.error(BizCode.BAD_REQUEST, "points 必填");
        Long memberId = ((Number) params.get("memberId")).longValue();
        String source = params.get("source").toString();
        Integer points = ((Number) params.get("points")).intValue();
        if (points <= 0) return Result.error(BizCode.BAD_REQUEST, "points 必须 > 0");
        Long refId = params.get("refId") == null ? null : ((Number) params.get("refId")).longValue();
        String remark = (String) params.get("remark");
        pointService.consumePoints(memberId, source, points, refId, remark);
        return Result.success("消费成功", null);
    }
}