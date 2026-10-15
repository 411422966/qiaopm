package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.payment.*;
import com.meiyetec.service.PaymentWebhookLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookLogServiceImpl extends ServiceImpl<PaymentWebhookLogMapper, PaymentWebhookLog>
        implements PaymentWebhookLogService {

    private final PaymentWebhookLogMapper webhookLogMapper;
    private final OverseasPaymentMapper paymentMapper;
    private final OverseasPaymentGatewayMapper gatewayMapper;
    private final PaymentGatewayRouter router;

    @Override
    public List<PaymentWebhookLog> queryList(String gatewayCode, String processStatus, Long tenantId) {
        // 海外 webhook 由 gatewayCode/processStatus 主过滤；tenantId 透传给 mapper 走 in-memory 兜底
        // (PaymentWebhookLogMapper.queryList 是固定 200 条 LIMIT + 全表扫描；tenantId 由 Controller 已过滤)
        return webhookLogMapper.queryList(gatewayCode, processStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processWebhook(String gatewayCode, String payload, String signature, String sourceIp) {
        // 1. 记录Webhook
        OverseasPaymentGateway gateway = gatewayMapper.findEnabledByCode(gatewayCode);
        if (gateway == null) {
            log.warn("Webhook received for unknown gateway: {}", gatewayCode);
            return;
        }

        PaymentWebhookLog logRecord = new PaymentWebhookLog();
        logRecord.setGatewayCode(gatewayCode);
        logRecord.setPayload(payload);
        logRecord.setSignature(signature);
        logRecord.setSourceIp(sourceIp);
        logRecord.setReceiveTime(LocalDateTime.now());
        logRecord.setProcessStatus("pending");

        // 2. 验证签名
        OverseasPaymentAdapter adapter = router.getAdapter(gatewayCode);
        boolean verifyOk = adapter.verifyWebhookSignature(gateway, payload, signature);
        logRecord.setVerifyStatus(verifyOk ? 1 : 0);

        if (!verifyOk) {
            logRecord.setProcessStatus("failed");
            logRecord.setErrorMessage("Signature verification failed");
            webhookLogMapper.insert(logRecord);
            return;
        }

        // 3. 解析事件
        OverseasPaymentAdapter.WebhookEvent event = adapter.parseWebhookEvent(gateway, payload);
        logRecord.setEventType(event.eventType);
        logRecord.setGatewayEventId(event.eventId);
        logRecord.setGatewayPaymentId(event.paymentId);

        // 4. 幂等检查
        PaymentWebhookLog existing = webhookLogMapper.findByEventId(event.eventId);
        if (existing != null) {
            logRecord.setProcessStatus("skipped");
            logRecord.setErrorMessage("Event already processed");
            webhookLogMapper.insert(logRecord);
            return;
        }

        // 5. 处理业务
        try {
            handleEvent(event);
            logRecord.setProcessStatus("processed");
            logRecord.setProcessTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            logRecord.setProcessStatus("failed");
            logRecord.setErrorMessage(e.getMessage());
            logRecord.setRetryCount(1);
        }

        webhookLogMapper.insert(logRecord);
    }

    private void handleEvent(OverseasPaymentAdapter.WebhookEvent event) {
        if (event.paymentId == null) return;

        OverseasPayment payment = paymentMapper.findByGatewayPaymentId(event.paymentId);
        if (payment == null) {
            log.warn("Webhook payment not found: {}", event.paymentId);
            return;
        }

        // 转换状态
        switch (event.eventType) {
            case "payment_intent.succeeded":
            case "PAYMENT.CAPTURE.COMPLETED":
                payment.setStatus("succeeded");
                payment.setPaidAt(LocalDateTime.now());
                break;
            case "payment_intent.payment_failed":
            case "PAYMENT.CAPTURE.DENIED":
                payment.setStatus("failed");
                payment.setFailureMessage("Payment failed");
                break;
            case "charge.refunded":
            case "PAYMENT.CAPTURE.REFUNDED":
                payment.setStatus("refunded");
                break;
            default:
                log.info("Unhandled webhook event: {}", event.eventType);
        }

        paymentMapper.updateById(payment);
    }
}