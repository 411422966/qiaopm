package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.AttendanceConfig;
public interface AttendanceConfigService extends IService<AttendanceConfig> {
    /**
     * 获取生效的考勤配置（按门店等值 + 全局行兜底，历史语义不变）。
     *
     * <p>供打卡判定（isLate / isEarlyLeave / getAllowLateMinutes）使用，
     * 传入的是记录自身的 storeId，不做会话范围裁决。</p>
     */
    AttendanceConfig getActiveConfig(Long tenantId, Long storeId);
    /**
     * 获取生效的考勤配置（I186 第 1 批：按当前会话有效门店范围解析）。
     *
     * <p>显式指定门店 → 先做范围校验（越权 403），再按「本店行 + 本租户全局行」取；
     * 缺省 → 按当前会话的有效门店集合取，门店行优先、全局行兜底。</p>
     */
    AttendanceConfig getActiveConfigForScope(Long tenantId, Long storeId);
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