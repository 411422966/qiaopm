package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 效期预警看板聚合数据（每日统计） 
*/
@Data
@TableName("biz_expiry_dashboard_cache")
public class ExpiryDashboardCache {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 统计日期 
*/    
private String statDate;    
/** 已过期批次数量 
*/    private Integer expiredCount;    
/** 严重预警批次数量(7天内过期) 
*/    private Integer criticalCount;    
/** 警告批次数量(30天内过期) 
*/    private Integer warningCount;    
/** 正常批次数量 
*/    private Integer normalCount;    
/** 临期批次总价值 
*/    private java.math.BigDecimal nearExpiryValue;    
/** 已过期批次价值 
*/    private java.math.BigDecimal expiredValue;    
/** 待处理预警数量 
*/    private Integer pendingAlertCount;    
/** 累计损失金额 
*/    private java.math.BigDecimal lossAmount;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;}
