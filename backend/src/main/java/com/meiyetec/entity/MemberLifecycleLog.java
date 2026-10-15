package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * P4-02 · QPM-006: 会员生命周期触发日志
 *
 * 统一记录 4 大规则触发的执行结果。
 * uk_rule_member_date 唯一索引保证：同一规则对同一会员在同一 trigger_date 不重复触发。
 */
@Data
@TableName("member_lifecycle_log")
public class MemberLifecycleLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long ruleId;
    private String ruleCode;
    private String ruleType;

    private Long memberId;
    private String memberName;
    private String memberPhone;

    /** 触发日期（同一天内同规则同人不会重复） */
    private LocalDate triggerDate;

    private String triggerReason;

    /** 触达渠道：SMS / WECHAT / MINI_PROGRAM / EMAIL / PUSH */
    private String channel;

    private Long templateId;
    private String templateContent;

    private String couponCode;
    private Integer pointsAwarded;
    private String giftValue;

    /** 关联旅程实例 ID（RETENTION 类规则使用） */
    private Long journeyInstanceId;

    /** PENDING/SENT/FAILED/USED/EXPIRED */
    private String status;

    private String errorMessage;
    private String externalMsgId;
    private LocalDateTime convertedAt;
    private Long relatedOrderId;

    private LocalDateTime sentAt;
    private LocalDateTime usedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}