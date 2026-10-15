package com.meiyetec.service;

import com.meiyetec.util.Result;

import java.util.Map;

/**
 * B-2 · 套餐核销服务。
 *
 * <p>职责：POS 收银台套餐支付（method="package"）的核销入口。
 * <ul>
 *   <li>幂等：同一 orderId+packageOrderId 组合只能核销一次（走 IdempotencyService）</li>
 *   <li>CAS：使用 PackageOrder.version 乐观锁，防止并发扣减</li>
 *   <li>原子写：扣次数 + 写核销记录在一个事务内</li>
 * </ul>
 *
 * <p>所有调用必须通过 {@link IdempotencyService#execute} 包装幂等键。
 */
public interface PackageRedeemService {

    /**
     * 套餐核销（幂等 CAS 版）。
     *
     * @param orderId        关联的 POS 订单 ID
     * @param packageOrderId 套餐购买记录 ID（crm_package_order.id）
     * @param times          本次核销次数（通常为 1）
     * @param tenantId       租户 ID
     * @param operatorId     操作人 ID（收银员）
     * @param storeId        当前核销门店 ID
     * @return 核销结果，含 packageRedemptionId / remainingTimes
     */
    Result<Map<String, Object>> redeem(Long orderId, Long packageOrderId,
                                      Integer times, Long tenantId,
                                      Long operatorId, Long storeId);
}
