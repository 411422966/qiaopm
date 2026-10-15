package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.PaymentRouteRule;
import com.meiyetec.mapper.PaymentRouteRuleMapper;
import com.meiyetec.service.PaymentRouteRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class PaymentRouteRuleServiceImpl extends ServiceImpl<PaymentRouteRuleMapper, PaymentRouteRule>
        implements PaymentRouteRuleService {
    private final PaymentRouteRuleMapper routeRuleMapper;
    @Override
    public List<PaymentRouteRule> findMatchingRules(String currency, String countryCode, String paymentMethod, Long tenantId) {
        return routeRuleMapper.findMatchingRules(currency, countryCode, paymentMethod, tenantId);
    }
}