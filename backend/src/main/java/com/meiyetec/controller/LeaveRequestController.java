package com.meiyetec.controller;

import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.LeaveRequest;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.LeaveRequestService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance/leave")
@RequiredArgsConstructor
@SysOpLog(module = "Leave Request")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @PostMapping
    public Result<LeaveRequest> submit(@RequestBody Map<String, Object> params) {
        Long employeeId = getLong(params, "employeeId");
        String leaveType = (String) params.get("leaveType");
        String startDateStr = (String) params.get("startDate");
        String endDateStr = (String) params.get("endDate");
        String reason = (String) params.getOrDefault("reason", "");
        Long storeId = getLong(params, "storeId");
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LeaveRequest request = leaveRequestService.submit(tid(), employeeId, leaveType, startDate, endDate, reason, storeId);
        return Result.success("请假申请已提交", request);
    }

    @PutMapping("/{id:\\d+}/approve")
    public Result<LeaveRequest> approve(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {
        Long approverId = getLong(params, "approverId");
        String remark = (String) params.getOrDefault("remark", "");
        boolean approved = Boolean.TRUE.equals(params.get("approved"));
        LeaveRequest request = leaveRequestService.approve(tid(), id, approverId, remark, approved);
        return Result.success(approved ? "审批通过" : "审批拒绝", request);
    }

    @PutMapping("/{id:\\d+}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Long employeeId = getLong(params, "employeeId");
        leaveRequestService.cancel(tid(), id, employeeId);
        return Result.success("请假申请已取消", null);
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> requests = leaveRequestService.queryRequests(tid(), employeeId, storeId, status, startDate, endDate);
        return Result.success(requests);
    }

    @GetMapping("/my")
    public Result<List<LeaveRequest>> myList(@RequestParam Long employeeId) {
        List<LeaveRequest> requests = leaveRequestService.getByEmployee(tid(), employeeId);
        return Result.success(requests);
    }

    @GetMapping("/pending-count")
    public Result<Integer> pendingCount(@RequestParam Long storeId) {
        int count = leaveRequestService.getPendingCount(tid(), storeId);
        return Result.success(count);
    }

    @GetMapping("/{id:\\d+}")
    public Result<LeaveRequest> getById(@PathVariable Long id) {
        LeaveRequest r = leaveRequestService.getById(id);
        if (r == null || !tid().equals(r.getTenantId())) return Result.error(BizCode.NOT_FOUND, "请假申请不存在");
        return Result.success(r);
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            String str = (String) value;
            return str.isEmpty() ? null : Long.parseLong(str);
        }
        return null;
    }
}