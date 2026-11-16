package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.AttendanceConfig;
import com.meiyetec.mapper.AttendanceConfigMapper;
import com.meiyetec.security.DataScopeService;
import com.meiyetec.security.StoreScope;
import com.meiyetec.service.AttendanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceConfigServiceImpl extends ServiceImpl<AttendanceConfigMapper, AttendanceConfig>
        implements AttendanceConfigService {

    private final AttendanceConfigMapper attendanceConfigMapper;
    private final DataScopeService dataScopeService;

    /**
     * 取生效的考勤配置（历史语义：门店等值 + 全局行兜底）。
     *
     * <p>打卡判定链路使用（isLate / isEarlyLeave / getAllowLateMinutes），
     * 传入的是记录自身的 storeId，不做会话范围裁决。</p>
     */
    @Override
    public AttendanceConfig getActiveConfig(Long tenantId, Long storeId) {
        return attendanceConfigMapper.findActiveConfig(tenantId, storeId);
    }

    /**
     * 取生效的考勤配置（I186 第 1 批：按当前会话有效门店范围解析）。
     *
     * <p>显式指定门店 → 先做范围校验（越权 403），再按「本店行 + 本租户全局行」取；
     * 缺省 → 按当前会话的有效门店集合取，门店行优先、全局行兜底。</p>
     */
    @Override
    public AttendanceConfig getActiveConfigForScope(Long tenantId, Long storeId) {
        if (storeId != null) {
            dataScopeService.assertStoreScope(storeId);
            return attendanceConfigMapper.findActiveConfig(tenantId, storeId);
        }
        StoreScope scope = dataScopeService.resolveStoreScope();
        List<Long> storeIds = scope.isAll() ? null : scope.resolveIds();
        return attendanceConfigMapper.findActiveConfigByStores(tenantId, storeIds);
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