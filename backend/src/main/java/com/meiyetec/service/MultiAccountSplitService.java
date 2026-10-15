package com.meiyetec.service;

import com.meiyetec.entity.PosOrder;

import java.util.Map;

/**
 * M02-04 多账户分账服务
 * <p>订单支付成功后调用，将订单实付金额按规则拆分到 provider / branch / employee 三方子钱包。
 */
public interface MultiAccountSplitService {

    /**
     * 异步触发（订单支付 AFTER_COMMIT 时调用，事务外跑）
     * <p>失败 → 写 m02_split_record.status=FAILED；不抛异常，不影响主链路。
     */
    void splitOrderAsync(PosOrder order);

    /**
     * 同步分账（手动触发/补单用）
     * @return 分账结果 + 明细列表
     */
    Map<String, Object> splitOrderSync(PosOrder order, Long operatorId, String operatorName);
}
