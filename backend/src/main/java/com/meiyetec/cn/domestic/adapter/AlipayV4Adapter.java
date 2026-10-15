package com.meiyetec.cn.domestic.adapter;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.meiyetec.cn.domestic.DomesticPayChannelAdapter;
import com.meiyetec.cn.domestic.DomesticPayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 国内支付-支付宝 V4 适配器（alipay-sdk-java 4.35.584.ALL）
 *
 * <p>支持场景：
 * <ul>
 *   <li>预支付扫码（NATIVE/QR 码）</li>
 *   <li>订单查询（支付状态兜底）</li>
 *   <li>退款（幂等）</li>
 *   <li>关闭订单（超时未支付）</li>
 *   <li>异步通知验签（RSA2）</li>
 * </ul>
 *
 * <p>沙箱模式（凭据未配置或 env-mode=sandbox）：返回 mock 结果，
 * 确保 DomesticPayRouter / Webhook / Controller 全链路可联通。
 *
 * @since 2026-09-07
 */
@Slf4j
@Component
public class AlipayV4Adapter implements DomesticPayChannelAdapter {

    private final DomesticPayProperties properties;
    private final boolean sandboxMode;
    private final AlipayClient alipayClient;

    public AlipayV4Adapter(DomesticPayProperties properties) {
        this.properties = properties;
        this.sandboxMode = isSandboxMode(properties);
        if (!sandboxMode) {
            DomesticPayProperties.Alipay cfg = properties.getAlipay();
            this.alipayClient = new DefaultAlipayClient(
                    cfg.getGateway(),
                    cfg.getAppId(),
                    cfg.getPrivateKey(),
                    "json",
                    "UTF-8",
                    cfg.getAlipayPublicKey(),
                    cfg.getSignType() == null ? "RSA2" : cfg.getSignType());
            log.info("[CN-ALIPAY] PROD mode — AlipayClient initialized, gateway={}, appId={}",
                    cfg.getGateway(), cfg.getAppId());
        } else {
            this.alipayClient = null;
            log.warn("[CN-ALIPAY] SANDBOX mode — real API calls disabled, returning mock results");
        }
    }

    private boolean isSandboxMode(DomesticPayProperties p) {
        DomesticPayProperties.Alipay a = p.getAlipay();
        return a.getAppId() == null || a.getAppId().isBlank() || a.getAppId().contains("PLACEHOLDER")
                || a.getPrivateKey() == null || a.getPrivateKey().isBlank()
                || "sandbox".equalsIgnoreCase(p.getEnvMode());
    }

    @Override
    public String channelCode() {
        return "ALIPAY";
    }

    @Override
    public String channelName() {
        return sandboxMode ? "支付宝（沙箱）" : "支付宝";
    }

    @Override
    public String status() {
        return sandboxMode ? "sandbox" : "active";
    }

    // ============================================================
    // 1. prepay — 扫码预下单（alipay.trade.precreate）
    // ============================================================
    @Override
    public PrepayResult prepay(PrepayRequest req) {
        if (sandboxMode) return mockPrepay(req);

        try {
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setBizContent("{"
                    + "\"out_trade_no\":\"" + escapeJson(req.getOutOrderNo()) + "\","
                    + "\"total_amount\":\"" + centsToYuan(req.getAmountCents()) + "\","
                    + "\"subject\":\"" + escapeJson(req.getGoodsName()) + "\""
                    + (req.getExpireAt() != null ? ",\"timeout_express\":\"" + req.getExpireAt() + "\"" : "")
                    + "}");
            request.putOtherTextParam("notify_url", req.getNotifyUrl());

            AlipayTradePrecreateResponse resp = alipayClient.execute(request);
            if (resp.isSuccess()) {
                log.info("[CN-ALIPAY] prepay OK: outOrderNo={} qrCode={}", req.getOutOrderNo(), resp.getQrCode());
                return PrepayResult.builder()
                        .success(true)
                        .prepayId(resp.getOutTradeNo())
                        .qrCode(resp.getQrCode())
                        .expiresAt(req.getExpireAt() == null
                                ? LocalDateTime.now().plusMinutes(15)
                                        .atZone(ZoneOffset.ofHours(8))
                                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                : req.getExpireAt())
                        .build();
            } else {
                log.error("[CN-ALIPAY] prepay FAIL: outOrderNo={} code={} msg={}",
                        req.getOutOrderNo(), resp.getCode(), resp.getMsg());
                return PrepayResult.builder()
                        .success(false)
                        .errorCode(resp.getCode())
                        .errorMsg(resp.getMsg())
                        .build();
            }
        } catch (AlipayApiException e) {
            log.error("[CN-ALIPAY] prepay exception: outOrderNo={}", req.getOutOrderNo(), e);
            return PrepayResult.builder()
                    .success(false)
                    .errorCode(e.getErrCode())
                    .errorMsg(e.getErrMsg())
                    .build();
        }
    }

    // ============================================================
    // 2. query — 交易查询（alipay.trade.query）
    // ============================================================
    @Override
    public QueryResult query(OrderQueryRequest req) {
        if (sandboxMode) return mockQuery(req);

        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizContent("{\"out_trade_no\":\"" + escapeJson(req.getOutOrderNo()) + "\"}");

            AlipayTradeQueryResponse resp = alipayClient.execute(request);
            if (resp.isSuccess()) {
                String status = mapAlipayStatus(resp.getTradeStatus());
                long amountCents = yuanToCents(resp.getTotalAmount());
                log.info("[CN-ALIPAY] query OK: outOrderNo={} status={}", req.getOutOrderNo(), status);
                return QueryResult.builder()
                        .success(true)
                        .status(status)
                        .amountCents(amountCents)
                        .transactionId(resp.getTradeNo())
                        .paidAt(dateToString(resp.getSendPayDate()))
                        .build();
            } else {
                return QueryResult.builder()
                        .success(false)
                        .errorCode(resp.getCode())
                        .errorMsg(resp.getMsg())
                        .build();
            }
        } catch (AlipayApiException e) {
            log.error("[CN-ALIPAY] query exception: outOrderNo={}", req.getOutOrderNo(), e);
            return QueryResult.builder()
                    .success(false)
                    .errorCode(e.getErrCode())
                    .errorMsg(e.getErrMsg())
                    .build();
        }
    }

    // ============================================================
    // 3. refund — 退款（alipay.trade.refund）
    // ============================================================
    @Override
    public RefundResult refund(RefundRequest req) {
        if (sandboxMode) return mockRefund(req);

        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            request.setBizContent("{"
                    + "\"out_trade_no\":\"" + escapeJson(req.getOutOrderNo()) + "\","
                    + "\"refund_amount\":\"" + centsToYuan(req.getAmountCents()) + "\","
                    + "\"out_request_no\":\"" + escapeJson(req.getRefundOrderNo()) + "\""
                    + (req.getReason() != null ? ",\"refund_reason\":\"" + escapeJson(req.getReason()) + "\"" : "")
                    + "}");
            if (req.getNotifyUrl() != null) {
                request.putOtherTextParam("notify_url", req.getNotifyUrl());
            }

            AlipayTradeRefundResponse resp = alipayClient.execute(request);
            if (resp.isSuccess()) {
                log.info("[CN-ALIPAY] refund OK: outOrderNo={} refundNo={} refundFee={}",
                        req.getOutOrderNo(), req.getRefundOrderNo(), resp.getRefundFee());
                return RefundResult.builder()
                        .success(true)
                        .refundId(resp.getTradeNo())
                        .refundedAt(dateToString(resp.getGmtRefundPay()))
                        .build();
            } else {
                log.error("[CN-ALIPAY] refund FAIL: outOrderNo={} code={} msg={}",
                        req.getOutOrderNo(), resp.getCode(), resp.getMsg());
                return RefundResult.builder()
                        .success(false)
                        .errorCode(resp.getCode())
                        .errorMsg(resp.getMsg())
                        .build();
            }
        } catch (AlipayApiException e) {
            log.error("[CN-ALIPAY] refund exception: outOrderNo={}", req.getOutOrderNo(), e);
            return RefundResult.builder()
                    .success(false)
                    .errorCode(e.getErrCode())
                    .errorMsg(e.getErrMsg())
                    .build();
        }
    }

    // ============================================================
    // 4. closeOrder — 关闭订单（alipay.trade.close）
    // ============================================================
    @Override
    public CloseResult closeOrder(CloseRequest req) {
        if (sandboxMode) return CloseResult.builder().success(true).build();

        try {
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            request.setBizContent("{\"out_trade_no\":\"" + escapeJson(req.getOutOrderNo()) + "\"}");

            AlipayTradeCloseResponse resp = alipayClient.execute(request);
            if (resp.isSuccess()) {
                log.info("[CN-ALIPAY] close OK: outOrderNo={}", req.getOutOrderNo());
                return CloseResult.builder().success(true).build();
            } else {
                // 重复关闭（订单已支付/已关闭）不算错误
                if ("ACQ.TRADE_NOT_EXIST".equals(resp.getCode()) || "ACQ.TRADE_STATUS_ERROR".equals(resp.getCode())) {
                    return CloseResult.builder().success(true)
                            .errorCode(resp.getCode()).errorMsg(resp.getSubMsg()).build();
                }
                return CloseResult.builder()
                        .success(false)
                        .errorCode(resp.getCode())
                        .errorMsg(resp.getMsg())
                        .build();
            }
        } catch (AlipayApiException e) {
            log.error("[CN-ALIPAY] close exception: outOrderNo={}", req.getOutOrderNo(), e);
            return CloseResult.builder()
                    .success(false)
                    .errorCode(e.getErrCode())
                    .errorMsg(e.getErrMsg())
                    .build();
        }
    }

    // ============================================================
    // 5. parseAndVerifyNotify — 异步通知验签（RSA2）
    //
    // 支付宝回调参数在 body（application/x-www-form-urlencoded 格式），
    // SDK 用 AlipaySignature.rsaCheckV1 验签。
    // ============================================================
    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        // 沙箱模式不验签，直接返回 success
        if (sandboxMode) {
            return parseBodyOnly(rawBody, true);
        }

        try {
            // 支付宝通知 body 是 application/x-www-form-urlencoded，SDK 自动解析
            // rsaCheckV1 第二个参数是 alipayPublicKey（支付宝公钥）
            boolean valid = AlipaySignature.rsaCheckV1(
                    parseUrlEncoded(rawBody),
                    properties.getAlipay().getAlipayPublicKey(),
                    "UTF-8",
                    properties.getAlipay().getSignType() == null ? "RSA2" : properties.getAlipay().getSignType());

            if (!valid) {
                log.warn("[CN-ALIPAY] notify signature INVALID: body={}", rawBody);
                return NotifyResult.builder()
                        .signatureValid(false)
                        .rawBody(rawBody)
                        .errorMsg("签名校验失败")
                        .build();
            }

            Map<String, String> params = parseUrlEncoded(rawBody);
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String totalAmount = params.get("total_amount");
            String tradeStatus = params.get("trade_status");
            String notifyId = params.get("notify_id");

            long amountCents = yuanToCents(totalAmount);
            boolean isSuccess = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);

            log.info("[CN-ALIPAY] notify verified OK: outTradeNo={} status={} amount={}",
                    outTradeNo, tradeStatus, amountCents);

            return NotifyResult.builder()
                    .signatureValid(true)
                    .eventType(isSuccess ? "PAY_SUCCESS" : "PAY_FAILED")
                    .notifyId(notifyId)
                    .outOrderNo(outTradeNo)
                    .transactionId(tradeNo)
                    .amountCents(amountCents)
                    .rawBody(rawBody)
                    .build();

        } catch (AlipayApiException e) {
            log.error("[CN-ALIPAY] notify verify exception", e);
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("验签异常: " + e.getErrMsg())
                    .build();
        } catch (Exception e) {
            log.error("[CN-ALIPAY] notify parse exception", e);
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("解析异常: " + e.getMessage())
                    .build();
        }
    }

    // ============================================================
    // Mock 结果（沙箱 / 凭据未配置时）
    // ============================================================

    private PrepayResult mockPrepay(PrepayRequest req) {
        String prepayId = "MOCK-ALI-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String qrCode = "https://qr.alipay.com/bax" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[CN-ALIPAY-SANDBOX] prepay: outOrderNo={} prepayId={}", req.getOutOrderNo(), prepayId);
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

    private QueryResult mockQuery(OrderQueryRequest req) {
        return QueryResult.builder().success(true).status("WAIT_PAY").build();
    }

    private RefundResult mockRefund(RefundRequest req) {
        log.info("[CN-ALIPAY-SANDBOX] refund: outOrderNo={} refundNo={}",
                req.getOutOrderNo(), req.getRefundOrderNo());
        return RefundResult.builder()
                .success(true)
                .refundId("MOCK-ALI-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .refundedAt(LocalDateTime.now().atZone(ZoneOffset.ofHours(8))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .build();
    }

    private NotifyResult parseBodyOnly(String rawBody, boolean valid) {
        Map<String, String> params = parseUrlEncoded(rawBody);
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.getOrDefault("trade_no", "MOCK-ALI-TX-" + UUID.randomUUID().toString().substring(0, 8));
        String totalAmount = params.getOrDefault("total_amount", "0.00");
        String tradeStatus = params.getOrDefault("trade_status", "TRADE_SUCCESS");
        String notifyId = params.getOrDefault("notify_id", "MOCK-NOTIFY-" + UUID.randomUUID().toString().substring(0, 8));
        long amountCents = yuanToCents(totalAmount);
        boolean isSuccess = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);

        return NotifyResult.builder()
                .signatureValid(valid)
                .eventType(isSuccess ? "PAY_SUCCESS" : "PAY_FAILED")
                .notifyId(notifyId)
                .outOrderNo(outTradeNo)
                .transactionId(tradeNo)
                .amountCents(amountCents)
                .rawBody(rawBody)
                .build();
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private String centsToYuan(Long cents) {
        if (cents == null) return "0.00";
        return String.format("%.2f", cents / 100.0);
    }

    private long yuanToCents(String yuan) {
        if (yuan == null || yuan.isEmpty()) return 0L;
        try {
            return Math.round(Double.parseDouble(yuan) * 100);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String mapAlipayStatus(String alipayStatus) {
        if (alipayStatus == null) return "UNKNOWN";
        return switch (alipayStatus) {
            case "WAIT_BUYER_PAY" -> "WAIT_PAY";
            case "TRADE_CLOSED" -> "CLOSED";
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> "SUCCESS";
            case "TRADE_REFUNDED" -> "REFUNDED";
            default -> alipayStatus;
        };
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Date → ISO 8601 String（支付宝响应日期字段转换）
     */
    private String dateToString(Date d) {
        if (d == null) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 application/x-www-form-urlencoded body 为 Map
     */
    private Map<String, String> parseUrlEncoded(String body) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (body == null || body.isEmpty()) return map;
        try {
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf('=');
                if (idx < 0) continue;
                String k = pair.substring(0, idx);
                String v = pair.length() > idx + 1
                        ? java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                        : "";
                map.put(k, v);
            }
        } catch (Exception e) {
            log.warn("[CN-ALIPAY] url-decode failed: {}", e.getMessage());
        }
        return map;
    }
}
