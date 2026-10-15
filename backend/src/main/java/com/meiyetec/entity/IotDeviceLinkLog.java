package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * IOT-006：设备联动执行日志
 *
 * <p>每次联动触发（executeLink）记录一行：
 * <ul>
 *   <li>成功 / 失败状态</li>
 *   <li>触发数据快照</li>
 *   <li>下发的指令 JSON</li>
 *   <li>设备响应摘要</li>
 *   <li>执行耗时（毫秒）</li>
 *   <li>错误码与错误信息</li>
 * </ul>
 */
@Data
@TableName("iot_device_link_log")
public class IotDeviceLinkLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long linkId;
    private Long masterDeviceId;
    private Long slaveDeviceId;
    /** 触发类型：TELEMETRY / COMMAND / ALERT / MANUAL / SCHEDULED */
    private String triggerType;
    /** 触发条件是否满足（true / false） */
    private Integer triggerMatched;
    /** 触发数据快照 */
    private String triggerSnapshot;
    /** 下发的指令 JSON */
    private String commandPayload;
    /** 指令响应摘要 */
    private String responseSummary;
    /** 执行状态：success / failed / skipped / timeout */
    private String status;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 执行耗时（毫秒） */
    private Long durationMs;
    /** 下发协议：mqtt / http / modbus / ble */
    private String protocol;
    /** 设备收到响应的话题 */
    private String deviceTopic;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
