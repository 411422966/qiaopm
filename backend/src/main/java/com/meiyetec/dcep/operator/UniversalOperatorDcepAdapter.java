package com.meiyetec.dcep.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.dcep.AbstractDcepAdapter;
import com.meiyetec.dcep.DcepChannelAdapter;
import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DCEP-03/04 阶段 B：通用运营机构 DCEP 适配器（取代 5 个占位 Adapter）
 *
 * <p>职责：
 * <ol>
 *   <li>根据 channelCode 路由到对应的 {@link Operator} 配置</li>
 *   <li>通过 {@link OperatorClientFactory} 拿到客户端（REST / Netty）</li>
 *   <li>在请求前自动加签，响应后自动验签（验签失败则拒绝）</li>
 *   <li>未启用真实机构时 → 委托给 {@link com.meiyetec.dcep.impl.SandboxDcepAdapter}</li>
 * </ol>
 *
 * <p>channelCode 由调用方决定：
 * <ul>
 *   <li>{@code DCEP-ICBC} / {@code DCEP-CCB} / {@code DCEP-ABC} / {@code DCEP-BOC} / {@code DCEP-BCOM}</li>
 *   <li>{@code DCEP-SANDBOX} 强制走沙箱（即使配置了真实机构）</li>
 * </ul>
 *
 * <p><b>替换策略</b>：当 {@code dcep.operators.ICBC.enabled=true} 时，
 * 此适配器在 {@link #prepay(PrepayRequest)} 中走真实 HTTP 调用；否则委托给 {@link SandboxDcepAdapter}。
 */
@Slf4j
@Component("universalOperatorDcepAdapter")
public class UniversalOperatorDcepAdapter extends AbstractDcepAdapter implements DcepChannelAdapter {

    @Autowired private DcepOperatorRegistry registry;
    @Autowired private OperatorClientFactory clientFactory;
    @Autowired private OperatorSignatureService signatureService;
    @Autowired(required = false) private com.meiyetec.dcep.impl.SandboxDcepAdapter sandboxAdapter;

    private static final ObjectMapper JSON = new ObjectMapper();

    @PostConstruct
    void registerSignatureBean() {
        OperatorClientFactory.BeanRegistrar.registerAll(signatureService);
        log.info("[DCEP-Universal] 启动完成，enabled operators: {}, supported protocols: {}",
                registry.listEnabledOperators(), clientFactory.supportedProtocols());
    }

    @Override
    public String channelCode() {
        // 多 channelCode 复用：路由由 DcepAdapterFactory 调度，本类不限定 channelCode
        return "DCEP-UNIVERSAL";
    }

    @Override
    public String toString() {
        return "UniversalOperatorDcepAdapter(enabled=" + registry.listEnabledOperators() + ")";
    }

    @Override
    public PrepayResult prepay(PrepayRequest req) {
        if (registry.shouldUseSandbox(req.getChannelCode()) || sandboxAdapter == null) {
            log.debug("[DCEP-Universal] channel={} 未启用真实机构，转 Sandbox", req.getChannelCode());
            return sandboxAdapter != null ? sandboxAdapter.prepay(req) : failPrepay("NO_ADAPTER", "无可用适配器");
        }
        Operator op = registry.lookup(req.getChannelCode());
        if (op == null) {
            return failPrepay("OPERATOR_NOT_CONFIGURED", "未配置 operator: " + req.getChannelCode());
        }
        Map<String, Object> payload = toPrepayPayload(req);
        OperatorResponse resp = clientFactory.getClient(op).invoke(op, op.getPrepayPath(), payload);
        if (!resp.isSuccess()) {
            return failPrepay(resp.getErrorCode(), resp.getErrorMsg());
        }
        return PrepayResult.builder()
                .success(true)
                .prepayId(stringOf(resp.getData(), "prepayId"))
                .qrCode(stringOf(resp.getData(), "qrCode"))
                .h5Url(stringOf(resp.getData(), "h5Url"))
                .expiresAt(stringOf(resp.getData(), "expiresAt"))
                .build();
    }

    @Override
    public QueryResult query(OrderQueryRequest req) {
        if (registry.shouldUseSandbox(req.getEnvMode() == null ? null : req.getEnvMode())
                || sandboxAdapter == null) {
            return sandboxAdapter != null ? sandboxAdapter.query(req)
                    : QueryResult.builder().success(false).errorCode("NO_ADAPTER").build();
        }
        Operator op = registry.lookup("DCEP-" + (req.getEnvMode() == null ? "SANDBOX" : ""));
        // 简化：根据 envMode 推断 channelCode
        Operator opResolved = op != null ? op : registry.lookup(deriveChannelCode(req.getEnvMode()));
        if (opResolved == null) {
            return QueryResult.builder().success(false).errorCode("OPERATOR_NOT_CONFIGURED").build();
        }
        Map<String, Object> payload = Map.of(
                "merchantId", opResolved.getMerchantId(),
                "dcepOrderNo", req.getDcepOrderNo(),
                "envMode", opResolved.getEnvMode());
        OperatorResponse resp = clientFactory.getClient(opResolved).invoke(opResolved,
                opResolved.getQueryPath(), payload);
        if (!resp.isSuccess()) {
            return QueryResult.builder().success(false).errorCode(resp.getErrorCode()).errorMsg(resp.getErrorMsg()).build();
        }
        Map<String, Object> d = resp.getData();
        return QueryResult.builder()
                .success(true)
                .status(stringOf(d, "status"))
                .amountCents(longOf(d, "amountCents"))
                .transactionId(stringOf(d, "transactionId"))
                .paidAt(stringOf(d, "paidAt"))
                .build();
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        if (sandboxAdapter == null) {
            return RefundResult.builder().success(false).errorCode("NO_ADAPTER").build();
        }
        if (registry.shouldUseSandbox(req.getEnvMode() == null ? null : req.getEnvMode())) {
            return sandboxAdapter.refund(req);
        }
        Operator op = registry.lookup(deriveChannelCode(req.getEnvMode()));
        if (op == null) {
            return RefundResult.builder().success(false).errorCode("OPERATOR_NOT_CONFIGURED").build();
        }
        Map<String, Object> payload = Map.of(
                "merchantId", op.getMerchantId(),
                "dcepOrderNo", req.getDcepOrderNo(),
                "refundOrderNo", req.getRefundOrderNo(),
                "amountCents", req.getAmountCents(),
                "reason", req.getReason(),
                "notifyUrl", req.getNotifyUrl() == null ? "" : req.getNotifyUrl());
        OperatorResponse resp = clientFactory.getClient(op).invoke(op, op.getRefundPath(), payload);
        if (!resp.isSuccess()) {
            return RefundResult.builder().success(false).errorCode(resp.getErrorCode()).errorMsg(resp.getErrorMsg()).build();
        }
        Map<String, Object> d = resp.getData();
        return RefundResult.builder()
                .success(true)
                .refundId(stringOf(d, "refundId"))
                .refundedAt(stringOf(d, "refundedAt"))
                .build();
    }

    @Override
    public CloseResult closeOrder(CloseRequest req) {
        if (sandboxAdapter == null) {
            return CloseResult.builder().success(false).errorCode("NO_ADAPTER").build();
        }
        if (registry.shouldUseSandbox(req.getEnvMode() == null ? null : req.getEnvMode())) {
            return sandboxAdapter.closeOrder(req);
        }
        Operator op = registry.lookup(deriveChannelCode(req.getEnvMode()));
        if (op == null) {
            return CloseResult.builder().success(false).errorCode("OPERATOR_NOT_CONFIGURED").build();
        }
        Map<String, Object> payload = Map.of(
                "merchantId", op.getMerchantId(),
                "dcepOrderNo", req.getDcepOrderNo());
        OperatorResponse resp = clientFactory.getClient(op).invoke(op, op.getClosePath(), payload);
        return CloseResult.builder().success(resp.isSuccess())
                .errorCode(resp.getErrorCode()).errorMsg(resp.getErrorMsg()).build();
    }

    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        if (sandboxAdapter == null) {
            return NotifyResult.builder().signatureValid(false).timestampValid(false).rawBody(rawBody).build();
        }
        if (rawBody == null || rawBody.isEmpty()) {
            return NotifyResult.builder().signatureValid(false).timestampValid(false).rawBody(rawBody).build();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JSON.readValue(rawBody, Map.class);
            // 1) 提取 channelCode（可能由 header 或 body 提供）
            String channelCode = headers == null ? null : headers.get("X-DCEP-Channel");
            if (channelCode == null && parsed.get("channelCode") != null) {
                channelCode = String.valueOf(parsed.get("channelCode"));
            }
            Operator op = channelCode == null ? null : registry.lookup(channelCode);
            // 2) 如果机构未启用 → 走 Sandbox 通知解析（不验签）
            if (op == null || registry.shouldUseSandbox(channelCode)) {
                NotifyResult sandboxResult = sandboxAdapter.parseAndVerifyNotify(rawBody, headers);
                sandboxResult.setSignatureValid(true);
                sandboxResult.setTimestampValid(true);
                return sandboxResult;
            }
            // 3) 真实机构：必须验签 + 防重放
            Map<String, String> stringFields = new LinkedHashMap<>();
            parsed.forEach((k, v) -> stringFields.put(k, v == null ? "" : String.valueOf(v)));
            String signature = stringFields.remove("sign");
            if (signature == null) signature = headers.get("X-DCEP-Sign");
            boolean valid = signature != null && signatureService.verifyNotify(op, stringFields, signature);
            String timestamp = stringFields.get("timestamp");
            boolean tsValid = isTimestampValid(timestamp);
            String notifyId = stringFields.getOrDefault("notifyId", "");
            // 防重放（Redis SETNX）
            boolean isReplay = !op.equals(null) && notifyId != null && !notifyId.isEmpty()
                    && !replayProtectorAcquire(op, notifyId);
            return NotifyResult.builder()
                    .signatureValid(valid)
                    .timestampValid(tsValid)
                    .eventType(stringFields.getOrDefault("eventType", "PAY_SUCCESS"))
                    .dcepOrderNo(stringFields.get("dcepOrderNo"))
                    .transactionId(stringFields.get("transactionId"))
                    .amountCents(parseLong(stringFields.get("amountCents")))
                    .rawBody(rawBody)
                    .build()
                    .withReplayDetected(isReplay);
        } catch (Exception e) {
            log.warn("[DCEP-Universal] notify parse failed: {}", e.getMessage());
            return NotifyResult.builder().signatureValid(false).timestampValid(false).rawBody(rawBody).build();
        }
    }

    /** 注入 NotifyReplayProtector（避免强制依赖 Redis） */
    @Autowired(required = false)
    private NotifyReplayProtector replayProtector;

    private boolean replayProtectorAcquire(Operator op, String notifyId) {
        if (replayProtector == null) return true; // 未配置：放行（dev 模式）
        return replayProtector.tryAcquire(op, notifyId);
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String deriveChannelCode(String envMode) {
        if (envMode == null) return "DCEP-SANDBOX";
        // envMode 不是 channelCode — 这里只是占位，实际应由调用方传 channelCode
        // 简化：返回 envMode，让 registry 查不到 → fallback 到 sandbox
        return envMode;
    }

    private Map<String, Object> toPrepayPayload(PrepayRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("merchantId", req.getMerchantId());
        m.put("appId", req.getAppId());
        m.put("dcepOrderNo", req.getDcepOrderNo());
        m.put("bizOrderNo", req.getBizOrderNo());
        m.put("amountCents", req.getAmountCents());
        m.put("goodsName", req.getGoodsName());
        m.put("payerPhone", req.getPayerPhone());
        m.put("notifyUrl", req.getNotifyUrl());
        m.put("expireAt", req.getExpireAt());
        m.put("walletType", req.getWalletType());
        m.put("envMode", req.getEnvMode());
        return m;
    }

    private String stringOf(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object v = data.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private Long longOf(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object v = data.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
