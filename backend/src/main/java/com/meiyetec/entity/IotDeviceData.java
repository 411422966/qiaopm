package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * IoT 设备数据采集记录
 * 记录设备每次上报的传感器数据/操作日志 
*/
@Data
@TableName("biz_iot_device_data")
public class IotDeviceData {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 设备ID 
*/    private Long deviceId;    
/** 设备编号 
*/    
private String deviceCode;    
/** 设备类型 
*/    
private String deviceType;    
/** 客户ID(关联客户) 
*/    private Long memberId;    
/** 客户姓名 
*/    
private String memberName;    
/** 关联订单ID 
*/    private Long orderId;    
/** 数据类型: detection/treatment/operation/error/heartbeat 
*/    
private String dataType;    
/** 指标JSON: {"水分":35.5,"油分":28.2,"弹性":0.85} 
*/    
private String metrics;    
/** 关键指标1(冗余索引) 
*/    private BigDecimal primaryMetric;    
/** 关键指标2(冗余索引) 
*/    private BigDecimal secondaryMetric;    
/** 检测图片URL 
*/    
private String imageUrl;    
/** 检测报告URL 
*/    
private String reportUrl;    
/** 数据来源: device/manual/auto 
*/    
private String source;    
/** 采集时间 
*/    private LocalDateTime collectTime;    
/** 设备上报时间 
*/    private LocalDateTime deviceReportTime;    
/** 状态: 0-无效, 1-有效 
*/    private Integer valid;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
