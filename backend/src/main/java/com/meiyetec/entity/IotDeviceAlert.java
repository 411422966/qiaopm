package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * IoT 设备告警
 * 设备异常/维护提醒/数据异常 
*/
@Data
@TableName("biz_iot_device_alert")
public class IotDeviceAlert {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 设备ID 
*/    private Long deviceId;    
/** 设备名称 
*/    
private String deviceName;    
/** 告警级别: critical/warning/info 
*/    
private String alertLevel;    
/** 告警类型: offline/error/data_anomaly/maintenance/firmware_update/low_battery 
*/    
private String alertType;    
/** 告警标题 
*/    
private String title;    
/** 告警详情 
*/    
private String message;    
/** 处理状态: pending/handling/resolved/ignored 
*/    
private String status;    
/** 处理人ID 
*/    private Long handlerId;    
/** 处理人姓名 
*/    
private String handlerName;    
/** 处理时间 
*/    private LocalDateTime handleTime;    
/** 处理备注 
*/    
private String handleNote;    private LocalDateTime alertTime;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
