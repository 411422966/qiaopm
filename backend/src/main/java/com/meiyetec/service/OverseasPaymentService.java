package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.OverseasPayment;
import com.meiyetec.payment.OverseasPaymentAdapter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public interface OverseasPaymentService extends IService<OverseasPayment> {
    List<OverseasPayment> queryList(Long orderId, Long memberId, String gatewayCode,
                                    String status, LocalDateTime startTime, LocalDateTime endTime, Long tenantId);

    /**
     * 智能选择最优网关
     */
    String selectBestGateway(String currency, String countryCode, String paymentMethod, BigDecimal amount, Long tenantId);

    /**
     * 创建支付
     */
    OverseasPayment createPayment(String orderType, Long orderId, String orderNo,
                                  Long memberId, String memberName, String memberEmail,
                                  BigDecimal amount, String currency, String paymentMethod, Long tenantId);

    /**
     * 退款
     */
    OverseasPayment refund(String paymentId, BigDecimal amount, String reason, Long tenantId);

    Map<String, Object> statistics(LocalDateTime startTime, LocalDateTime endTime, Long tenantId);
}