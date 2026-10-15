package com.meiyetec.cn.domestic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.PaymentWebhookLog;
import com.meiyetec.mapper.PaymentWebhookLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 国内支付 webhook 日志与处理服务（与海外复用同一张表 biz_payment_webhook_log）
 *
 * <p>行为契约（用户拍板 — P0）：
 * <ul>
 *   <li>**全量入库**：任何 webhook 一律先写库（即使验签失败），processStatus=pending / verifyStatus=0</li>
 *   <li>**失败重试 3 次**：处理失败时 retryCount++，本地间隔退避重试 3 次</li>
 *   <li>**不降级**：失败不切路由，保留失败记录供人工排查（与海外 webhook 行为不同）</li>
 *   <li>**MDC 链路追踪**：traceId 写入 log 表，关联上游日志</li>
 * </ul>
 *
 * @since 2026-09-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticPayWebhookService {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final int MAX_RETRY = 3;

    private final PaymentWebhookLogMapper webhookLogMapper;
    private final DomesticPayChannelRouter channelRouter;

    /**
     * 入口：接收 + 入库 + 立即尝试处理（processWebhook 同步重试 3 次）
     */
    @Transactional(rollbackFor = Exception.class)
    public DomesticPayResponse<String> handle(String channelCode, String rawBody,
                                               Map<String, String> headers, String sourceIp) {
        String traceId = MDC.get("traceId");
        if (traceId == null) traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 0) 幂等检查：biz_payment_webhook_log.uk_event_id 在同一网关事件下唯一
        //    微信/支付宝/银联都可能重发同一事件，避免重复入库 + 重复处理
        String preEventId = extractEventId(channelCode, headers, rawBody);
        PaymentWebhookLog existing = preEventId == null ? null
                : webhookLogMapper.findByEventId(preEventId);
        if (existing != null) {
            log.warn("[CN-DOMESTIC-WEBHOOK] replay detected: channel={} eventId={} previousId={} processStatus={}",
                    channelCode, preEventId, existing.getId(), existing.getProcessStatus());
            return DomesticPayResponse.<String>builder()
                    .ok(true).channelCode(channelCode)
                    .outOrderNo(existing.getGatewayPaymentId())
                    .payload(String.format("replay=true, previousLogId=%d, previousProcessStatus=%s",
                            existing.getId(), existing.getProcessStatus()))
                    .build();
        }

        // 1) 入库（不论验签/解析成败，先记录原始 payload）
        PaymentWebhookLog logRow = new PaymentWebhookLog();
        logRow.setTenantId(null);                          // 国内支付 webhook 不按租户聚合，由 orderNo 反查
        logRow.setGatewayCode(channelCode);                // WECHAT_PAY / ALIPAY
        logRow.setEventType("WEBHOOK_RECEIVED");
        logRow.setGatewayEventId(preEventId);
        logRow.setGatewayPaymentId(headers.get("out_trade_no"));
        logRow.setPayload(rawBody);
        logRow.setSignature(headers.get("Wechatpay-Signature"));
        logRow.setVerifyStatus(0);                          // 默认 0，由 processWebhook 改写
        logRow.setProcessStatus("pending");
        logRow.setProcessTime(LocalDateTime.now());
        logRow.setRetryCount(0);
        logRow.setReceiveTime(LocalDateTime.now());
        logRow.setSourceIp(sourceIp);
        try {
            webhookLogMapper.insert(logRow);
        } catch (DuplicateKeyException dupEx) {
            // 兜底幂等：并发场景下两个 webhook 同时进入，第二个会撞唯一键
            log.warn("[CN-DOMESTIC-WEBHOOK] duplicate event caught: channel={} eventId={}",
                    channelCode, preEventId);
            return DomesticPayResponse.<String>builder()
                    .ok(true).channelCode(channelCode)
                    .payload("replay=true, race-condition-handled")
                    .build();
        }

        // 2) 立即处理 + 失败重试 3 次
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            logRow.setRetryCount(attempt - 1);
            try {
                DomesticPayChannelAdapter adapter = channelRouter.getAdapter(channelCode);
                DomesticPayChannelAdapter.NotifyResult notify = adapter.parseAndVerifyNotify(rawBody, headers);
                logRow.setVerifyStatus(notify.isSignatureValid() ? 1 : 0);
                logRow.setEventType(notify.getEventType());
                logRow.setGatewayPaymentId(notify.getOutOrderNo());
                // gatewayEventId 保持与入库时一致（preEventId）—— 不要用 notify.getNotifyId() 覆盖，
                // 否则 webhook 幂等（uk_event_id）会因每次都不同而失效
                // notify.getTransactionId() 写到 errorMessage / payload 备注，供业务追溯
                if (notify.getTransactionId() != null) {
                    String remark = logRow.getErrorMessage() == null ? "" : logRow.getErrorMessage() + " | ";
                    logRow.setErrorMessage(remark + "txnId=" + notify.getTransactionId());
                }

                if (!notify.isSignatureValid()) {
                    logRow.setProcessStatus("skipped");
                    logRow.setErrorMessage("SignatureInvalid: " + notify.getErrorMsg());
                    webhookLogMapper.updateById(logRow);
                    log.warn("[CN-DOMESTIC-WEBHOOK] sig invalid: channel={} traceId={} reason={}",
                            channelCode, traceId, notify.getErrorMsg());
                    return DomesticPayResponse.<String>builder()
                            .ok(false).channelCode(channelCode)
                            .errorCode("SIGNATURE_INVALID").errorMessage(notify.getErrorMsg())
                            .build();
                }

                // 业务处理：当前仅记录日志，二期接订单状态机
                log.info("[CN-DOMESTIC-WEBHOOK] handled: channel={} traceId={} outOrderNo={} event={} txnId={}",
                        channelCode, traceId, notify.getOutOrderNo(), notify.getEventType(), notify.getTransactionId());

                logRow.setProcessStatus("processed");
                logRow.setProcessTime(LocalDateTime.now());
                webhookLogMapper.updateById(logRow);

                return DomesticPayResponse.<String>builder()
                        .ok(true).channelCode(channelCode)
                        .outOrderNo(notify.getOutOrderNo())
                        .payload(String.format("event=%s, transactionId=%s",
                                notify.getEventType(), notify.getTransactionId()))
                        .build();

            } catch (Exception e) {
                logRow.setErrorMessage(truncate("Attempt " + attempt + " failed: " + e.getMessage(), 1000));
                if (attempt < MAX_RETRY) {
                    long backoffMs = (long) (Math.pow(2, attempt) * 100);  // 200ms, 400ms, 800ms
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // 3) 三次都失败：保留失败记录，不降级
        logRow.setProcessStatus("failed");
        logRow.setRetryCount(MAX_RETRY);
        webhookLogMapper.updateById(logRow);
        log.error("[CN-DOMESTIC-WEBHOOK] ALL RETRIES FAILED: channel={} traceId={} payload={}",
                channelCode, traceId, truncate(rawBody, 200));
        return DomesticPayResponse.<String>builder()
                .ok(false).channelCode(channelCode)
                .errorCode("PROCESS_FAILED_AFTER_RETRY")
                .errorMessage("All " + MAX_RETRY + " retries failed, see logId=" + logRow.getId())
                .build();
    }

    private String extractEventId(String channelCode, Map<String, String> headers, String rawBody) {
        if ("ALIPAY".equalsIgnoreCase(channelCode)) {
            return headers.get("notify_id");
        }
        if ("WECHAT_PAY".equalsIgnoreCase(channelCode)) {
            // 微信支付 V3 通知 id 用 transaction_id（同通知幂等）
            try {
                JsonNode node = OM.readTree(rawBody);
                return node.path("id").asText(
                        node.path("transaction_id").asText(UUID.randomUUID().toString()));
            } catch (Exception e) {
                return UUID.randomUUID().toString();
            }
        }
        return UUID.randomUUID().toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}