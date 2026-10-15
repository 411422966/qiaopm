package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.mapper.OverseasPaymentGatewayMapper;
import com.meiyetec.payment.OverseasPaymentAdapter;
import com.meiyetec.payment.PaymentGatewayRouter;
import com.meiyetec.service.OverseasPaymentGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OverseasPaymentGatewayServiceImpl extends ServiceImpl<OverseasPaymentGatewayMapper, OverseasPaymentGateway>
        implements OverseasPaymentGatewayService {

    private final OverseasPaymentGatewayMapper gatewayMapper;
    private final PaymentGatewayRouter router;

    @Override
    public List<OverseasPaymentGateway> queryList(String gatewayCode, Integer status, String environment, Long tenantId) {
        return gatewayMapper.queryList(gatewayCode, status, environment, tenantId);
    }

    @Override
    public OverseasPaymentGateway findEnabledByCode(String gatewayCode) {
        return gatewayMapper.findEnabledByCode(gatewayCode);
    }

    @Override
    public OverseasPaymentAdapter.HealthStatus healthCheck(Long gatewayId) {
        OverseasPaymentGateway gateway = getById(gatewayId);
        if (gateway == null) {
            OverseasPaymentAdapter.HealthStatus status = new OverseasPaymentAdapter.HealthStatus();
            status.healthy = false;
            status.message = "Gateway not found";
            return status;
        }
        OverseasPaymentAdapter adapter = router.getAdapter(gateway.getGatewayCode());
        OverseasPaymentAdapter.HealthStatus status = adapter.healthCheck(gateway);
        // 更新网关状态
        gateway.setHealthStatus(status.healthy ? "healthy" : "unhealthy");
        gateway.setLastHealthCheck(LocalDateTime.now());
        updateById(gateway);
        return status;
    }
}
