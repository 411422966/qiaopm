package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PaymentWebhookLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentWebhookLogMapper extends BaseMapper<PaymentWebhookLog> {

    @Select("SELECT * FROM biz_payment_webhook_log WHERE deleted = 0 "
            + "AND (#{gatewayCode} IS NULL OR gateway_code = #{gatewayCode}) "
            + "AND (#{processStatus} IS NULL OR process_status = #{processStatus}) "
            + "ORDER BY receive_time DESC LIMIT 200")
    List<PaymentWebhookLog> queryList(@Param("gatewayCode") String gatewayCode,
                                      @Param("processStatus") String processStatus);

    @Select("SELECT * FROM biz_payment_webhook_log WHERE deleted = 0 "
            + "AND gateway_event_id = #{eventId} LIMIT 1")
    PaymentWebhookLog findByEventId(@Param("eventId") String eventId);
}
