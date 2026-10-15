package com.meiyetec.event;

import com.meiyetec.entity.PosOrder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * R2.0-D T-D02 扩展点：订单支付成功事件。
 *
 * <p>当前实装保留 <b>同步</b> 库存消耗以保证"订单与库存同生共死"的业务正确性。
 * 本事件仅作为报表 / 统计 / 异步通知的扩展点使用。</p>
 *
 * <p>listeners 应使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * 监听，事务回滚则事件不会被消费。</p>
 */
@Getter
public class OrderPaidEvent extends ApplicationEvent {

    private final PosOrder order;

    public OrderPaidEvent(Object source, PosOrder order) {
        super(source);
        this.order = order;
    }
}