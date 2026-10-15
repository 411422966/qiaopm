package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionLogService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/care/suggestion/log")
@RequiredArgsConstructor
@SysOpLog(module = "CareSuggestionLog")
public class CareSuggestionLogController {

    private final CareSuggestionLogService careSuggestionLogService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @GetMapping("/list")
    public Result<List<CareSuggestionLog>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String suggestionType) {
        return Result.success(careSuggestionLogService.queryList(memberId, status, suggestionType));
    }

    @GetMapping("/{id:\\d+}")
    public Result<CareSuggestionLog> getById(@PathVariable Long id) {
        CareSuggestionLog l = careSuggestionLogService.getById(id);
        if (l == null || !tid().equals(l.getTenantId())) return Result.error(BizCode.NOT_FOUND, "日志不存在");
        return Result.success(l);
    }

    @PutMapping("/{id:\\d+}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> params) {
        careSuggestionLogService.updateStatus(id, params.get("status"));
        return Result.success("更新成功", null);
    }

    @PutMapping("/{id:\\d+}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        careSuggestionLogService.markAccepted(id);
        return Result.success("已采纳", null);
    }

    @PutMapping("/{id:\\d+}/view")
    public Result<Void> view(@PathVariable Long id) {
        careSuggestionLogService.markViewed(id);
        return Result.success("已查看", null);
    }

    @PutMapping("/{id:\\d+}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        careSuggestionLogService.markRejected(id);
        return Result.success("已拒绝", null);
    }
}
