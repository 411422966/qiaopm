package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.EmployeeSchedule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.service.EmployeeScheduleService;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import com.meiyetec.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * M06 技师排班 CRUD
 * T06-15：CRUD
 */
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
@SysOpLog(module = "排班")
public class ScheduleController {

    private final EmployeeScheduleService scheduleService;

    private Long tenantId() {
        Long id = TenantContextHolder.get().getTenantId();
        if (id == null) return null;
        return id;
    }

    private LambdaQueryWrapper<EmployeeSchedule> baseWrapper(Long tid) {
        return new LambdaQueryWrapper<EmployeeSchedule>()
                .eq(EmployeeSchedule::getTenantId, tid);
    }

    /** 全量列表（按技师 + 日期） */
    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        LambdaQueryWrapper<EmployeeSchedule> qw = baseWrapper(tid);
        if (employeeId != null) qw.eq(EmployeeSchedule::getEmployeeId, employeeId);
        if (startDate != null && !startDate.isEmpty())
            qw.ge(EmployeeSchedule::getWorkDate, java.time.LocalDate.parse(startDate));
        if (endDate != null && !endDate.isEmpty())
            qw.le(EmployeeSchedule::getWorkDate, java.time.LocalDate.parse(endDate));
        qw.orderByAsc(EmployeeSchedule::getWorkDate);
        return Result.success(scheduleService.list(qw));
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        LambdaQueryWrapper<EmployeeSchedule> qw = baseWrapper(tid);
        if (employeeId != null) qw.eq(EmployeeSchedule::getEmployeeId, employeeId);
        if (startDate != null && !startDate.isEmpty())
            qw.ge(EmployeeSchedule::getWorkDate, java.time.LocalDate.parse(startDate));
        if (endDate != null && !endDate.isEmpty())
            qw.le(EmployeeSchedule::getWorkDate, java.time.LocalDate.parse(endDate));
        qw.orderByAsc(EmployeeSchedule::getWorkDate);
        IPage<EmployeeSchedule> p = scheduleService.page(new Page<>(current, size), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        data.put("current", p.getCurrent());
        data.put("size", p.getSize());
        return Result.success(data);
    }

    @GetMapping("/{id:\\d+}")
    public Result<EmployeeSchedule> get(@PathVariable Long id) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        EmployeeSchedule s = scheduleService.getOne(baseWrapper(tid).eq(EmployeeSchedule::getId, id));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "排班不存在");
        return Result.success(s);
    }

    /** 新增排班（已存在则报错） */
    @PostMapping
    public Result<EmployeeSchedule> create(@RequestBody EmployeeSchedule s) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        if (s.getEmployeeId() == null || s.getWorkDate() == null) {
            return Result.error("技师与日期必填");
        }
        // 同一租户内排重
        EmployeeSchedule exists = scheduleService.getOne(
                baseWrapper(tid)
                        .eq(EmployeeSchedule::getEmployeeId, s.getEmployeeId())
                        .eq(EmployeeSchedule::getWorkDate, s.getWorkDate()));
        if (exists != null) return Result.error("该技师当天已有排班");
        s.setTenantId(tid);
        scheduleService.save(s);
        return Result.success("排班创建成功", s);
    }

    @PutMapping("/{id:\\d+}")
    public Result<EmployeeSchedule> update(@PathVariable Long id, @RequestBody EmployeeSchedule s) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        EmployeeSchedule old = scheduleService.getOne(baseWrapper(tid).eq(EmployeeSchedule::getId, id));
        if (old == null) return Result.error(BizCode.NOT_FOUND, "排班不存在");
        s.setId(id);
        s.setTenantId(old.getTenantId());
        scheduleService.updateById(s);
        return Result.success("排班更新成功", scheduleService.getById(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        Long tid = tenantId();
        if (tid == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        EmployeeSchedule s = scheduleService.getOne(baseWrapper(tid).eq(EmployeeSchedule::getId, id));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "排班不存在");
        scheduleService.removeById(id);
        return Result.success("删除成功", null);
    }
}
