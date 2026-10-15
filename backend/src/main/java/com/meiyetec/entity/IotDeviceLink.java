package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物联网设备联动关系：一台主设备在某种触发条件下联动一台从设备
 */
@Data
@TableName("iot_device_link")
public class IotDeviceLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** 主设备 ID */
    private Long masterDeviceId;
    /** 从设备 ID */
    private Long slaveDeviceId;
    /** 触发类型（如 TELEMETRY / COMMAND / ALERT） */
    private String triggerType;
    /** 触发条件 JSON（数据条件表达式） */
    private String triggerCondition;
    /** 联动动作 JSON（如发送指令/告警） */
    private String actionJson;
    /** 1启用 0停用 */
    private Integer enabled;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}