package com.meiyetec.service;

import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 补齐-P1-9 Stripe/Square/BNPL 国际支付（演示模式）
 */
@Slf4j
@RestController
@RequestMapping("/intl-payment")
public class IntlPaymentService {

    private static final Map<String, Map<String, Object>> GATEWAYS = new ConcurrentHashMap<>();
    private static final List<Map<String, Object>> CHARGES = Collections.synchronizedList(new ArrayList<>());
    private static final Random RAND = new Random();

    static {
        Map<String, Object> stripe = new HashMap<>(); stripe.put("code", "STRIPE"); stripe.put("name", "Stripe"); stripe.put("fee", "2.9% + 0.30"); stripe.put("currency", "USD/EUR/GBP"); GATEWAYS.put("STRIPE", stripe);
        Map<String, Object> square = new HashMap<>(); square.put("code", "SQUARE"); square.put("name", "Square"); square.put("fee", "2.6% + 0.10"); square.put("currency", "USD/GBP/JPY"); GATEWAYS.put("SQUARE", square);
        Map<String, Object> paypal = new HashMap<>(); paypal.put("code", "PAYPAL"); paypal.put("name", "PayPal"); paypal.put("fee", "3.4% + 0.30"); paypal.put("currency", "USD/EUR"); GATEWAYS.put("PAYPAL", paypal);
        Map<String, Object> afterpay = new HashMap<>(); afterpay.put("code", "AFTERPAY"); afterpay.put("name", "Afterpay (BNPL)"); afterpay.put("fee", "4-6%"); afterpay.put("currency", "USD/AUD"); afterpay.put("feature", "4 期免息"); GATEWAYS.put("AFTERPAY", afterpay);
        Map<String, Object> klarna = new HashMap<>(); klarna.put("code", "KLARNA"); klarna.put("name", "Klarna (BNPL)"); klarna.put("fee", "3-4%"); klarna.put("currency", "EUR/USD"); klarna.put("feature", "3/6/12 期免息"); GATEWAYS.put("KLARNA", klarna);
    }

    @GetMapping("/gateways")
    public Result<List<Map<String, Object>>> listGateways() {
        return Result.success(new ArrayList<>(GATEWAYS.values()));
    }

    @PostMapping("/charge")
    public Result<Map<String, Object>> charge(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String gateway = (String) body.get("gateway");
        if (gateway == null || !GATEWAYS.containsKey(gateway)) return Result.error(BizCode.BAD_REQUEST, "不支持的网关");
        Number amount = (Number) body.get("amount");
        if (amount == null) return Result.error(BizCode.BAD_REQUEST, "amount 必填");

        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("id", System.currentTimeMillis());
        tx.put("tenantId", tenantId);
        tx.put("gateway", gateway);
        tx.put("amount", amount);
        tx.put("currency", body.getOrDefault("currency", "USD"));
        tx.put("orderId", body.get("orderId"));
        tx.put("status", RAND.nextInt(100) < 5 ? "DECLINED" : "SUCCEEDED");
        tx.put("txnNo", gateway.toUpperCase() + System.currentTimeMillis());
        tx.put("createdAt", LocalDateTime.now());
        CHARGES.add(tx);
        return Result.success("已发起支付", tx);
    }

    @GetMapping("/charges")
    public Result<List<Map<String, Object>>> listCharges() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(CHARGES.stream().filter(c -> tenantId.equals(c.get("tenantId"))).toList());
    }

    @PostMapping("/{id}/refund")
    public Result<Map<String, Object>> refund(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Optional<Map<String, Object>> tx = CHARGES.stream().filter(c -> id.equals(c.get("id"))).findFirst();
        if (tx.isEmpty()) return Result.error(BizCode.NOT_FOUND, "交易不存在");
        Map<String, Object> t = tx.get();
        t.put("status", "REFUNDED");
        t.put("refundedAt", LocalDateTime.now());
        t.put("refundReason", body == null ? null : body.get("reason"));
        return Result.success("已退款", t);
    }
}