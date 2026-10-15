package com.meiyetec.payment.bnpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock BNPL Provider — 保留当前 BnplServiceImpl 的行为，
 * 作为 Klarna / Afterpay 未配置时的降级 Fallback。
 *
 * <p>生产环境请配置 {@code bnpl.klarna.*} 或 {@code bnpl.afterpay.*}
 * 凭证，使对应 {@link KlarnaBnplProvider} / {@link AfterpayBnplProvider} 生效。
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "realBnplProvider")
public class MockBnplProvider implements BnplProvider {

    // 可被 YAML 注入（如果将来需要），目前完全走内存逻辑
    private final String providerCode;

    public MockBnplProvider() {
        this("MOCK");
    }

    /** 允许子类通过构造函数注入不同 code（ATOME / AFFIRM） */
    protected MockBnplProvider(String providerCode) {
        this.providerCode = providerCode;
        log.info("[BNPL-MOCK] Initialized as fallback provider: {}", providerCode);
    }

    @Override
    public String providerCode() {
        return providerCode;
    }

    @Override
    public boolean isConfigured() {
        return false; // mock 总认为未配置
    }

    @Override
    public CreditResult queryCredit(String phone) {
        int[] periods = supportedPeriods();
        BigDecimal feeRate = feeRate();

        // 模拟额度：手机号 hash 决定额度区间
        BigDecimal creditLimit = BigDecimal.valueOf(1000 + Math.abs(phone.hashCode() % 9000));

        return CreditResult.builder()
                .success(true)
                .creditLimit(creditLimit)
                .availableLimit(creditLimit)
                .status("APPROVED")
                .supportedPeriods(periods)
                .feeRate(feeRate)
                .build();
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        int installments = req.getInstallments() != null ? req.getInstallments() : 3;
        BigDecimal amount = req.getAmount();
        BigDecimal feeRate = feeRate();
        BigDecimal platformFee = amount.multiply(feeRate).setScale(2, java.math.RoundingMode.HALF_UP);

        // Mock 分期计划：每期等额
        BigDecimal installmentAmount = amount.divide(BigDecimal.valueOf(installments), 2, java.math.RoundingMode.HALF_UP);
        Map<Integer, InstallmentPlan> plans = new LinkedHashMap<>();
        for (int i = 1; i <= installments; i++) {
            plans.put(i, InstallmentPlan.builder()
                    .seq(i)
                    .amount(installmentAmount)
                    .dueDate(LocalDate.now().plusMonths(i).toString())
                    .status("PENDING")
                    .build());
        }

        String providerOrderNo = providerCode + "-" + System.currentTimeMillis();

        log.info("[BNPL-MOCK] createOrder bizOrderNo={} provider={} amount={} installments={}",
                req.getBizOrderNo(), providerCode, amount, installments);

        return CreateOrderResult.builder()
                .success(true)
                .providerOrderNo(providerOrderNo)
                .redirectUrl(null) // mock 不返回跳转 URL，由本地处理
                .installments(plans)
                .platformFee(platformFee)
                .localOrderNo(req.getBizOrderNo())
                .build();
    }

    @Override
    public WebhookResult handleWebhook(String eventType, String payload, String signature, String secret) {
        // Mock 不验签，直接解析事件类型
        Integer newStatus = mapEventToStatus(eventType);

        log.info("[BNPL-MOCK] handleWebhook eventType={} newStatus={}", eventType, newStatus);

        return WebhookResult.builder()
                .signatureValid(true)
                .replayDetected(false)
                .newStatus(newStatus)
                .eventType(eventType)
                .rawPayload(payload)
                .build();
    }

    @Override
    public RefundResult refund(String providerOrderNo, BigDecimal amount, String reason) {
        log.info("[BNPL-MOCK] refund orderNo={} amount={} reason={}", providerOrderNo, amount, reason);
        return RefundResult.builder()
                .success(false)
                .errorCode("REFUND_NOT_SUPPORTED")
                .errorMsg("Mock 模式不支持 API 退款")
                .build();
    }

    // ---- 子类可覆盖 ----

    protected int[] supportedPeriods() {
        return new int[]{3, 6, 12};
    }

    protected BigDecimal feeRate() {
        return BigDecimal.valueOf(0.029);
    }

    protected static Integer mapEventToStatus(String eventType) {
        return switch (eventType) {
            case "order.confirmed"  -> 2;
            case "order.shipped"    -> 3;
            case "order.delivered"  -> 4;
            case "order.completed"  -> 5;
            case "order.cancelled"  -> 6;
            case "order.refunded"   -> 8;
            default                 -> null;
        };
    }
}
