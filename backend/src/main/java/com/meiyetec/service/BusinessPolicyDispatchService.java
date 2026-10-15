package com.meiyetec.service;

import com.meiyetec.entity.MktDispatch;

import java.util.List;
import java.util.Map;

/**
 * M12-05 业务策略下发服务
 *
 * <p>支持下发类型：
 * <ul>
 *   <li>PRICING_RULE — 动态定价规则</li>
 *   <li>ACTIVITY — 营销活动</li>
 *   <li>COUPON_BATCH — 优惠券批次</li>
 *   <li>EXPERIMENT — A/B 实验</li>
 * </ul>
 */
public interface BusinessPolicyDispatchService {

    /**
     * 创建下发单（DRAFT）
     */
    Long createDispatch(MktDispatch dispatch, List<Long> targetStoreIds);

    /**
     * 同步执行下发：写 mkt_dispatch + m12_dispatch_log × N 门店 + 业务落地
     * @return 执行结果（含 success/fail/totalCount）
     */
    Map<String, Object> publishAndDispatch(Long dispatchId, Long operatorId, String operatorName);

    /**
     * 异步执行（长任务：超 50 个门店时自动切异步）
     */
    void publishAndDispatchAsync(Long dispatchId, Long operatorId, String operatorName);

    /**
     * 取消未执行的 DRAFT/PUBLISHED 状态 dispatch
     */
    boolean cancelDispatch(Long dispatchId, Long operatorId, String operatorName);
}
