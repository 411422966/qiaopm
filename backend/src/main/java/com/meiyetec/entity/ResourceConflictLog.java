package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 资源冲突日志
 * 记录排程中的房间/技师冲突事件 
*/
@Data
@TableName("biz_resource_conflict_log")
public class ResourceConflictLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 冲突类型: room_conflict/technician_conflict/both 
*/    
private String conflictType;    
/** 资源类型: room/technician 
*/    
private String resourceType;    
/** 资源ID 
*/    private Long resourceId;    
/** 资源名称 
*/    
private String resourceName;    
/** 新排程ID 
*/    private Long newScheduleId;    
/** 已有排程ID 
*/    private Long existingScheduleId;    
/** 冲突开始时间 
*/    private LocalDateTime conflictStart;    
/** 冲突结束时间 
*/    private LocalDateTime conflictEnd;    
/** 处理方式: auto_replace/manual_resolve/allow_overlap/cancel 
*/    
private String resolveAction;    
/** 处理人ID 
*/    private Long resolveBy;    
/** 处理时间 
*/    private LocalDateTime resolveTime;    
/** 检测时间 
*/    private LocalDateTime detectTime;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
