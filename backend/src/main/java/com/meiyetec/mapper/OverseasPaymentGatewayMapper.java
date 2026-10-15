package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.OverseasPaymentGateway;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OverseasPaymentGatewayMapper extends BaseMapper<OverseasPaymentGateway> {

    @Select("SELECT * FROM biz_overseas_payment_gateway "
            + "WHERE deleted = 0 "
            + "AND (#{gatewayCode} IS NULL OR gateway_code = #{gatewayCode}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{environment} IS NULL OR environment = #{environment}) "
            + "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) "
            + "ORDER BY create_time DESC")
    List<OverseasPaymentGateway> queryList(@Param("gatewayCode") String gatewayCode,
                                           @Param("status") Integer status,
                                           @Param("environment") String environment,
                                           @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM biz_overseas_payment_gateway WHERE deleted = 0 "
            + "AND gateway_code = #{gatewayCode} AND status = 1 LIMIT 1")
    OverseasPaymentGateway findEnabledByCode(@Param("gatewayCode") String gatewayCode);

    @Select("SELECT * FROM biz_overseas_payment_gateway WHERE deleted = 0 AND status = 1 ORDER BY create_time DESC")
    List<OverseasPaymentGateway> findEnabledGateways();
}
