package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.PaymentRouteRule;
import java.util.List;
public interface PaymentRouteRuleService extends IService<PaymentRouteRule> {
    List<PaymentRouteRule> findMatchingRules(String currency, String countryCode, String paymentMethod, Long tenantId);
}