package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.AttendanceConfig;
import com.meiyetec.mapper.AttendanceConfigMapper;
import com.meiyetec.service.AttendanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AttendanceConfigServiceImpl extends ServiceImpl<AttendanceConfigMapper, AttendanceConfig>
        implements AttendanceConfigService {

    private final AttendanceConfigMapper attendanceConfigMapper;

    @Override
    public AttendanceConfig getActiveConfig(Long tenantId, Long storeId) {
        return attendanceConfigMapper.findActiveConfig(tenantId, storeId);
    }

    @Override
    public boolean isLate(LocalDateTime checkInTime, Long tenantId, Long storeId) {
        AttendanceConfig config = getActiveConfig(tenantId, storeId);
        if (config == null || config.getWorkStartTime() == null) {
            return false;
        }
        int allowLateMinutes = config.getAllowLateMinutes() != null ? config.getAllowLateMinutes() : 0;
        LocalDateTime workStartTime = checkInTime.with(config.getWorkStartTime());
        LocalDateTime latestAcceptableTime = workStartTime.plusMinutes(allowLateMinutes);
        return checkInTime.isAfter(latestAcceptableTime);
    }

    @Override
    public boolean isEarlyLeave(LocalDateTime checkOutTime, Long tenantId, Long storeId) {
        AttendanceConfig config = getActiveConfig(tenantId, storeId);
        if (config == null || config.getWorkEndTime() == null) {
            return false;
        }
        int earlyLeaveMinutes = config.getEarlyLeaveMinutes() != null ? config.getEarlyLeaveMinutes() : 0;
        LocalDateTime workEndTime = checkOutTime.with(config.getWorkEndTime());
        LocalDateTime earliestAcceptableTime = workEndTime.minusMinutes(earlyLeaveMinutes);
        return checkOutTime.isBefore(earliestAcceptableTime);
    }

    @Override
    public int getAllowLateMinutes(Long tenantId, Long storeId) {
        AttendanceConfig config = getActiveConfig(tenantId, storeId);
        return config != null && config.getAllowLateMinutes() != null ? config.getAllowLateMinutes() : 0;
    }
}