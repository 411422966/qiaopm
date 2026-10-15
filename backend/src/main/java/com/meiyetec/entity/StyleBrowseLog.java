package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 款式浏览收藏日志
 * 跟踪客户款式浏览/收藏行为，用于AI推荐 
*/
@Data
@TableName("biz_style_browse_log")
public class StyleBrowseLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 客户ID 
*/    private Long memberId;    
/** 款式ID 
*/    private Long styleId;    
/** 行为类型: view(浏览)/favorite(收藏)/share(分享)/order(下单) 
*/    
private String actionType;    
/** 行为来源: app/wechat/miniprogram/store 
*/    
private String source;    
/** 停留时长(秒) 
*/    private Integer dwellTime;    
/** 会话ID 
*/    
private String sessionId;    
/** 行为时间 
*/    private LocalDateTime actionTime;    
private String ipAddress;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
