package com.meiyetec.mkt.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M16 营销事件发布器
 *
 * <p>业务侧只需注入 {@code MarketingEventPublisher}，调用快捷方法发布事件即可。
 * 监听器自动处理，无需关心下游逻辑。
 *
 * <p>用法：
 * <pre>
 *   publisher.memberRegistered(1L, 123L, "Alice");
 *   publisher.orderPlaced(1L, 123L, "Alice", "ORD001", new BigDecimal("500"));
 *   publisher.bookingCreated(1L, 123L, "Alice", "BK001");
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    /** 通用发布入口 */
    public void publish(MarketingEvent event) {
        if (event == null || event.getType() == null) {
            log.warn("[MarketingEvent] event/type 为空，已忽略");
            return;
        }
        if (event.getTenantId() == null) {
            log.warn("[MarketingEvent] tenantId 缺失，event={}", event.getType());
            return;
        }
        log.debug("[MarketingEvent] 发布: type={} tenant={} member={} amount={}",
                event.getType(), event.getTenantId(), event.getMemberId(), event.getAmount());
        springPublisher.publishEvent(event);
    }

    /** 拼接属性 map */
    private static Map<String, Object> pair(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }
    private static Map<String, Object> pair2(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1); m.put(k2, v2);
        return m;
    }

    // ====================================================================
    // 便捷方法 - 减少业务侧样板代码
    // ====================================================================

    public void memberRegistered(Long tenantId, Long memberId, String memberName) {
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.MEMBER_REGISTER)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .attributes(pair("registrationSource", "store_signup"))
                .build());
    }

    public void orderPlaced(Long tenantId, Long memberId, String memberName,
                              String orderNo, BigDecimal amount) {
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.ORDER_PLACED)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .amount(amount)
                .refNo(orderNo)
                .attributes(pair("orderNo", orderNo))
                .build());
    }

    public void orderPaid(Long tenantId, Long memberId, String memberName,
                            String orderNo, BigDecimal amount) {
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.ORDER_PAID)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .amount(amount)
                .refNo(orderNo)
                .attributes(pair("orderNo", orderNo))
                .build());
    }

    public void consume(Long tenantId, Long memberId, String memberName,
                          String orderNo, BigDecimal amount) {
        Map<String, Object> attr = new HashMap<>();
        attr.put("orderNo", orderNo);
        attr.put("daysSinceLastConsume", 0L);
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.CONSUME)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .amount(amount)
                .refNo(orderNo)
                .attributes(attr)
                .build());
    }

    public void recharge(Long tenantId, Long memberId, String memberName,
                           String refNo, BigDecimal amount) {
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.RECHARGE)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .amount(amount)
                .refNo(refNo)
                .attributes(pair("rechargeAmount", amount))
                .build());
    }

    public void bookingCreated(Long tenantId, Long memberId, String memberName,
                                  String bookingNo, Long storeId) {
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.BOOKING_CREATED)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .refNo(bookingNo)
                .storeId(storeId)
                .attributes(pair("bookingNo", bookingNo))
                .build());
    }

    public void birthday(Long tenantId, Long memberId, String memberName, String birthdayTag) {
        publish(MarketingEvent.builder()
                .type(MarketingEventTypes.BIRTHDAY)
                .tenantId(tenantId)
                .memberId(memberId)
                .memberName(memberName)
                .attributes(pair2("daysFromBirthday", 0, "birthdayTag", birthdayTag))
                .build());
    }
}
