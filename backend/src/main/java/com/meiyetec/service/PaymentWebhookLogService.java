package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.PaymentWebhookLog;
import java.util.List;
public interface PaymentWebhookLogService extends IService<PaymentWebhookLog> {
    List<PaymentWebhookLog> queryList(String gatewayCode, String processStatus, Long tenantId);
    void processWebhook(String gatewayCode, String payload, String signature, String sourceIp);
}