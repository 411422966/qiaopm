package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.BnplOrder;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.BnplService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/bnpl")
@RequiredArgsConstructor
@SysOpLog(module = "BNPL")
public class BnplController {

    private final BnplService bnplService;

    @GetMapping("/credit")
    @RequirePermission("bnpl:credit")
    public Result<Map<String, Object>> queryCredit(@RequestParam String provider,
                                                   @RequestParam String phone) {
        return bnplService.queryCredit(provider, phone, TenantContextHolder.get().getTenantId());
    }

    @PostMapping("/order")
    @RequirePermission("bnpl:create")
    public Result<BnplOrder> createOrder(@RequestParam Long memberId,
                                         @RequestParam String provider,
                                         @RequestParam String bizOrderNo,
                                         @RequestParam BigDecimal amount,
                                         @RequestParam(defaultValue = "CNY") String currency,
                                         @RequestParam(defaultValue = "3") Integer installments) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        return bnplService.createOrder(memberId, provider, bizOrderNo, amount, currency, installments, tenantId);
    }

    @GetMapping("/orders")
    @RequirePermission("bnpl:list")
    public Result<?> listOrders(@RequestParam(required = false) String provider,
                                @RequestParam(required = false) Long memberId,
                                @RequestParam(required = false) Integer status,
                                @RequestParam(defaultValue = "1") long current,
                                @RequestParam(defaultValue = "20") long size) {
        return bnplService.listOrders(provider, memberId, status, current, size, TenantContextHolder.get().getTenantId());
    }

    @PostMapping("/order/{id}/cancel")
    @RequirePermission("bnpl:cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        return bnplService.cancel(id, TenantContextHolder.get().getTenantId());
    }

    @GetMapping("/statistics")
    @RequirePermission("bnpl:statistics")
    public Result<Map<String, Object>> statistics(@RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        return bnplService.statistics(startDate, endDate, TenantContextHolder.get().getTenantId());
    }

    /**
     * 【开发测试用】模拟 BNPL 平台回调 — 绕过真实 webhook 验签。
     * <p>生产环境请使用 {@code POST /bnpl/webhook/{provider}}。
     * @deprecated 建议用真实 webhook 接口 {@code POST /bnpl/webhook/{provider}}
     */
    @Deprecated
    @PostMapping("/notify/simulate")
    public Result<Void> simulateNotify(@RequestParam Long orderId,
                                       @RequestParam(defaultValue = "order.confirmed") String eventType) {
        return bnplService.simulateNotify(orderId, eventType, TenantContextHolder.get().getTenantId());
    }

    /**
     * 接收 BNPL 平台的真实 webhook 回调。
     *
     * <p>此接口为公开端点（无需登录态），由 Klarna / Afterpay 等平台在订单状态变更时调用。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 provider 从 {@link com.meiyetec.payment.bnpl.BnplProviderRouter} 获取对应实现</li>
     *   <li>调用 {@code provider.handleWebhook(...)} 完成验签 + 解析事件</li>
     *   <li>按返回的 newStatus 更新本地 {@code biz_bnpl_order} 记录</li>
     * </ol>
     *
     * <p>平台配置 webhook URL 示例：
     * <ul>
     *   <li>Klarna：{@code https://your-domain/bnpl/webhook/klarna}</li>
     *   <li>Afterpay：{@code https://your-domain/bnpl/webhook/afterpay}</li>
     * </ul>
     *
     * @param provider   平台代码（路径参数，KLARNA / AFTERPAY）
     * @param eventType  事件类型（Query 参数，部分平台在 body 中）
     * @param payload    原始 JSON body（用于验签）
     * @param signature  签名/Token（从 HTTP header 或 payload 中提取）
     * @param secret     可选的 webhook 密钥（优先用配置；如传入则覆盖配置）
     * @return 验签结果 + 状态变更结果
     */
    @PostMapping("/webhook/{provider}")
    public ResponseEntity<Result<Map<String, Object>>> handleWebhook(
            @PathVariable String provider,
            @RequestParam(required = false) String eventType,
            @RequestBody String payload,
            @RequestHeader(value = "X-Klarna-Signature", required = false) String klarnaSig,
            @RequestHeader(value = "X-Afterpay-Signature", required = false) String afterpaySig,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String genericSig,
            @RequestParam(required = false) String secret) {

        String effectiveSignature = klarnaSig != null ? klarnaSig
                : afterpaySig != null ? afterpaySig
                : genericSig != null ? genericSig
                : "";

        try {
            Result<Map<String, Object>> r = bnplService.handleWebhook(
                    provider, eventType, payload, effectiveSignature, secret, null);
            // 业务失败 → 4xx 不重试；系统异常已在 ServiceImpl 抛出，会走 catch
            if (r.getCode() != 200) {
                // 验签失败 PERMISSION_DENIED → 403（不重试同 rawBody）
                // 其他业务失败 → 400（不重试同 payload）
                HttpStatus status = (r.getCode() == BizCode.PERMISSION_DENIED.code)
                        ? HttpStatus.FORBIDDEN
                        : HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status).body(r);
            }
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            // 系统异常（DB 缺表等）→ HTTP 500，让 BNPL 上游重试
            log.error("[BNPL] webhook process failed: provider={} err={}", provider, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(BizCode.SYSTEM_ERROR, "BNPL webhook 处理失败: " + e.getMessage()));
        }
    }

    /**
     * 查询已注册的 BNPL provider 及其配置状态。
     */
    @GetMapping("/providers")
    @RequirePermission("bnpl:list")
    public Result<Map<String, Boolean>> listProviders() {
        return Result.success(bnplService.listProviders());
    }
}