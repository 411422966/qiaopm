package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PaymentRouteRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentRouteRuleMapper extends BaseMapper<PaymentRouteRule> {

    @Select("SELECT * FROM biz_payment_route_rule "
            + "WHERE deleted = 0 AND status = 1 "
            + "AND (#{currency} IS NULL OR currency = #{currency}) "
            + "AND (#{countryCode} IS NULL OR country_code = #{countryCode}) "
            + "AND (#{paymentMethod} IS NULL OR payment_method = #{paymentMethod}) "
            + "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) "
            + "ORDER BY priority DESC")
    List<PaymentRouteRule> findMatchingRules(@Param("currency") String currency,
                                             @Param("countryCode") String countryCode,
                                             @Param("paymentMethod") String paymentMethod,
                                             @Param("tenantId") Long tenantId);
}