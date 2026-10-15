package com.meiyetec.cn.domestic;

import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 国内支付统一入口
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST /domestic/pay/prepay}          统一下单（前端调用）</li>
 *   <li>{@code POST /domestic/pay/query}           订单状态查询</li>
 *   <li>{@code POST /domestic/pay/refund}          退款</li>
 *   <li>{@code POST /domestic/pay/close}           关闭订单</li>
 *   <li>{@code POST /domestic/webhook/{channel}}   三方回调（微信 / 支付宝）</li>
 *   <li>{@code GET  /domestic/health}              健康检查（已注册通道列表）</li>
 * </ul>
 *
 * @since 2026-09-07
 */
@Slf4j
@RestController
@RequestMapping("/domestic")
@RequiredArgsConstructor
public class DomesticPayController {

    private final DomesticPayChannelRouter channelRouter;
    private final DomesticPayWebhookService webhookService;

    // ============================================================
    // 1. 统一下单
    // ============================================================
    @PostMapping("/pay/prepay")
    public Result<DomesticPayResponse<DomesticPayChannelAdapter.PrepayResult>> prepay(
            @RequestParam String channel,
            @RequestBody DomesticPayChannelAdapter.PrepayRequest request) {
        if (!channelRouter.hasAdapter(channel)) {
            return Result.error(4001, "未知渠道：" + channel);
        }
        DomesticPayChannelAdapter adapter = channelRouter.getAdapter(channel);
        // 自动补 notifyUrl（若 Properties 配置了 default）
        if (request.getNotifyUrl() == null || request.getNotifyUrl().isBlank()) {
            request.setNotifyUrl("https://dev.meiyetec.com/domestic/webhook/" + channel.toLowerCase());
        }
        DomesticPayChannelAdapter.PrepayResult r = adapter.prepay(request);
        if (!r.isSuccess()) {
            return Result.error(4002, r.getErrorCode() + ": " + r.getErrorMsg());
        }
        DomesticPayResponse<DomesticPayChannelAdapter.PrepayResult> resp =
                DomesticPayResponse.<DomesticPayChannelAdapter.PrepayResult>builder()
                        .ok(true)
                        .channelCode(channel)
                        .outOrderNo(request.getOutOrderNo())
                        .payload(r)
                        .build();
        return Result.success(resp);
    }

    // ============================================================
    // 2. 订单查询
    // ============================================================
    @PostMapping("/pay/query")
    public Result<DomesticPayResponse<DomesticPayChannelAdapter.QueryResult>> query(
            @RequestParam String channel,
            @RequestParam String outOrderNo) {
        DomesticPayChannelAdapter.OrderQueryRequest req = DomesticPayChannelAdapter.OrderQueryRequest.builder()
                .outOrderNo(outOrderNo).build();
        DomesticPayChannelAdapter.QueryResult r = channelRouter.getAdapter(channel).query(req);
        if (!r.isSuccess()) return Result.error(4003, r.getErrorCode() + ": " + r.getErrorMsg());
        return Result.success(DomesticPayResponse.<DomesticPayChannelAdapter.QueryResult>builder()
                .ok(true).channelCode(channel).outOrderNo(outOrderNo).payload(r).build());
    }

    // ============================================================
    // 3. 退款
    // ============================================================
    @PostMapping("/pay/refund")
    public Result<DomesticPayResponse<DomesticPayChannelAdapter.RefundResult>> refund(
            @RequestParam String channel,
            @RequestBody DomesticPayChannelAdapter.RefundRequest request) {
        DomesticPayChannelAdapter.RefundResult r = channelRouter.getAdapter(channel).refund(request);
        if (!r.isSuccess()) return Result.error(4004, r.getErrorCode() + ": " + r.getErrorMsg());
        return Result.success(DomesticPayResponse.<DomesticPayChannelAdapter.RefundResult>builder()
                .ok(true).channelCode(channel).outOrderNo(request.getOutOrderNo()).payload(r).build());
    }

    // ============================================================
    // 4. 关闭订单
    // ============================================================
    @PostMapping("/pay/close")
    public Result<DomesticPayResponse<DomesticPayChannelAdapter.CloseResult>> close(
            @RequestParam String channel,
            @RequestParam String outOrderNo) {
        DomesticPayChannelAdapter.CloseRequest req = DomesticPayChannelAdapter.CloseRequest.builder()
                .outOrderNo(outOrderNo).build();
        DomesticPayChannelAdapter.CloseResult r = channelRouter.getAdapter(channel).closeOrder(req);
        if (!r.isSuccess()) return Result.error(4005, r.getErrorCode() + ": " + r.getErrorMsg());
        return Result.success(DomesticPayResponse.<DomesticPayChannelAdapter.CloseResult>builder()
                .ok(true).channelCode(channel).outOrderNo(outOrderNo).payload(r).build());
    }

    // ============================================================
    // 5. 三方回调（关键端点）
    //    - 微信支付 V3: POST /domestic/webhook/WECHAT_PAY，header 含 Wechatpay-Signature 等
    //    - 支付宝:      POST /domestic/webhook/ALIPAY，body application/x-www-form-urlencoded
    // ============================================================
    @PostMapping("/webhook/{channel}")
    public ResponseEntity<Result<DomesticPayResponse<String>>> webhook(
            @PathVariable String channel,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest request) {
        Map<String, String> headers = extractHeaders(request, channel);
        String sourceIp = request.getRemoteAddr();
        log.info("[CN-DOMESTIC] webhook received: channel={} ip={} headerKeys={}",
                channel, sourceIp, headers.keySet());
        try {
            DomesticPayResponse<String> r = webhookService.handle(channel, rawBody, headers, sourceIp);
            // 业务处理失败（如验签失败 / 订单不存在）→ HTTP 4xx，让上游不要无限重试
            if (!r.isOk()) {
                String errCode = r.getErrorCode() == null ? "UNKNOWN" : r.getErrorCode();
                // 验签失败/订单不存在 = 4xx（不应重试）；其他业务失败 = 5xx（可重试）
                HttpStatus status = ("SIGNATURE_INVALID".equalsIgnoreCase(errCode)
                        || "ORDER_NOT_FOUND".equalsIgnoreCase(errCode))
                        ? HttpStatus.BAD_REQUEST
                        : HttpStatus.INTERNAL_SERVER_ERROR;
                return ResponseEntity.status(status)
                        .body(Result.error(4010, errCode + ": " + r.getErrorMessage()));
            }
            return ResponseEntity.ok(Result.success(r));
        } catch (Exception e) {
            // 系统异常（DB 缺表 / 网络错误等）→ HTTP 500，让上游重试
            log.error("[CN-DOMESTIC] webhook process failed: channel={} err={}", channel, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(4010, "WEBHOOK_EXCEPTION: " + e.getMessage()));
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request, String channel) {
        Map<String, String> map = new HashMap<>();
        java.util.Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, request.getHeader(name));
        }
        // 支付宝通知是表单格式，参数在 request.getParameter()
        if ("ALIPAY".equalsIgnoreCase(channel)) {
            request.getParameterMap().forEach((k, v) -> {
                if (v != null && v.length > 0) map.putIfAbsent(k, v[0]);
            });
        }
        return map;
    }

    // ============================================================
    // 6. 健康检查（动态读取已注册通道）
    // ============================================================
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> adapters = new HashMap<>();
        for (String code : channelRouter.listChannels()) {
            adapters.put(code, channelRouter.getChannelStatus(code));
        }
        return Result.success(adapters);
    }
}