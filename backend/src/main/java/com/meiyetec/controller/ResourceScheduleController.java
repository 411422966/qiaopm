package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.Booking;
import com.meiyetec.service.ResourceScheduleService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/resource/schedule")
@RequiredArgsConstructor
@SysOpLog(module = "ResourceSchedule")
public class ResourceScheduleController {

    private final ResourceScheduleService resourceScheduleService;

    @GetMapping("/check-conflict")
    @RequirePermission("resource:schedule:view")
    public Result<ResourceScheduleService.ConflictCheckResult> checkConflict(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long excludeBookingId) {
        return Result.success(resourceScheduleService.checkConflict(
                roomId, employeeId, startTime, endTime, excludeBookingId));
    }

    @GetMapping("/suggest")
    @RequirePermission("resource:schedule:view")
    public Result<List<ResourceScheduleService.ScheduleSuggestion>> suggest(
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(defaultValue = "60") Integer durationMinutes) {
        return Result.success(resourceScheduleService.suggest(serviceId, startTime, durationMinutes));
    }

    @PostMapping("/create")
    @RequirePermission("resource:schedule:edit")
    public Result<Booking> createSchedule(@RequestBody Booking booking,
                                          @RequestParam(defaultValue = "manual_resolve") String conflictResolveAction) {
        return Result.success("创建成功", resourceScheduleService.createSchedule(booking, conflictResolveAction));
    }
}