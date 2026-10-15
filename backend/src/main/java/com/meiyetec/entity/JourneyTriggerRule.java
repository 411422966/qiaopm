package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * M16 营销旅程事件触发器规则
 *
 * <p>取代硬编码 triggerEvent 字段，提供可配置的、事件驱动的旅程自动触发规则。
 * 每个规则定义：
 * <ul>
 *   <li>eventType — 监听哪种业务事件</li>
 *   <li>filterConditions — JSON 过滤（满足哪些条件才触发）</li>
 *   <li>journeyId — 触发后让会员进入哪个旅程</li>
 *   <li>delayMinutes / delayCron — 延时执行（防过急触达）</li>
 *   <li>rateLimit — 每会员每天最多触发 1 次（deduplication）</li>
 * </ul>
 */
@Data
@TableName("mkt_journey_trigger")
public class JourneyTriggerRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;

    /** 触发器编码（全局唯一） */
    private String triggerCode;
    /** 触发器名称（运营可读） */
    private String triggerName;
    /** 业务事件类型，见 {@link com.meiyetec.mkt.event.MarketingEventTypes} */
    private String eventType;

    /** 过滤条件 JSON，如 {"minOrderAmount":500,"memberTag":"VIP","storeIds":[1,2]} */
    private String filterConditions;
    /** 关联的旅程 ID（触发后让会员进入该旅程） */
    private Long journeyId;
    /** 延时触发（分钟），0=立即触发 */
    private Integer delayMinutes;
    /** 延时时间窗口（小时），用于批量执行归集，如"+5"=在事件发生5小时后合并触发 */
    private Integer delayWindowHours;

    /** 每天每会员最多触发 1 次（防骚扰） */
    private Integer dailyLimitPerMember;
    /** 每个事件最多触发多少会员（防爆） */
    private Integer batchLimit;

    /** 1草稿 2启用 3暂停 */
    private Integer status;
    /** 优先级，数字越小越优先 */
    private Integer priority;

    /** 统计 */
    private Integer matchCount;
    private Integer enterCount;
    private LocalDateTime lastTriggerAt;

    private String description;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
