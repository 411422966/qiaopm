package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 效期预警规则
 * 配置哪些耗材需要效期追踪、预警阈值 
*/
@Data
@TableName("biz_expiry_alert_rule")
public class ExpiryAlertRule {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 规则名称 
*/    
private String ruleName;    
/** 适用耗材分类 
*/    
private String category;    
/** 适用耗材ID(可选) 
*/    private Long productId;    
/** 耗材名称(冗余字段) 
*/    
@TableField(exist = false)    
private String productName;    
/** 严重预警阈值(天) - 距离过期X天内红色预警 
*/    private Integer criticalDays;    
/** 普通预警阈值(天) - 距离过期X天内黄色预警 
*/    private Integer warningDays;    
/** 预警接收人(JSON 数组) 
*/    
private String recipients;    
/** 预警渠道: sms/wechat/system/all 
*/    
private String notifyChannels;    
/** 是否自动处理: 0-否, 1-是 
*/    private Integer autoHandle;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
