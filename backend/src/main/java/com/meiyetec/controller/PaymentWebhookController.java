package com.meiyetec.controller;

import com.meiyetec.entity.PaymentWebhookLog;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.PaymentWebhookLogService;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/overseas-payment/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookLogService webhookLogService;

    @PostMapping("/{gatewayCode}")
    public void handleWebhook(@PathVariable String gatewayCode,
                              @RequestBody String payload,
                              HttpServletRequest request) {
        String signature = request.getHeader("Stripe-Signature");
        if (signature == null) signature = request.getHeader("Square-Signature");
        if (signature == null) signature = request.getHeader("PAYPAL-TRANSMISSION-SIG");
        String sourceIp = request.getRemoteAddr();
        log.info("Webhook received: gateway={} ip={} signature={}", gatewayCode, sourceIp, signature != null);
        webhookLogService.processWebhook(gatewayCode, payload, signature, sourceIp);
    }

    @GetMapping("/logs")
    public Result<List<PaymentWebhookLog>> listLogs(@RequestParam(required = false) String gatewayCode,
                                            @RequestParam(required = false) String processStatus) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(webhookLogService.queryList(gatewayCode, processStatus, tenantId));
    }
}
