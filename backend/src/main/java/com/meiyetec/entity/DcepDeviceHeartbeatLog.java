package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DCEP-04 阶段 C：设备心跳日志
 */
@Data
@TableName("biz_dcep_device_heartbeat_log")
public class DcepDeviceHeartbeatLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private String deviceSerial;
    /** 心跳时刻 */
    private LocalDateTime heartbeatAt;
    /** 电量百分比 0~100 */
    private Integer batteryLevel;
    /** 信号强度 dBm */
    private Integer rssi;
    /** 当前固件版本 */
    private String fwVersion;
    /** 累计离线交易数（设备侧累计） */
    private Long offlineTxCount;
    /** 累计待同步交易数 */
    private Long pendingSyncCount;
    /** 异常状态（无异常时为空） */
    private String healthStatus;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
