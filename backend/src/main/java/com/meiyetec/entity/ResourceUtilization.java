package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 资源利用率统计
 * 房间/技师的每日利用率统计 
*/
@Data
@TableName("biz_resource_utilization")
public class ResourceUtilization {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 资源类型: room/technician 
*/    
private String resourceType;    
/** 资源ID 
*/    private Long resourceId;    
/** 资源名称 
*/    
private String resourceName;    
/** 统计日期 
*/    private LocalDate statDate;    
/** 总可用时长(分钟) 
*/    private Integer totalAvailableMinutes;    
/** 已预约时长(分钟) 
*/    private Integer bookedMinutes;    
/** 实际服务时长(分钟) 
*/    private Integer actualServiceMinutes;    
/** 空闲时长(分钟) 
*/    private Integer idleMinutes;    
/** 利用率(0-100) 
*/    private java.math.BigDecimal utilizationRate;    
/** 订单数 
*/    private Integer orderCount;    
/** 取消数 
*/    private Integer cancelCount;    
/** 爽约数 
*/    private Integer noShowCount;    
/** 收入(元) 
*/    private java.math.BigDecimal revenue;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
