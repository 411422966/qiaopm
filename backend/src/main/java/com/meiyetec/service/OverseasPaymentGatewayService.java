package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.payment.OverseasPaymentAdapter;

import java.util.List;

public interface OverseasPaymentGatewayService extends IService<OverseasPaymentGateway> {

    List<OverseasPaymentGateway> queryList(String gatewayCode, Integer status, String environment, Long tenantId);

    OverseasPaymentGateway findEnabledByCode(String gatewayCode);

    /**
     * 健康检查
     */
    OverseasPaymentAdapter.HealthStatus healthCheck(Long gatewayId);
}