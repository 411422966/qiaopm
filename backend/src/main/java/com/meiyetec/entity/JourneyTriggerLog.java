package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * M16 触发器执行日志
 *
 * <p>记录每次事件触发后的匹配、入旅程、跳转结果，可追溯。
 * 触发日志与执行结果一一对应：
 * <ul>
 *   <li>MATCHED  — 过滤通过，准备入旅程</li>
 *   <li>ENTERED  — 会员已进入旅程实例</li>
 *   <li>SKIPPED  — 因 dailyLimitPerMember / 幂等原因跳过</li>
 *   <li>FAILED   — 执行异常</li>
 * </ul>
 */
@Data
@TableName("mkt_journey_trigger_log")
public class JourneyTriggerLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long triggerId;
    private String triggerCode;
    private String eventType;
    private String eventId;
    private Long memberId;
    private String memberName;
    private Long journeyId;
    private Long journeyInstanceId;
    /** MATCHED/ENTERED/SKIPPED/FAILED */
    private String status;
    /** 匹配详情（哪条 filter 通过 / 跳过原因） */
    private String matchDetail;
    /** 业务事件载荷（脱敏后） */
    private String eventPayload;
    /** 处理延迟（事件 → 执行） ms */
    private Long latencyMs;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
