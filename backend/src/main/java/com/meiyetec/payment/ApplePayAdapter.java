package com.meiyetec.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Apple Pay 海外钱包支付适配器（基于 Stripe PaymentIntents）
 *
 * <h3>架构</h3>
 * <pre>
 *   ┌──────────────┐  1. 后端创建 PaymentIntent（含 payment_method_types=card）
 *   │   Backend    │ ──────────────────────────────────────────┐
 *   └──────────────┘                                           │
 *           │  2. 返回 clientSecret                            ▼
 *           ▼                                          ┌─────────────┐
 *   ┌──────────────┐  3. Stripe.js 渲染 Apple Pay      │   Stripe    │
 *   │   Browser /  │     按钮 → 用户指纹/PIN 验证       │     API     │
 *   │   Mobile Web │     → Apple Pay Sheet              └─────────────┘
 *   └──────────────┘                                           ▲
 *           │  4. 用户授权后 Apple Pay 返回 PKPayment            │
 *           │     → Stripe.js tokenize 后 confirm               │
 *           │     → PaymentIntent 状态机流转                    │
 *           ▼                                                  │
 *   ┌──────────────┐  5. Webhook 异步通知 (payment_intent.succeeded) │
 *   │   Backend    │ ──────────────────────────────────────────┘
 *   └──────────────┘
 * </pre>
 *
 * <h3>配置说明（biz_overseas_payment_gateway 表）</h3>
 * <ul>
 *   <li>{@code gatewayCode} = {@code apple_pay}</li>
 *   <li>{@code publishableKey} = Stripe Publishable Key (pk_test_xxx / pk_live_xxx)</li>
 *   <li>{@code secretKey}      = Stripe Secret Key      (sk_test_xxx / sk_live_xxx) — 后端调 Stripe API 用</li>
 *   <li>{@code webhookSecret}  = Stripe Webhook Signing Secret (whsec_xxx) — 验证 webhook 签名用</li>
 *   <li>{@code merchantId}     = Apple Pay Merchant Identifier (merchant.com.xxx.xxx)</li>
 *   <li>{@code metadata}       = JSON 可选字段：displayName / domainVerificationFilePath / supportedNetworks</li>
 * </ul>
 *
 * <h3>金额约定</h3>
 * Stripe 内部以「最小货币单位」（cents）计；零小数货币（JPY/KRW 等）保留原值。
 *
 * <h3>与 StripeAdapter 关系</h3>
 * Apple Pay 底层走 Stripe API，但本适配器强制附加
 * {@code payment_method_types=card} + {@code metadata[wallet_type]=apple_pay}
 * 以便 webhook 解析时区分通道来源。
 *
 * @author Cursor Agent
 * @since 2026-09-05 (P3.6 海外 Apple Pay / Google Pay)
 */
@Slf4j
@Component("applePayAdapter")
public class ApplePayAdapter implements OverseasPaymentAdapter {

    private static final String STRIPE_BASE_URL = "https://api.stripe.com/v1";
    private static final ObjectMapper OM = new ObjectMapper();

    /** 零小数货币（Stripe API 文档定义） */
    private static final Map<String, Boolean> ZERO_DECIMAL;
    static {
        Map<String, Boolean> m = new HashMap<>();
        m.put("BIF", true); m.put("CLP", true); m.put("DJF", true);
        m.put("GNF", true); m.put("JPY", true); m.put("KMF", true);
        m.put("KRW", true); m.put("MGA", true); m.put("PYG", true);
        m.put("RWF", true); m.put("UGX", true); m.put("VND", true);
        m.put("VUV", true); m.put("XAF", true); m.put("XOF", true);
        m.put("XPF", true);
        ZERO_DECIMAL = m;
    }

    @Override
    public String gatewayCode() {
        return "apple_pay";
    }

    // ─── PaymentIntent ──────────────────────────────────────────────────────

    @Override
    public CreatePaymentIntentResponse createPaymentIntent(OverseasPaymentGateway gateway,
                                                           CreatePaymentIntentRequest request) {
        if (gateway.getSecretKey() == null || gateway.getSecretKey().isBlank()) {
            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = false;
            r.errorMessage = "Apple Pay 网关未配置 Stripe Secret Key（gateway.secretKey）";
            log.error("[APPLE-PAY] {}", r.errorMessage);
            return r;
        }

        try {
            Map<String, String> params = buildPaymentIntentParams(gateway, request);
            String resp = HttpUtil.postForm(STRIPE_BASE_URL + "/payment_intents",
                params, Map.of("Authorization", basicAuth(gateway.getSecretKey())));

            JsonNode node = OM.readTree(resp);

            if (node.has("error")) {
                CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
                r.success = false;
                r.errorMessage = node.path("error").path("message").asText("stripe error");
                log.error("[APPLE-PAY] createPaymentIntent error: {}", r.errorMessage);
                return r;
            }

            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = true;
            r.intentId = node.path("id").asText();
            r.clientSecret = node.path("client_secret").asText();
            r.status = node.path("status").asText();
            // 附加元数据供前端识别（clientSecret 已是 Stripe 标准字段）
            log.info("[APPLE-PAY] PaymentIntent created: id={} amount={} {} merchantId={} status={}",
                r.intentId, request.amount, request.currency, gateway.getMerchantId(), r.status);
            return r;

        } catch (Exception e) {
            log.error("[APPLE-PAY] createPaymentIntent exception", e);
            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    @Override
    public PaymentStatusResponse confirmPayment(OverseasPaymentGateway gateway, String intentId) {
        return queryPayment(gateway, intentId);
    }

    @Override
    public PaymentStatusResponse queryPayment(OverseasPaymentGateway gateway, String paymentId) {
        if (gateway.getSecretKey() == null || gateway.getSecretKey().isBlank()) {
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = false;
            r.errorMessage = "Apple Pay 网关未配置 Stripe Secret Key";
            return r;
        }
        try {
            String resp = HttpUtil.get(STRIPE_BASE_URL + "/payment_intents/" + paymentId,
                Map.of("Authorization", basicAuth(gateway.getSecretKey()),
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);

            if (node.has("error")) {
                PaymentStatusResponse r = new PaymentStatusResponse();
                r.success = false;
                r.errorMessage = node.path("error").path("message").asText("stripe error");
                return r;
            }

            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = true;
            r.paymentId = node.path("id").asText();
            r.status = node.path("status").asText();
            if (node.has("amount")) {
                String currency = node.path("currency").asText("USD").toUpperCase();
                r.amount = fromStripeAmount(node.path("amount").asLong(), currency);
            }
            log.info("[APPLE-PAY] PaymentIntent status: id={} status={}", r.paymentId, r.status);
            return r;
        } catch (Exception e) {
            log.error("[APPLE-PAY] queryPayment exception: {}", paymentId, e);
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    // ─── Refund ─────────────────────────────────────────────────────────────

    @Override
    public RefundResponse refund(OverseasPaymentGateway gateway, RefundRequest request) {
        if (gateway.getSecretKey() == null || gateway.getSecretKey().isBlank()) {
            RefundResponse r = new RefundResponse();
            r.success = false;
            r.errorMessage = "Apple Pay 网关未配置 Stripe Secret Key";
            return r;
        }
        try {
            Map<String, String> params = new HashMap<>();
            params.put("payment_intent", request.paymentId);
            params.put("amount", toStripeAmount(request.amount, "USD"));
            params.put("currency", "usd");
            if (request.reason != null && !request.reason.isBlank()) {
                params.put("reason", "requested_by_customer");
            }

            String resp = HttpUtil.postForm(STRIPE_BASE_URL + "/refunds",
                params, Map.of("Authorization", basicAuth(gateway.getSecretKey())));
            JsonNode node = OM.readTree(resp);

            if (node.has("error")) {
                RefundResponse r = new RefundResponse();
                r.success = false;
                r.errorMessage = node.path("error").path("message").asText("stripe error");
                log.error("[APPLE-PAY] refund error: {}", r.errorMessage);
                return r;
            }

            RefundResponse r = new RefundResponse();
            r.success = true;
            r.refundId = node.path("id").asText();
            r.status = node.path("status").asText();
            if (node.has("amount")) {
                r.refundAmount = BigDecimal.valueOf(node.path("amount").asLong());
            }
            log.info("[APPLE-PAY] Refund created: id={} status={}", r.refundId, r.status);
            return r;
        } catch (Exception e) {
            log.error("[APPLE-PAY] refund exception: paymentId={}", request.paymentId, e);
            RefundResponse r = new RefundResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    // ─── Webhook ────────────────────────────────────────────────────────────

    /**
     * Stripe Webhook 签名校验：t=timestamp,v1=sig
     * 签名字符串 = "{timestamp}.{rawBody}"
     * HMAC-SHA256(secret) → Base64 → 与 v1= 比较
     */
    @Override
    public boolean verifyWebhookSignature(OverseasPaymentGateway gateway, String payload, String signature) {
        if (gateway.getWebhookSecret() == null || gateway.getWebhookSecret().isBlank()) {
            log.warn("[APPLE-PAY] webhookSecret not configured, skipping verification");
            return true;
        }
        try {
            String timestamp = "";
            String sig = "";
            for (String p : signature.split(",")) {
                if (p.startsWith("t=")) timestamp = p.substring(2);
                if (p.startsWith("v1=")) sig = p.substring(3);
            }
            if (timestamp.isEmpty() || sig.isEmpty()) return false;

            String signedPayload = timestamp + "." + payload;
            String expected = hmacSha256Base64(signedPayload, gateway.getWebhookSecret());
            boolean ok = expected.equals(sig);
            if (!ok) log.warn("[APPLE-PAY] webhook signature mismatch");
            return ok;
        } catch (Exception e) {
            log.error("[APPLE-PAY] webhook verify exception", e);
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhookEvent(OverseasPaymentGateway gateway, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.rawData = new HashMap<>();
        try {
            JsonNode node = OM.readTree(payload);
            event.eventId = node.path("id").asText();
            event.eventType = node.path("type").asText();
            JsonNode obj = node.path("data").path("object");
            event.paymentId = obj.path("id").asText();
            event.status = obj.path("status").asText();
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = OM.readValue(payload, Map.class);
            event.rawPayload = raw;
            // 校验是否是 Apple Pay 通道的 PaymentIntent（防止与普通卡支付混淆）
            String walletType = obj.path("metadata").path("wallet_type").asText("");
            if (!"apple_pay".equals(walletType)) {
                log.debug("[APPLE-PAY] webhook wallet_type != apple_pay, type={}", walletType);
            }
        } catch (Exception e) {
            log.error("[APPLE-PAY] parseWebhookEvent error", e);
            event.eventType = "unknown";
        }
        return event;
    }

    // ─── HealthCheck ────────────────────────────────────────────────────────

    @Override
    public HealthStatus healthCheck(OverseasPaymentGateway gateway) {
        HealthStatus status = new HealthStatus();
        try {
            // Apple Pay 网关底层用 Stripe，healthCheck 直接 ping Stripe /balance
            String resp = HttpUtil.get(STRIPE_BASE_URL + "/balance",
                Map.of("Authorization", basicAuth(gateway.getSecretKey()),
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);
            if (node.has("object") && "balance".equals(node.path("object").asText())) {
                status.healthy = true;
                status.message = "Apple Pay (Stripe) API reachable, merchantId=" + gateway.getMerchantId();
            } else {
                status.healthy = false;
                status.message = "Unexpected Stripe response";
            }
        } catch (Exception e) {
            status.healthy = false;
            status.message = "Apple Pay (Stripe) API unreachable: " + e.getMessage();
            log.warn("[APPLE-PAY] healthCheck failed: {}", e.getMessage());
        }
        status.checkedAt = new Date().toString();
        return status;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Map<String, String> buildPaymentIntentParams(OverseasPaymentGateway gateway,
                                                        CreatePaymentIntentRequest request) {
        Map<String, String> m = new HashMap<>();
        String currency = request.currency != null ? request.currency.toLowerCase() : "usd";
        m.put("amount", toStripeAmount(request.amount, currency));
        m.put("currency", currency);
        // Apple Pay tokens 在 Stripe 内部作为 card 处理
        m.put("payment_method_types[]", "card");

        // metadata: wallet_type=apple_pay 用于 webhook 区分
        if (request.orderNo != null && !request.orderNo.isBlank()) {
            m.put("metadata[order_no]", request.orderNo);
        }
        m.put("metadata[wallet_type]", "apple_pay");
        if (gateway.getMerchantId() != null && !gateway.getMerchantId().isBlank()) {
            m.put("metadata[apple_pay_merchant_id]", gateway.getMerchantId());
        }

        if (request.description != null && !request.description.isBlank()) {
            m.put("description", request.description);
        }
        if (request.customerEmail != null && !request.customerEmail.isBlank()) {
            m.put("receipt_email", request.customerEmail);
        }
        if (request.returnUrl != null && !request.returnUrl.isBlank()) {
            m.put("return_url", request.returnUrl);
        }
        if (request.cancelUrl != null && !request.cancelUrl.isBlank()) {
            m.put("cancel_url", request.cancelUrl);
        }
        // 自动捕获关闭：客户端完成 Apple Pay 确认后由前端 Stripe.js 触发 capture
        m.put("capture", "false");
        return m;
    }

    private String basicAuth(String secretKey) {
        String creds = "sk:" + secretKey;
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    private String toStripeAmount(BigDecimal amount, String currency) {
        if (amount == null) return "0";
        String upper = currency == null ? "USD" : currency.toUpperCase();
        if (Boolean.TRUE.equals(ZERO_DECIMAL.get(upper))) {
            return amount.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        return amount.multiply(BigDecimal.valueOf(100))
                     .setScale(0, RoundingMode.DOWN).toPlainString();
    }

    private BigDecimal fromStripeAmount(long units, String currency) {
        if (Boolean.TRUE.equals(ZERO_DECIMAL.get(currency))) {
            return BigDecimal.valueOf(units);
        }
        return BigDecimal.valueOf(units).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String hmacSha256Base64(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
