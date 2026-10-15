package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("iot_command_dest")
public class IotCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private String deviceCode;
    /** START / STOP / RESET / CALIBRATE / UPGRADE / CUSTOM */
    private String commandType;
    private String commandPayload;
    /** 1-10, 1 最高 */
    private Integer priority;
    /** PENDING / SENT / ACK / SUCCESS / FAIL / TIMEOUT */
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime ackAt;
    private LocalDateTime completedAt;
    private String responsePayload;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}