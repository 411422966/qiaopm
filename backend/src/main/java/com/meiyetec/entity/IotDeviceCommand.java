package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-4：下行指令队列
 */
@Data
@TableName("iot_device_command")
public class IotDeviceCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    /** UNLOCK/RESTART/UPGRADE/RESET/... */
    private String commandCode;
    private String payload;
    /** PENDING/SENT/SUCCESS/FAILED/TIMEOUT */
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime ackAt;
    private String result;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}