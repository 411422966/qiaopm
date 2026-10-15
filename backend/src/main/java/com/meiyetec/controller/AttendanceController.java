package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.AttendanceRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.AttendanceRecordService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
@SysOpLog(module = "Attendance")
public class AttendanceController {

    private final AttendanceRecordService attendanceRecordService;
    private Long tid() { Long t = TenantContextHolder.get().getTenantId(); return t == null ? 1L : t; }

    @PostMapping("/check-in")
    public Result<AttendanceRecord> checkIn(@RequestBody Map<String, Object> params) {
        Long employeeId = getLong(params, "employeeId");
        String method = (String) params.getOrDefault("method", "app");
        String location = (String) params.getOrDefault("location", "");
        Long storeId = getLong(params, "storeId");
        AttendanceRecord record = attendanceRecordService.checkIn(tid(), employeeId, method, location, storeId);
        return Result.success("上班打卡成功", record);
    }

    @PostMapping("/check-out")
    public Result<AttendanceRecord> checkOut(@RequestBody Map<String, Object> params) {
        Long employeeId = getLong(params, "employeeId");
        String method = (String) params.getOrDefault("method", "app");
        String location = (String) params.getOrDefault("location", "");
        AttendanceRecord record = attendanceRecordService.checkOut(tid(), employeeId, method, location);
        return Result.success("下班打卡成功", record);
    }

    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayStatus(@RequestParam Long employeeId) {
        AttendanceRecord record = attendanceRecordService.getByEmployeeAndDate(tid(), employeeId, LocalDate.now());
        Map<String, Object> result = new HashMap<>();
        result.put("hasCheckedIn", record != null && record.getCheckInTime() != null);
        result.put("hasCheckedOut", record != null && record.getCheckOutTime() != null);
        result.put("checkInTime", record != null ? record.getCheckInTime() : null);
        result.put("checkOutTime", record != null ? record.getCheckOutTime() : null);
        result.put("checkInStatus", record != null ? record.getCheckInStatus() : null);
        result.put("workMinutes", record != null ? record.getWorkMinutes() : null);
        result.put("isLate", record != null && record.getIsLate() != null && record.getIsLate() == 1);
        result.put("lateMinutes", record != null ? record.getLateMinutes() : 0);
        result.put("isEarlyLeave", record != null && record.getIsEarlyLeave() != null && record.getIsEarlyLeave() == 1);
        return Result.success(result);
    }

    @PostMapping("/page")
    public Result<PageResult<AttendanceRecord>> page(@RequestBody Map<String, Object> params) {
        int pageNum = getInt(params, "pageNum", 1);
        int pageSize = getInt(params, "pageSize", 20);
        Long employeeId = getLong(params, "employeeId");
        Long storeId = getLong(params, "storeId");
        String startDateStr = (String) params.get("startDate");
        String endDateStr = (String) params.get("endDate");
        LocalDate startDate = startDateStr != null ? LocalDate.parse(startDateStr) : null;
        LocalDate endDate = endDateStr != null ? LocalDate.parse(endDateStr) : null;
        Long t = tid();
        Page<AttendanceRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceRecord::getTenantId, t);
        if (employeeId != null) wrapper.eq(AttendanceRecord::getEmployeeId, employeeId);
        if (storeId != null) wrapper.eq(AttendanceRecord::getStoreId, storeId);
        if (startDate != null) wrapper.ge(AttendanceRecord::getCheckDate, startDate);
        if (endDate != null) wrapper.le(AttendanceRecord::getCheckDate, endDate);
        wrapper.orderByDesc(AttendanceRecord::getCheckDate);
        var result = attendanceRecordService.page(page, wrapper);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Map<String, Object>> recs = attendanceRecordService.queryRecords(tid(), employeeId, storeId, startDate, endDate);
        return Result.success(recs);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> stats = attendanceRecordService.getStatistics(tid(), storeId, startDate, endDate);
        return Result.success(stats);
    }

    @GetMapping("/monthly-report")
    public Result<Map<String, Object>> monthlyReport(
            @RequestParam Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> report = attendanceRecordService.getMonthlyReport(tid(), employeeId, year, month);
        return Result.success(report);
    }

    @GetMapping("/{id:\\d+}")
    public Result<AttendanceRecord> getById(@PathVariable Long id) {
        AttendanceRecord r = attendanceRecordService.getById(id);
        if (r == null || !tid().equals(r.getTenantId())) return Result.error(BizCode.NOT_FOUND, "打卡记录不存在");
        return Result.success(r);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AttendanceRecord record) {
        AttendanceRecord exist = attendanceRecordService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) return Result.error(BizCode.NOT_FOUND, "打卡记录不存在");
        record.setId(id);
        record.setTenantId(tid());
        attendanceRecordService.updateById(record);
        return Result.success("更新成功", null);
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

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        return defaultValue;
    }
}