package com.meiyetec.mkt.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M16 通用营销事件
 *
 * <p>所有业务触点的统一事件载荷，通过 {@link MarketingEventPublisher} 发布，
 * 由 {@code JourneyTriggerListener} 监听并按规则匹配触发旅程。
 *
 * <p>使用 builder 模式构造：
 * <pre>
 * MarketingEvent event = MarketingEvent.builder()
 *     .type(MarketingEventTypes.ORDER_PLACED)
 *     .tenantId(1L).memberId(123L).memberName("Alice")
 *     .amount(new BigDecimal("500"))
 *     .attribute("orderNo", "ORD202609040001")
 *     .attribute("storeId", 5L)
 *     .build();
 * </pre>
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class MarketingEvent {

    /** 事件类型（必填） */
    private String type;

    /** 租户 ID */
    private Long tenantId;

    /** 会员 ID（核心匹配键） */
    private Long memberId;

    /** 会员姓名（冗余，加快日志查询） */
    private String memberName;

    /** 事件唯一 ID（幂等：同 eventId 不重复触发） */
    @lombok.Builder.Default
    private String eventId = UUID.randomUUID().toString().replace("-", "");

    /** 业务时间（事件实际发生时间） */
    @lombok.Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    /** 事件金额（充值 / 下单 等） */
    private BigDecimal amount;

    /** 触发的会员标签 / 等级 */
    private String memberTag;
    private String memberLevel;

    /** 关联订单号 / 预约号 等 */
    private String refNo;
    /** 关联门店 */
    private Long storeId;

    /** 扩展属性（如订单类型、渠道、来源等） */
    @lombok.Builder.Default
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /** 用 builder 模式给扩展属性赋值（注意：避免与 Lombok @Builder 冲突） */
    public MarketingEvent putAttr(String key, Object value) {
        if (attributes == null) attributes = new LinkedHashMap<>();
        attributes.put(key, value);
        return this;
    }

    public Object attr(String key) {
        return attributes == null ? null : attributes.get(key);
    }

    public String attrAsString(String key) {
        Object v = attr(key);
        return v == null ? null : v.toString();
    }

    public Long attrAsLong(String key) {
        Object v = attr(key);
        return v == null ? null : (v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString()));
    }
}
