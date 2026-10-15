package com.meiyetec.cn.domestic.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.cn.domestic.DomesticPayChannelAdapter;
import com.meiyetec.cn.domestic.DomesticPayProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.MalformedMessageException;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 国内支付-微信支付 V3 适配器（官方 SDK 0.2.17）
 *
 * <p>基于 com.github.wechatpay-apiv3:wechatpay-java 封装，验签走 SDK 内置 {@link NotificationParser}。
 *
 * <p>支持的场景（本期）：Native（NATIVE，二维码扫码支付）
 * <p>未启用场景：JSAPI / H5 / 小程序 / 退款 — 接口预留，二期补 {@code JsapiServiceExtension} / {@code RefundService}
 *
 * <p>**沙箱模式**（{@code app.cn-pay.env-mode=sandbox} 或 app-id/mch-id 未填）：
 * <ul>
 *   <li>不调用微信支付接口，返回 mock prepayId（{@code MOCK-WX-{timestamp}-{uuid}}）</li>
 *   <li>{@link #parseAndVerifyNotify} 直接解析 body 返回（签名"通过"），便于本地链路联调</li>
 * </ul>
 *
 * @since 2026-09-07
 */
@Slf4j
@Component
public class WechatPayV3Adapter implements DomesticPayChannelAdapter {

    private static final ObjectMapper OM = new ObjectMapper();

    private final DomesticPayProperties properties;
    private NativePayService nativePayService;   // 沙箱模式为 null
    private NotificationParser notificationParser;
    private Config sdkConfig;
    private final boolean sandboxMode;

    public WechatPayV3Adapter(DomesticPayProperties properties) {
        this.properties = properties;
        this.sandboxMode = isSandboxMode(properties);
        if (!sandboxMode) {
            initSdk(properties);
        } else {
            log.warn("[CN-WECHAT] SANDBOX mode enabled — 凭据未配置或 env-mode=sandbox，" +
                    "所有 prepay/query/refund 将返回 mock 结果（仅供联调）");
        }
    }

    private void initSdk(DomesticPayProperties properties) {
        try {
            DomesticPayProperties.Wechat w = properties.getWechat();
            this.sdkConfig = new RSAAutoCertificateConfig.Builder()
                    .merchantId(w.getMchId())
                    .merchantSerialNumber(w.getCertSerialNo())
                    .apiV3Key(w.getApiV3Key())
                    .privateKeyFromPath(w.getPrivateKeyPath())
                    .build();
            this.nativePayService = new NativePayService.Builder().config(sdkConfig).build();
            // 回调验签使用与业务相同的 config（复用平台证书缓存）
            NotificationConfig notifyConfig = new RSAAutoCertificateConfig.Builder()
                    .merchantId(w.getMchId())
                    .merchantSerialNumber(w.getCertSerialNo())
                    .apiV3Key(w.getApiV3Key())
                    .privateKeyFromPath(w.getPrivateKeyPath())
                    .build();
            this.notificationParser = new NotificationParser(notifyConfig);
            log.info("[CN-WECHAT] SDK init ok: mchId={} appId={}",
                    w.getMchId(), w.getAppId());
        } catch (Exception e) {
            log.error("[CN-WECHAT] SDK init failed — 降级为 sandbox 模式", e);
            // 此处不修改 sandboxMode（final），让 bean 初始化失败快速可见
            throw new IllegalStateException("WechatPayV3 SDK init failed: " + e.getMessage(), e);
        }
    }

    private boolean isSandboxMode(DomesticPayProperties p) {
        if (!"sandbox".equalsIgnoreCase(p.getEnvMode())) {
            // 显式 prod 也允许走真实 SDK；如果凭据 placeholder 则仍切 sandbox
        }
        DomesticPayProperties.Wechat w = p.getWechat();
        boolean placeholder = w.getMchId() == null || w.getMchId().isBlank() || w.getMchId().contains("PLACEHOLDER")
                || w.getAppId() == null || w.getAppId().isBlank();
        return placeholder || "sandbox".equalsIgnoreCase(p.getEnvMode());
    }

    @Override
    public String channelCode() {
        return "WECHAT_PAY";
    }

    @Override
    public String channelName() {
        return sandboxMode ? "微信支付（沙箱）" : "微信支付";
    }

    @Override
    public String status() {
        return sandboxMode ? "sandbox" : "active";
    }

    // ============================================================
    // 1. prepay
    // ============================================================
    @Override
    public PrepayResult prepay(PrepayRequest req) {
        if (sandboxMode) return prepayMock(req);
        try {
            com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest wxReq =
                    new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
            wxReq.setAppid(properties.getWechat().getAppId());
            wxReq.setMchid(properties.getWechat().getMchId());
            wxReq.setDescription(truncate(req.getGoodsName(), 127));
            wxReq.setOutTradeNo(req.getOutOrderNo());
            wxReq.setNotifyUrl(req.getNotifyUrl());
            com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                    new com.wechat.pay.java.service.payments.nativepay.model.Amount();
            amount.setTotal(req.getAmountCents().intValue());
            amount.setCurrency("CNY");
            wxReq.setAmount(amount);
            if (req.getExpireAt() != null) {
                wxReq.setTimeExpire(req.getExpireAt());
            }

            com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse wxResp =
                    nativePayService.prepay(wxReq);
            log.info("[CN-WECHAT] prepay ok: outTradeNo={} codeUrl={}",
                    req.getOutOrderNo(), truncate(wxResp.getCodeUrl(), 32));
            return PrepayResult.builder()
                    .success(true)
                    // Native 支付 SDK 0.2.17 仅返回 codeUrl，无 prepay_id；prepay_id 仅 JSAPI 有
                    .prepayId(null)
                    .qrCode(wxResp.getCodeUrl())
                    .expiresAt(req.getExpireAt() == null
                            ? LocalDateTime.now().plusMinutes(15)
                                    .atZone(ZoneOffset.ofHours(8))
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            : req.getExpireAt())
                    .build();
        } catch (ServiceException e) {
            log.error("[CN-WECHAT] prepay service error: outTradeNo={} code={} msg={}",
                    req.getOutOrderNo(), e.getErrorCode(), e.getErrorMessage());
            return PrepayResult.builder()
                    .success(false)
                    .errorCode(e.getErrorCode())
                    .errorMsg(e.getErrorMessage())
                    .build();
        } catch (Exception e) {
            log.error("[CN-WECHAT] prepay exception", e);
            return PrepayResult.builder()
                    .success(false)
                    .errorCode("WX_INVOKE_ERROR")
                    .errorMsg(e.getMessage())
                    .build();
        }
    }

    private PrepayResult prepayMock(PrepayRequest req) {
        String prepayId = "MOCK-WX-" + Instant.now().getEpochSecond() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String qrCode = "weixin://wxpay/bizpayurl?pr=MOCK" + req.getOutOrderNo();
        log.info("[CN-WECHAT-MOCK] prepay: outTradeNo={} prepayId={}", req.getOutOrderNo(), prepayId);
        return PrepayResult.builder()
                .success(true)
                .prepayId(prepayId)
                .qrCode(qrCode)
                .expiresAt(req.getExpireAt() == null
                        ? LocalDateTime.now().plusMinutes(15)
                                .atZone(ZoneOffset.ofHours(8))
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        : req.getExpireAt())
                .build();
    }

    // ============================================================
    // 2. query
    // ============================================================
    @Override
    public QueryResult query(OrderQueryRequest req) {
        if (sandboxMode) {
            return QueryResult.builder().success(true).status("WAIT_PAY").build();
        }
        try {
            QueryOrderByOutTradeNoRequest wxReq = new QueryOrderByOutTradeNoRequest();
            wxReq.setMchid(properties.getWechat().getMchId());
            wxReq.setOutTradeNo(req.getOutOrderNo());
            Transaction txn = nativePayService.queryOrderByOutTradeNo(wxReq);
            return QueryResult.builder()
                    .success(true)
                    .status(mapWxTradeState(txn.getTradeState()))
                    .amountCents(txn.getAmount() == null || txn.getAmount().getTotal() == null
                            ? null : (long) txn.getAmount().getTotal())
                    .transactionId(txn.getTransactionId())
                    .paidAt(txn.getSuccessTime())
                    .build();
        } catch (ServiceException e) {
            return QueryResult.builder()
                    .success(false)
                    .errorCode(e.getErrorCode())
                    .errorMsg(e.getErrorMessage())
                    .build();
        } catch (Exception e) {
            return QueryResult.builder()
                    .success(false)
                    .errorCode("WX_QUERY_ERROR")
                    .errorMsg(e.getMessage())
                    .build();
        }
    }

    private String mapWxTradeState(Transaction.TradeStateEnum state) {
        if (state == null) return "UNKNOWN";
        String name = state.name();
        // 微信支付 V3 交易状态枚举值映射
        if ("SUCCESS".equals(name)) return "SUCCESS";
        if ("NOTPAY".equals(name) || "USERPAYING".equals(name) || "ACCEPTED".equals(name)) return "WAIT_PAY";
        if ("CLOSED".equals(name)) return "CLOSED";
        if ("REFUND".equals(name)) return "REFUNDED";
        if ("REVOKED".equals(name) || "PAYERROR".equals(name)) return "FAILED";
        return name;
    }

    // ============================================================
    // 3. refund（二期接入 RefundService）
    // ============================================================
    @Override
    public RefundResult refund(RefundRequest req) {
        log.warn("[CN-WECHAT] refund stub — 二期补 RefundsService: outTradeNo={} refundNo={}",
                req.getOutOrderNo(), req.getRefundOrderNo());
        return RefundResult.builder()
                .success(false)
                .errorCode("WX_REFUND_NOT_IMPLEMENTED")
                .errorMsg("二期接入 com.wechat.pay.java.service.refund.RefundService")
                .build();
    }

    // ============================================================
    // 4. closeOrder
    // ============================================================
    @Override
    public CloseResult closeOrder(CloseRequest req) {
        if (sandboxMode) return CloseResult.builder().success(true).build();
        try {
            CloseOrderRequest wxReq = new CloseOrderRequest();
            wxReq.setMchid(properties.getWechat().getMchId());
            wxReq.setOutTradeNo(req.getOutOrderNo());
            nativePayService.closeOrder(wxReq);
            return CloseResult.builder().success(true).build();
        } catch (Exception e) {
            return CloseResult.builder()
                    .success(false)
                    .errorCode("WX_CLOSE_ERROR")
                    .errorMsg(e.getMessage())
                    .build();
        }
    }

    // ============================================================
    // 5. parseAndVerifyNotify（走 SDK NotificationParser）
    // ============================================================
    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        if (sandboxMode) return parseMockNotify(rawBody);

        // 微信 V3 头：Wechatpay-Signature / Wechatpay-Timestamp / Wechatpay-Nonce / Wechatpay-Serial
        String signature = headers.get("Wechatpay-Signature");
        String timestamp = headers.get("Wechatpay-Timestamp");
        String nonce = headers.get("Wechatpay-Nonce");
        String serial = headers.get("Wechatpay-Serial");
        String signatureType = headers.get("Wechatpay-Signature-Type");
        // signatureType 在 SDK 0.2.x 默认 WECHATPAY2-SHA256-RSA2048，可不显式传

        if (signature == null || timestamp == null || nonce == null || serial == null) {
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("Missing Wechatpay-* header")
                    .build();
        }

        try {
            RequestParam param = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(rawBody)
                    .build();
            Transaction txn = notificationParser.parse(param, Transaction.class);
            log.info("[CN-WECHAT] notify verified: outTradeNo={} txnId={} state={}",
                    txn.getOutTradeNo(), txn.getTransactionId(), txn.getTradeState());
            return NotifyResult.builder()
                    .signatureValid(true)
                    .eventType("PAY_SUCCESS")
                    .notifyId(txn.getTransactionId())   // 微信支付 V3 用 transactionId 幂等
                    .outOrderNo(txn.getOutTradeNo())
                    .transactionId(txn.getTransactionId())
                    .amountCents(txn.getAmount() == null ? null : (long) txn.getAmount().getTotal())
                    .rawBody(rawBody)
                    .build();
        } catch (ValidationException e) {
            log.warn("[CN-WECHAT] notify signature validation failed: {}", e.getMessage());
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("ValidationException: " + e.getMessage())
                    .build();
        } catch (MalformedMessageException e) {
            log.warn("[CN-WECHAT] notify malformed: {}", e.getMessage());
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("MalformedMessageException: " + e.getMessage())
                    .build();
        }
    }

    private NotifyResult parseMockNotify(String rawBody) {
        try {
            JsonNode node = OM.readTree(rawBody);
            String outTradeNo = node.path("out_trade_no").asText();
            String transactionId = "MOCK-WX-TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            long total = node.path("amount").path("total").asLong(0);
            log.info("[CN-WECHAT-MOCK] notify passthrough: outTradeNo={} amount={}", outTradeNo, total);
            return NotifyResult.builder()
                    .signatureValid(true)
                    .eventType("PAY_SUCCESS")
                    .notifyId("MOCK-NOTIFY-" + UUID.randomUUID())
                    .outOrderNo(outTradeNo)
                    .transactionId(transactionId)
                    .amountCents(total)
                    .rawBody(rawBody)
                    .build();
        } catch (Exception e) {
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("Mock parse failed: " + e.getMessage())
                    .build();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
