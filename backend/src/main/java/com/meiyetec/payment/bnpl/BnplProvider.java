package com.meiyetec.payment.bnpl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * P2-16 BNPL Provider SPI — 先买后付通道抽象
 *
 * <p>统一接口：同一接口可切换任意 BNPL 提供商（Klarna / Afterpay / Atome / Affirm），
 * 未配置时自动降级为本地 Mock（保持现有行为）。
 *
 * <p>使用方式：
 * <pre>
 * BnplProvider p = bnplProviderRouter.get(provider);
 * CreditResult r = p.queryCredit(memberPhone);
 * CreateOrderResult r = p.createOrder(orderReq);
 * p.handleWebhook(eventType, payload, signature, secret);
 * </pre>
 *
 * <p>Provider 代码约定：
 * <ul>
 *   <li>{@code KLARNA}   → {@link KlarnaBnplProvider}</li>
 *   <li>{@code AFTERPAY} → {@link AfterpayBnplProvider}</li>
 *   <li>{@code ATOME}    → mock fallback</li>
 *   <li>{@code AFFIRM}   → mock fallback</li>
 * </ul>
 *
 * <p>所有实现必须实现此接口并在类上加 {@code @Component}，
 * Spring 按 providerCode 注册到 {@code Map&lt;String, BnplProvider&gt;}。
 */
public interface BnplProvider {

    /**
     * Provider 唯一代码（KLARNA / AFTERPAY / ATOME / AFFIRM）
     */
    String providerCode();

    /**
     * 是否已配置真实凭证（配置了 apiKey / merchantId 等）
     */
    boolean isConfigured();

    // ====================================================================
    // 1. 授信查询
    // ====================================================================

    /**
     * 查询用户授信额度
     *
     * <p>真实调用：Klarna → `/customer/v1/sessions`（POST）
     *              Afterpay → `/v1/customers/{phone}/credit`（GET）
     *
     * @param phone  用户手机号
     * @return 授信结果（creditLimit / availableLimit / status）
     */
    CreditResult queryCredit(String phone);

    // ====================================================================
    // 2. 创建订单
    // ====================================================================

    /**
     * 创建 BNPL 订单
     *
     * <p>真实调用：Klarna → `/checkout/v3/orders`（POST）
     *              Afterpay → `/v1/orders`（POST）
     *
     * <p>返回结果应包含：
     * <ul>
     *   <li>平台订单号（用于回调关联）</li>
     *   <li>H5 收银台跳转 URL（Klarna Redirect / Afterpay Checkout URL）</li>
     *   <li>分期计划（各期到期日 + 金额）</li>
     *   <li>平台手续费</li>
     * </ul>
     *
     * @param req 订单创建请求
     * @return 平台订单号 + 跳转 URL + 分期计划
     */
    CreateOrderResult createOrder(CreateOrderRequest req);

    // ====================================================================
    // 3. Webhook 处理
    // ====================================================================

    /**
     * 处理来自 BNPL 平台的异步通知（webhook）
     *
     * <p>真实调用时：先验签（防止伪造），再更新本地订单状态。
     *
     * <p>支持的事件类型：
     * <ul>
     *   <li>{@code order.confirmed}  — 平台确认订单</li>
     *   <li>{@code order.shipped}   — 商家发货（仅 Klarna）</li>
     *   <li>{@code order.delivered} — 买家确认收货</li>
     *   <li>{@code order.completed} — 订单完成（所有期还款成功）</li>
     *   <li>{@code order.cancelled} — 订单取消</li>
     *   <li>{@code order.refunded}  — 退款</li>
     * </ul>
     *
     * @param eventType  事件类型
     * @param payload    通知 body（JSON）
     * @param signature  签名/Token（用于验签）
     * @param secret     平台分配的 webhook 密钥（用于验签）
     * @return 验签结果 + 状态变更建议
     */
    WebhookResult handleWebhook(String eventType, String payload, String signature, String secret);

    // ====================================================================
    // 4. 退款（可选）
    // ====================================================================

    /**
     * 向平台申请退款（仅部分支持）
     *
     * <p>Klarna 支持全退 / 部分退；Afterpay 仅支持全退。
     * 未实现时返回 {@code success=false} + errorCode。
     */
    default RefundResult refund(String providerOrderNo, BigDecimal amountCents, String reason) {
        return RefundResult.builder()
                .success(false)
                .errorCode("REFUND_NOT_SUPPORTED")
                .errorMsg("该平台暂不支持 API 退款，请在平台后台手动处理")
                .build();
    }

    // ====================================================================
    // Request / Response DTOs
    // ====================================================================

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CreateOrderRequest {
        /** 我方商户订单号（用于关联） */
        private String bizOrderNo;
        /** 会员 ID */
        private Long memberId;
        /** 会员姓名 */
        private String memberName;
        /** 会员手机 */
        private String memberPhone;
        /** 会员邮箱 */
        private String memberEmail;
        /** 订单金额（元） */
        private BigDecimal amount;
        /** 币种（CNY / USD / EUR） */
        private String currency;
        /** 分期期数 */
        private Integer installments;
        /** 商品描述 */
        private String description;
        /** 回调地址（平台通知我方用） */
        private String notifyUrl;
        /** 成功跳转地址（用户支付后跳转） */
        private String successUrl;
        /** 取消跳转地址（用户取消后跳转） */
        private String cancelUrl;
        /** 租户 ID（用于记账） */
        private Long tenantId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CreateOrderResult {
        /** 是否成功 */
        private boolean success;
        /** 平台订单号（回调关联用） */
        private String providerOrderNo;
        /** H5 收银台跳转 URL（null 表示静默下单/嵌入式） */
        private String redirectUrl;
        /** 分期计划（seq → dueDate + amount） */
        private Map<Integer, InstallmentPlan> installments;
        /** 平台手续费（元） */
        private BigDecimal platformFee;
        /** 我方 localOrderNo（回传用于关联我方系统） */
        private String localOrderNo;
        /** 错误码（失败时填） */
        private String errorCode;
        /** 错误信息（失败时填） */
        private String errorMsg;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class InstallmentPlan {
        private Integer seq;
        /** 到期日 */
        private String dueDate;
        /** 应还金额（元） */
        private BigDecimal amount;
        /** PENDING / PAID / OVERDUE / CANCELLED */
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class CreditResult {
        private boolean success;
        /** 授信总额度 */
        private BigDecimal creditLimit;
        /** 可用额度 */
        private BigDecimal availableLimit;
        /** APPROVED / DECLINED / PENDING */
        private String status;
        /** 支持的分期期数 */
        private int[] supportedPeriods;
        /** 平台收续费率（0.029 等） */
        private BigDecimal feeRate;
        /** 错误码 */
        private String errorCode;
        /** 错误信息 */
        private String errorMsg;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class WebhookResult {
        /** 签名验证是否通过 */
        private boolean signatureValid;
        /** 是否为重复通知（防重放） */
        private boolean replayDetected;
        /** 建议的状态变更（null 表示不变） */
        private Integer newStatus;
        /** 平台订单号（用于关联本地） */
        private String providerOrderNo;
        /** 事件类型 */
        private String eventType;
        /** 原始 payload（JSON string） */
        private String rawPayload;
        /** 错误码 */
        private String errorCode;
        /** 错误信息 */
        private String errorMsg;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    class RefundResult {
        private boolean success;
        /** 平台退款单号 */
        private String providerRefundNo;
        /** 退款金额 */
        private BigDecimal refundedAmount;
        /** 错误码 */
        private String errorCode;
        /** 错误信息 */
        private String errorMsg;
    }
}
