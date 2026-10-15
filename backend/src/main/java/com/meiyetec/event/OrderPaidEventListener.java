package com.meiyetec.event;

import com.meiyetec.entity.PosOrder;
import com.meiyetec.service.MultiAccountSplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * R2.0-D T-D02 订单支付成功事件监听器（最小修复）。
 *
 * <p>之前 OrderPaidEvent 没有任何消费者，导致报表/统计/外部通知全断。
 * 本类先做一个最小的「日志聚合 + 消费链路验证」监听器，让事件不再孤儿。</p>
 *
 * <p>阶段一：仅日志 + 计数（当前实现）。<br>
 * 阶段二：报表聚合（report_metric_snapshot）。<br>
 * 阶段三：营销触点（微信/短信通知）。<br>
 * M02-04：多账户分账（自动触发）。</p>
 *
 * <p>用 {@code @TransactionalEventListener(AFTER_COMMIT)}：订单支付事务 commit 后才触发，
 * 若库存扣减失败回滚则事件不会被消费，保证业务一致性。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

    private final MultiAccountSplitService splitService;

    /**
     * 兜底日志：兜住任何忘记 @TransactionalEventListener 注解的发布点，
     * 真正触发的异步逻辑在 {@link #onOrderPaidAfterCommit} 中。
     */
    @EventListener
    public void onOrderPaidAnyPhase(OrderPaidEvent event) {
        if (event == null || event.getOrder() == null) return;
        PosOrder o = event.getOrder();
        log.debug("[OrderPaidEvent] detected orderId={} orderNo={} totalAmount={} tenantId={}",
                o.getId(), o.getOrderNo(), o.getTotalAmount(), o.getTenantId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaidAfterCommit(OrderPaidEvent event) {
        if (event == null || event.getOrder() == null) return;
        PosOrder order = event.getOrder();
        log.info("[OrderPaidEvent] AFTER_COMMIT orderId={} orderNo={} storeId={} totalAmount={}",
                order.getId(), order.getOrderNo(), order.getStoreId(), order.getTotalAmount());

        // M02-04：多账户分账（自动触发，幂等）
        try {
            splitService.splitOrderAsync(order);
        } catch (Exception e) {
            log.error("[M02-04] auto split trigger failed: orderId={} msg={}", order.getId(), e.getMessage());
        }

        // TODO 阶段二：写 report_metric_snapshot（日报）
        // TODO 阶段三：触发营销触点（核销提醒 / 评价邀请）
    }
}
