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
 * Stripe Payment Adapter
 *
 * Uses Stripe REST API v1:
 *   Base URL: https://api.stripe.com/v1
 *   Auth:     Basic Auth with secret_key (base64("sk:secret_key"))
 *
 * API endpoints:
 *   POST   /payment_intents              — create payment intent
 *   GET    /payment_intents/{id}         — retrieve intent / confirm status
 *   POST   /payment_intents/{id}/confirm — confirm (capture)
 *   POST   /refunds                     — create refund
 *
 * Amount convention: Stripe uses smallest currency unit (cents), no decimal.
 * Supported currencies: USD, EUR, GBP, CAD, AUD, JPY, SGD, HKD, CNY, etc.
 */
@Slf4j
@Component("stripeAdapter")
public class StripeAdapter implements OverseasPaymentAdapter {

    private static final String BASE_URL = "https://api.stripe.com/v1";
    private static final ObjectMapper OM = new ObjectMapper();

    /** Currencies that are zero-decimal (no subdivision). */
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

    // ─── PaymentIntent ────────────────────────────────────────────────────────

    @Override
    public CreatePaymentIntentResponse createPaymentIntent(OverseasPaymentGateway gateway,
                                                           CreatePaymentIntentRequest request) {
        String url = BASE_URL + "/payment_intents";

        try {
            Map<String, String> params = paymentIntentParams(request);
            String resp = HttpUtil.postForm(url, params,
                Map.of("Authorization", basicAuth(gateway.getSecretKey())));

            JsonNode node = OM.readTree(resp);

            if (node.has("error")) {
                CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
                r.success = false;
                r.errorMessage = node.path("error").path("message").asText("stripe error");
                log.error("[STRIPE] createPaymentIntent error: {}", r.errorMessage);
                return r;
            }

            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = true;
            r.intentId = node.path("id").asText();
            r.clientSecret = node.path("client_secret").asText();
            r.status = node.path("status").asText();
            log.info("[STRIPE] PaymentIntent created: id={} amount={} {} status={}",
                r.intentId, request.amount, request.currency, r.status);
            return r;

        } catch (Exception e) {
            log.error("[STRIPE] createPaymentIntent exception", e);
            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    // ─── Confirm / Query ─────────────────────────────────────────────────────

    @Override
    public PaymentStatusResponse confirmPayment(OverseasPaymentGateway gateway, String intentId) {
        return queryPayment(gateway, intentId);
    }

    @Override
    public PaymentStatusResponse queryPayment(OverseasPaymentGateway gateway, String paymentId) {
        String url = BASE_URL + "/payment_intents/" + paymentId;

        try {
            String resp = HttpUtil.get(url,
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
                r.amount = BigDecimal.valueOf(node.path("amount").asLong());
            }
            log.info("[STRIPE] PaymentIntent status: id={} status={}", r.paymentId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[STRIPE] queryPayment exception: {}", paymentId, e);
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    // ─── Refund ──────────────────────────────────────────────────────────────

    @Override
    public RefundResponse refund(OverseasPaymentGateway gateway, RefundRequest request) {
        String url = BASE_URL + "/refunds";

        try {
            Map<String, String> params = new HashMap<>();
            params.put("payment_intent", request.paymentId);
            params.put("amount", toStripeAmount(request.amount, "USD"));
            params.put("currency", "usd");
            if (request.reason != null && !request.reason.isBlank()) {
                params.put("reason", "requested_by_customer");
            }

            String resp = HttpUtil.postForm(url, params,
                Map.of("Authorization", basicAuth(gateway.getSecretKey())));
            JsonNode node = OM.readTree(resp);

            if (node.has("error")) {
                RefundResponse r = new RefundResponse();
                r.success = false;
                r.errorMessage = node.path("error").path("message").asText("stripe error");
                log.error("[STRIPE] refund error: {}", r.errorMessage);
                return r;
            }

            RefundResponse r = new RefundResponse();
            r.success = true;
            r.refundId = node.path("id").asText();
            r.status = node.path("status").asText();
            if (node.has("amount")) {
                r.refundAmount = BigDecimal.valueOf(node.path("amount").asLong());
            }
            log.info("[STRIPE] Refund created: id={} status={}", r.refundId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[STRIPE] refund exception: paymentId={}", request.paymentId, e);
            RefundResponse r = new RefundResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    // ─── Webhook ────────────────────────────────────────────────────────────

    @Override
    public boolean verifyWebhookSignature(OverseasPaymentGateway gateway, String payload, String signature) {
        if (gateway.getWebhookSecret() == null || gateway.getWebhookSecret().isBlank()) {
            log.warn("[STRIPE] webhookSecret not configured, skipping verification");
            return true;
        }
        try {
            // Stripe webhook signature format: t=<timestamp>,v1=<sig>
            String timestamp = "";
            String sig = "";
            for (String p : signature.split(",")) {
                if (p.startsWith("t=")) timestamp = p.substring(2);
                if (p.startsWith("v1=")) sig = p.substring(3);
            }
            if (timestamp.isEmpty() || sig.isEmpty()) return false;

            String signedPayload = timestamp + "." + payload;
            String expected = hmacSha256(signedPayload, gateway.getWebhookSecret());
            boolean ok = sig.equals(expected);
            if (!ok) log.warn("[STRIPE] webhook signature mismatch");
            return ok;
        } catch (Exception e) {
            log.error("[STRIPE] webhook verify exception", e);
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
            JsonNode data = node.path("data").path("object");
            event.paymentId = data.path("id").asText();
            event.status = data.path("status").asText();
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = OM.readValue(payload, Map.class);
            event.rawPayload = raw;
        } catch (Exception e) {
            log.error("[STRIPE] parseWebhookEvent error", e);
            event.eventType = "unknown";
        }
        return event;
    }

    // ─── HealthCheck ────────────────────────────────────────────────────────

    @Override
    public HealthStatus healthCheck(OverseasPaymentGateway gateway) {
        HealthStatus status = new HealthStatus();
        try {
            String resp = HttpUtil.get(BASE_URL + "/balance",
                Map.of("Authorization", basicAuth(gateway.getSecretKey()),
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);
            if (node.has("object") && "balance".equals(node.path("object").asText())) {
                status.healthy = true;
                status.message = "Stripe API reachable";
            } else {
                status.healthy = false;
                status.message = "Unexpected response";
            }
        } catch (Exception e) {
            status.healthy = false;
            status.message = "Stripe API unreachable: " + e.getMessage();
            log.warn("[STRIPE] healthCheck failed: {}", e.getMessage());
        }
        status.checkedAt = new Date().toString();
        return status;
    }

    @Override
    public String gatewayCode() {
        return "stripe";
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String basicAuth(String secretKey) {
        String creds = "sk:" + secretKey;
        return "Basic " + Base64.getEncoder().encodeToString(
            creds.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convert BigDecimal amount to Stripe smallest-unit integer.
     * Zero-decimal currencies stay as-is.
     */
    private String toStripeAmount(BigDecimal amount, String currency) {
        if (amount == null) return "0";
        if (Boolean.TRUE.equals(ZERO_DECIMAL.get(currency.toUpperCase()))) {
            return amount.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        return amount.multiply(BigDecimal.valueOf(100))
                     .setScale(0, RoundingMode.DOWN).toPlainString();
    }

    private Map<String, String> paymentIntentParams(CreatePaymentIntentRequest request) {
        Map<String, String> m = new HashMap<>();
        m.put("amount", toStripeAmount(request.amount, request.currency));
        m.put("currency", request.currency != null ? request.currency.toLowerCase() : "usd");
        if (request.description != null && !request.description.isBlank()) {
            m.put("description", request.description);
        }
        if (request.orderNo != null && !request.orderNo.isBlank()) {
            m.put("metadata[order_no]", request.orderNo);
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
        // Auto-capture off — client handles 3DS then we query status via confirmPayment
        m.put("capture", "false");
        return m;
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
