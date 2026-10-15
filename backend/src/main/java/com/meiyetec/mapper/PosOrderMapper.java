package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PosOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PosOrderMapper extends BaseMapper<PosOrder> {

    /**
     * B-3 / B-11：CAS 守卫 - 仅在 refunded_amount == expected 时累加退款金额
     *
     * <p>并发场景：两个并发 process() 同时读到的 refunded_amount 相同，
     * 各自 + 自身 refund amount，普通 updateById 会出现"两次都成功"导致累计超退。
     *
     * <p>本方法原子守卫：expected 匹配 → 累加；不匹配 → 影响 0 行，
     * <p>调用方应识别返回 0 后抛出 CONCURRENCY_CONFLICT 让 @Transactional 回滚。
     *
     * @param orderId            订单 ID
     * @param expectedRefunded   调用方从数据库读到的 refunded_amount 快照
     * @param refundDelta        本次新增的退款金额（正数）
     * @return 1=累加成功；0=条件不匹配（refunded_amount 已被其他事务修改）
     */
    @Update("UPDATE pos_order " +
            "SET refunded_amount = COALESCE(refunded_amount, 0) + #{refundDelta}, " +
            "    refund_count    = COALESCE(refund_count, 0) + 1, " +
            "    refund_amount   = COALESCE(refund_amount, 0) + #{refundDelta} " +
            "WHERE id = #{orderId} " +
            "  AND COALESCE(refunded_amount, 0) = #{expectedRefunded} " +
            "  AND deleted = 0")
    int casAddRefundAmount(@Param("orderId") Long orderId,
                           @Param("expectedRefunded") java.math.BigDecimal expectedRefunded,
                           @Param("refundDelta") java.math.BigDecimal refundDelta);
}