package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P4-02 · QPM-006: 会员生命周期规则
 *
 * 4 大规则类型统一抽象：
 * - BIRTHDAY   生日提醒（T-7/T-0/T+1）
 * - MEMBER_DAY 会员日（每月固定日 / 周年庆）
 * - RETENTION  复购唤醒（RFM AT_RISK / LOST → 进入旅程）
 * - INVITE     裂变邀请（双方奖励）
 */
@Data
@TableName("member_lifecycle_rule")
public class MemberLifecycleRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long brandId;

    /** 规则编码（租户内唯一，如 BIRTHDAY_T7 / MEMBER_DAY_MONTHLY / RETENTION_T30 / INVITE_REWARD） */
    private String ruleCode;

    /** 规则类型：BIRTHDAY / MEMBER_DAY / RETENTION / INVITE */
    private String ruleType;

    private String ruleName;
    private String description;

    /** 触发条件 JSON（不同 ruleType 不同结构） */
    private String triggerConfigJson;

    /** 关联触达模板 ID（mkt_channel_template.id） */
    private Long templateId;

    /** 适用 RFM 分群（CHAMPION/LOYAL/POTENTIAL/AT_RISK/LOST/HIBERNATING/GENERAL），RETENTION 类使用 */
    private String rfmSegment;

    /** 关联旅程 ID（mkt_journey.id），规则可触发会员进入旅程 */
    private Long invokeJourneyId;

    /** 自动发放优惠金额 */
    private BigDecimal invokeCouponAmount;

    /** 自动赠送积分 */
    private Integer invokePoints;

    /** cron 表达式（可选，部分规则使用 Scheduled 默认时间） */
    private String scheduleCron;

    /** 每日最大触发数（限流） */
    private Integer maxPerDay;

    /** 1=启用 0=禁用 */
    private Integer enabled;

    /** DRAFT/ACTIVE/PAUSED/ARCHIVED */
    private String status;

    private Integer priority;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}