package com.meiyetec.payment;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import com.meiyetec.entity.OverseasPaymentGateway;

public interface OverseasPaymentAdapter {

    String gatewayCode();

    default boolean enabled() {
        return true;
    }

    CreatePaymentIntentResponse createPaymentIntent(OverseasPaymentGateway gateway, CreatePaymentIntentRequest request);

    PaymentStatusResponse confirmPayment(OverseasPaymentGateway gateway, String intentId);

    RefundResponse refund(OverseasPaymentGateway gateway, RefundRequest request);

    PaymentStatusResponse queryPayment(OverseasPaymentGateway gateway, String paymentId);

    boolean verifyWebhookSignature(OverseasPaymentGateway gateway, String payload, String signature);

    WebhookEvent parseWebhookEvent(OverseasPaymentGateway gateway, String payload);

    HealthStatus healthCheck(OverseasPaymentGateway gateway);

    class HealthStatus {
        public boolean healthy;
        public String message;
        public String checkedAt;
    }

    class CreatePaymentIntentRequest {
        public String orderNo;
        public BigDecimal amount;
        public String currency;
        public String customerId;
        public String customerEmail;
        public String description;
        public String returnUrl;
        public String cancelUrl;
    }

    class CreatePaymentIntentResponse {
        public boolean success;
        public String intentId;
        public String paymentIntentId;
        public String clientSecret;
        public String approvalUrl;
        public String status;
        public String errorMessage;
    }

    class PaymentStatusResponse {
        public boolean success;
        public String paymentId;
        public String status;
        public BigDecimal amount;
        public String errorMessage;
        public String rawResponse;
    }

    class RefundRequest {
        public String paymentId;
        public BigDecimal amount;
        public String reason;
    }

    class RefundResponse {
        public boolean success;
        public String refundId;
        public BigDecimal amount;
        public BigDecimal refundAmount;
        public String status;
        public String errorMessage;
    }

    class WebhookEvent {
        public String eventType;
        public String eventId;
        public String paymentId;
        public String status;
        public Map<String, Object> rawPayload;
        public Map<String, Object> rawData;
    }
}
