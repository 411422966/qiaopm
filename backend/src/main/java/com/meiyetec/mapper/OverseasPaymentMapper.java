package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.OverseasPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OverseasPaymentMapper extends BaseMapper<OverseasPayment> {

    @Select("SELECT * FROM biz_overseas_payment "
            + "WHERE deleted = 0 "
            + "AND (#{orderId} IS NULL OR order_id = #{orderId}) "
            + "AND (#{memberId} IS NULL OR member_id = #{memberId}) "
            + "AND (#{gatewayCode} IS NULL OR gateway_code = #{gatewayCode}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{startTime} IS NULL OR create_time >= #{startTime}) "
            + "AND (#{endTime} IS NULL OR create_time <= #{endTime}) "
            + "ORDER BY create_time DESC")
    List<OverseasPayment> queryList(@Param("orderId") Long orderId,
                                    @Param("memberId") Long memberId,
                                    @Param("gatewayCode") String gatewayCode,
                                    @Param("status") String status,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM biz_overseas_payment WHERE deleted = 0 AND gateway_payment_id = #{paymentId} LIMIT 1")
    OverseasPayment findByGatewayPaymentId(@Param("paymentId") String paymentId);

    @Select("SELECT * FROM biz_overseas_payment WHERE deleted = 0 AND gateway_intent_id = #{intentId} LIMIT 1")
    OverseasPayment findByIntentId(@Param("intentId") String intentId);

    @Select("SELECT gateway_code AS gatewayCode, status, "
            + "COUNT(*) AS count, "
            + "SUM(amount) AS total_amount, "
            + "SUM(fee) AS total_fee, "
            + "SUM(net_amount) AS total_net "
            + "FROM biz_overseas_payment "
            + "WHERE deleted = 0 AND create_time BETWEEN #{startTime} AND #{endTime} "
            + "GROUP BY gateway_code, status")
    List<Map<String, Object>> statistics(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);
}
