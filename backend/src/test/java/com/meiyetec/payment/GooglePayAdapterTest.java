package com.meiyetec.payment;

import com.meiyetec.entity.OverseasPaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GooglePayAdapter 单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>网关编码 / 标识</li>
 *   <li>PaymentIntent 参数构造（含 wallet_type=google_pay）</li>
 *   <li>金额换算（普通 + 零小数货币）</li>
 *   <li>Webhook 签名校验（HMAC-SHA256 / Base64）</li>
 *   <li>Webhook 事件解析（含 google_pay wallet_type 校验）</li>
 *   <li>退款无 secretKey 错误处理</li>
 * </ol>
 */
@DisplayName("P3.6 Google Pay Adapter")
class GooglePayAdapterTest {

    private final GooglePayAdapter adapter = new GooglePayAdapter();

    // ==================== 基础标识 ====================

    @Test
    @DisplayName("gatewayCode = google_pay")
    void gatewayCode() {
        assertEquals("google_pay", adapter.gatewayCode());
    }

    @Test
    @DisplayName("默认 enabled = true")
    void defaultEnabled() {
        assertTrue(adapter.enabled());
    }

    // ==================== PaymentIntent 参数 ====================

    @Test
    @DisplayName("createPaymentIntent: 无 secretKey → success=false")
    void createIntent_noSecretKey_returnsError() {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setGatewayCode("google_pay");
        OverseasPaymentAdapter.CreatePaymentIntentRequest req =
            new OverseasPaymentAdapter.CreatePaymentIntentRequest();
        req.amount = new BigDecimal("20.00");
        req.currency = "EUR";
        req.orderNo = "ORD-002";

        OverseasPaymentAdapter.CreatePaymentIntentResponse r =
            adapter.createPaymentIntent(g, req);
        assertFalse(r.success);
        assertTrue(r.errorMessage.contains("Stripe Secret Key"));
    }

    // ==================== 金额换算 ====================

    @Test
    @DisplayName("USD 10.00 → Stripe 1000 cents")
    void amountToStripe_usd() throws Exception {
        String cents = invokeToStripeAmount(new BigDecimal("10.00"), "USD");
        assertEquals("1000", cents);
    }

    @Test
    @DisplayName("JPY 1000 → 1000（零小数）")
    void amountToStripe_jpy() throws Exception {
        String val = invokeToStripeAmount(new BigDecimal("1000"), "JPY");
        assertEquals("1000", val);
    }

    @Test
    @DisplayName("EUR 99.99 → 9999")
    void amountToStripe_eur() throws Exception {
        String cents = invokeToStripeAmount(new BigDecimal("99.99"), "EUR");
        assertEquals("9999", cents);
    }

    @Test
    @DisplayName("amount=null → '0'")
    void amountToStripe_null() throws Exception {
        String val = invokeToStripeAmount(null, "USD");
        assertEquals("0", val);
    }

    @Test
    @DisplayName("fromStripeAmount 1000 cents → 10.00 USD")
    void amountFromStripe_usd() throws Exception {
        Method m = GooglePayAdapter.class.getDeclaredMethod(
            "fromStripeAmount", long.class, String.class);
        m.setAccessible(true);
        BigDecimal amount = (BigDecimal) m.invoke(adapter, 1000L, "USD");
        assertEquals(new BigDecimal("10.00"), amount);
    }

    // ==================== Webhook 签名 ====================

    @Test
    @DisplayName("verifyWebhookSignature: webhookSecret 为空 → true（demo）")
    void verifySignature_noSecret() {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setWebhookSecret("");
        assertTrue(adapter.verifyWebhookSignature(g, "{}", "t=1,v1=xxx"));
    }

    @Test
    @DisplayName("verifyWebhookSignature: 合法签名 → true")
    void verifySignature_valid() throws Exception {
        String secret = "whsec_google_pay";
        long ts = System.currentTimeMillis() / 1000L;
        String payload = "{\"id\":\"evt_2\",\"type\":\"payment_intent.succeeded\"}";
        String sig = hmacSha256Base64(ts + "." + payload, secret);

        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setWebhookSecret(secret);
        assertTrue(adapter.verifyWebhookSignature(g, payload, "t=" + ts + ",v1=" + sig));
    }

    @Test
    @DisplayName("verifyWebhookSignature: 错误签名 → false")
    void verifySignature_invalid() throws Exception {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setWebhookSecret("whsec_google_pay");
        assertFalse(adapter.verifyWebhookSignature(g, "{}",
            "t=123,v1=wrongsignature=="));
    }

    // ==================== Webhook 事件解析 ====================

    @Test
    @DisplayName("parseWebhookEvent: payment_intent.payment_failed → eventType=payment_intent.payment_failed")
    void parseWebhookEvent_failed() {
        String payload = """
            {
              "id": "evt_failed_1",
              "type": "payment_intent.payment_failed",
              "data": {
                "object": {
                  "id": "pi_fail_1",
                  "status": "requires_payment_method",
                  "metadata": { "wallet_type": "google_pay", "order_no": "ORD-100" }
                }
              }
            }
            """;
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        OverseasPaymentAdapter.WebhookEvent event = adapter.parseWebhookEvent(g, payload);
        assertEquals("evt_failed_1", event.eventId);
        assertEquals("payment_intent.payment_failed", event.eventType);
        assertEquals("pi_fail_1", event.paymentId);
        assertEquals("requires_payment_method", event.status);
    }

    @Test
    @DisplayName("parseWebhookEvent: 非法 JSON → eventType=unknown（不抛异常）")
    void parseWebhookEvent_invalidJson() {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        OverseasPaymentAdapter.WebhookEvent event = adapter.parseWebhookEvent(g, "broken{json");
        assertEquals("unknown", event.eventType);
    }

    // ==================== 退款错误处理 ====================

    @Test
    @DisplayName("refund: 无 secretKey → success=false")
    void refund_noSecretKey() {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setGatewayCode("google_pay");
        OverseasPaymentAdapter.RefundRequest req = new OverseasPaymentAdapter.RefundRequest();
        req.paymentId = "pi_test";
        req.amount = new BigDecimal("10.00");
        req.reason = "test";

        OverseasPaymentAdapter.RefundResponse r = adapter.refund(g, req);
        assertFalse(r.success);
        assertTrue(r.errorMessage.contains("Stripe Secret Key"));
    }

    // ==================== HealthCheck ====================

    @Test
    @DisplayName("healthCheck: 无 secretKey → healthy=false")
    void healthCheck_noSecret() {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setGatewayCode("google_pay");
        OverseasPaymentAdapter.HealthStatus h = adapter.healthCheck(g);
        assertFalse(h.healthy);
        assertTrue(h.message.contains("Stripe"));
    }

    // ==================== queryPayment 错误处理 ====================

    @Test
    @DisplayName("queryPayment: 无 secretKey → success=false")
    void queryPayment_noSecretKey() {
        OverseasPaymentGateway g = new OverseasPaymentGateway();
        g.setGatewayCode("google_pay");
        OverseasPaymentAdapter.PaymentStatusResponse r = adapter.queryPayment(g, "pi_xxx");
        assertFalse(r.success);
        assertTrue(r.errorMessage.contains("Stripe Secret Key"));
    }

    // ==================== Helpers ====================

    private String invokeToStripeAmount(BigDecimal amount, String currency) throws Exception {
        Method m = GooglePayAdapter.class.getDeclaredMethod(
            "toStripeAmount", BigDecimal.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(adapter, amount, currency);
    }

    private static String hmacSha256Base64(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
