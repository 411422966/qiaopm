package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.AttendanceConfig;
public interface AttendanceConfigService extends IService<AttendanceConfig> {
    /**
     * 获取生效的考勤配置
     */
    AttendanceConfig getActiveConfig(Long tenantId, Long storeId);
    /**
     * 检查是否迟到
     */
    boolean isLate(java.time.LocalDateTime checkInTime, Long tenantId, Long storeId);
    /**
     * 检查是否早退
     */
    boolean isEarlyLeave(java.time.LocalDateTime checkOutTime, Long tenantId, Long storeId);
    /**
     * 获取允许迟到分钟数
     */
    int getAllowLateMinutes(Long tenantId, Long storeId);
}