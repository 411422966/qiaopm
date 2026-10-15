package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 护理建议记录
 * 为客户生成的护理建议发送记录 
*/
@Data
@TableName("biz_care_suggestion_log")
public class CareSuggestionLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 客户ID 
*/    private Long memberId;    
/** 客户姓名 
*/    
private String memberName;    
/** 命中的规则ID 
*/    private Long ruleId;    
/** 规则名称 
*/    
private String ruleName;    
/** 关联模板ID 
*/    private Long templateId;    
/** 建议标题 
*/    
private String title;    
/** 建议内容 
*/    
private String content;    
/** 推荐服务JSON 
*/    
private String recommendedServices;    
/** 推荐产品JSON 
*/    
private String recommendedProducts;    
/** 触发条件描述 
*/    
private String triggerReason;    
/** 触发的客户标签 
*/    
private String matchedTags;    
/** 触发的肤质类型 
*/    
private String skinType;    
/** 建议类型: auto(自动)/manual(手动) 
*/    
private String suggestionType;    
/** 状态: pending(待发送)/sent(已发送)/viewed(已查看)/accepted(已采纳)/rejected(已拒绝)/expired(已过期) 
*/    
private String status;    
/** 发送渠道: sms/wechat/system(系统) 
*/    
private String sendChannel;    
/** 发送时间 
*/    private LocalDateTime sendTime;    
/** 查看时间 
*/    private LocalDateTime viewTime;    
/** 采纳时间 
*/    private LocalDateTime acceptTime;    
/** 操作人(手动时) 
*/    private Long operatorId;    
/** 操作人姓名 
*/    
private String operatorName;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
