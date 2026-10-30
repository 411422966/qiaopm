package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.config.DcepConfigProperties;
import com.meiyetec.dcep.DcepAdapterFactory;
import com.meiyetec.dcep.DcepChannelAdapter;
import com.meiyetec.dcep.DcepChannelAdapter.CloseRequest;
import com.meiyetec.dcep.DcepChannelAdapter.OrderQueryRequest;
import com.meiyetec.dcep.DcepChannelAdapter.PrepayRequest;
import com.meiyetec.dcep.DcepChannelAdapter.QueryResult;
import com.meiyetec.dcep.DcepChannelAdapter.RefundRequest;
import com.meiyetec.entity.PayDcepChannel;
import com.meiyetec.entity.PayDcepNotify;
import com.meiyetec.entity.PayDcepOrder;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.event.OrderPaidEvent;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.PayDcepChannelMapper;
import com.meiyetec.mapper.PayDcepNotifyMapper;
import com.meiyetec.mapper.PayDcepOrderMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P3 · T-P05 + P0-MONEY-001：数字人民币 2.0（真实通道联调 + Mock 兜底）
 *  - 通道配置（建行/工行/农行 + Mock 模式）
 *  - 预支付（prepay_id）申请
 *  - 异步回调验签
 *  - 退款 / 关闭
 *  - 健康检查
 *
 * <p>生产：通过 {@code app.dcep.mode=prod} 启用严格模式，禁止 Mock 行为。
 * 开发：通过 {@code app.dcep.mode=dev}（默认）允许 Mock 行为。
 *
 * <p>生产：替换 MockDcepClient 为对应银行 SDK。
 */
@RestController
@RequestMapping("/pay/dcep")
@RequiredArgsConstructor
public class DcepService {

    private static final Logger log = LoggerFactory.getLogger(DcepService.class);

    private final PayDcepChannelMapper channelMapper;
    private final PayDcepOrderMapper orderMapper;
    private final PayDcepNotifyMapper notifyMapper;
    private final PosOrderMapper posOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * P0-MONEY-001：DCEP 配置（构造器注入 + @Lazy 防止循环依赖/NULL）
     */
    private final DcepConfigProperties dcepConfig;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // P0-T12：启动能力检查与运行时可用性标志
    // ====================================================================

    /**
     * P0-T12：所有注入的银行 adapter 列表（用于启动期能力扫描）。
     * dev/sandbox 模式允许 MOCK 占位，prod 模式下若全部为 NOT_IMPLEMENTED 应告警。
     */
    @Autowired(required = false)
    private List<DcepChannelAdapter> dcepChannelAdapters;

    /**
     * P0-T13 Phase 1：按 channelCode 路由到具体实现。
     * 由 DcepAdapterFactory 暴露 getAdapter(channelCode) 。
     */
    @Autowired(required = false)
    private DcepAdapterFactory dcepAdapterFactory;

    /**
     * P0-T12：DCEP 运行时可用性标志。
     * <ul>
     *   <li>dev/sandbox 模式：true（默认）</li>
     *   <li>prod 模式：默认 true；当且仅当所有注入的 bank adapter 均返回 NOT_IMPLEMENTED 时置为 false</li>
     * </ul>
     * 该标志由 {@link #validateRuntimeCapability()} 在启动期写入，
     * 可被上游业务或运维面板读取以判断"DCEP 在生产模式是否可用"。
     */
    private volatile boolean dcepRuntimeReady = true;

    public boolean isDcepRuntimeReady() {
        return dcepRuntimeReady;
    }

    /**
     * P0-T12：启动期能力校验。
     *
     * <p>仅当 {@code app.dcep.mode == prod} 时扫描注入的银行 adapter，
     * 若全部 5 个核心方法返回 NOT_IMPLEMENTED：
     * <ul>
     *   <li>log ERROR（不要阻断 CI，按方案 A 静默日志告警）</li>
     *   <li>将 {@link #dcepRuntimeReady} 置为 false，标记生产模式当前不可用</li>
     * </ul>
     *
     * <p>dev / sandbox 模式直接通过；prod 模式下若至少一个 adapter 返回正常值，
     * 也置为 true 并 INFO 日志。
     */
    @PostConstruct
    public void validateRuntimeCapability() {
        if (!dcepConfig.isStrictProd()) {
            log.info("[DCEP] mode={} 启动校验通过（dev/sandbox 不要求真实银行 SDK）",
                    dcepConfig.getMode());
            return;
        }

        // prod 模式：扫描所有注入的 adapter
        if (dcepChannelAdapters == null || dcepChannelAdapters.isEmpty()) {
            log.error("[DCEP] mode=prod 启动，但 Spring 容器中没有注入任何 DcepChannelAdapter；" +
                    "DCEP 在生产模式完全不可用");
            dcepRuntimeReady = false;
            return;
        }

        boolean anyAdapterImplemented = false;
        for (DcepChannelAdapter adapter : dcepChannelAdapters) {
            try {
                PrepayRequest probe = PrepayRequest.builder()
                        .channelCode(adapter.channelCode())
                        .dcepOrderNo("DCEP-PROBE-" + UUID.randomUUID().toString().substring(0, 8))
                        .amountCents(0L)
                        .envMode("PROD")
                        .build();
                var probeResult = adapter.prepay(probe);
                if (probeResult != null && probeResult.getErrorCode() == null) {
                    anyAdapterImplemented = true;
                    break;
                }
            } catch (Exception e) {
                log.warn("[DCEP] adapter probe {} failed: {}",
                        adapter.channelCode(), e.getMessage());
            }
        }

        if (!anyAdapterImplemented) {
            log.error("[DCEP] mode=prod 启动，但所有 {} 个银行 adapter 均返回 NOT_IMPLEMENTED；" +
                    "DCEP 在生产模式当前不可用，请尽快完成 T13 真实 SDK 接入",
                    dcepChannelAdapters.size());
            dcepRuntimeReady = false;
        } else {
            log.info("[DCEP] mode=prod 启动，至少 1 个银行 adapter 已对接，DCEP 运行时可用");
            dcepRuntimeReady = true;
        }
    }

    // ====================================================================
    // 通道配置
    // ====================================================================

    @GetMapping("/channel/list")
    public Result<List<PayDcepChannel>> channelList() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(channelMapper.selectList(new LambdaQueryWrapper<PayDcepChannel>()
                .eq(PayDcepChannel::getTenantId, tenantId)
                .eq(PayDcepChannel::getStatus, 1)));
    }

    @PostMapping("/channel")
    public Result<PayDcepChannel> createChannel(@RequestBody PayDcepChannel c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getChannelCode() == null) return Result.error(BizCode.BAD_REQUEST, "channelCode 必填");
        if (c.getEnvMode() == null) c.setEnvMode("MOCK");
        if (c.getStatus() == null) c.setStatus(1);
        c.setTenantId(tenantId);
        channelMapper.insert(c);
        return Result.success("已创建", c);
    }

    @PostMapping("/channel/{id}/health")
    public Result<Map<String, Object>> healthCheck(@PathVariable Long id) {
        PayDcepChannel c = channelMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "通道不存在");
        // P0-T13 Phase 1：MOCK 通道保留原简化 URL ping；
        //                SANDBOX/PROD 通道调用 adapter（如有真实实现）
        boolean ok;
        if (isMockChannel(c)) {
            ok = pingHealth(c.getApiBaseUrl());
        } else if (dcepAdapterFactory != null) {
            try {
                var probeReq = PrepayRequest.builder()
                        .channelCode(c.getChannelCode())
                        .merchantId(c.getMerchantId())
                        .amountCents(0L)
                        .envMode(c.getEnvMode())
                        .build();
                var probeResult = dcepAdapterFactory.getAdapter(c.getChannelCode()).prepay(probeReq);
                ok = probeResult != null && probeResult.getErrorCode() == null;
            } catch (Exception e) {
                log.warn("[DCEP] health probe via adapter failed channel={}: {}",
                        c.getChannelCode(), e.toString());
                ok = false;
            }
        } else {
            ok = pingHealth(c.getApiBaseUrl());
        }
        jdbc.update("UPDATE pay_dcep_channel SET last_health_at = NOW(), last_health_msg = ? WHERE id = ?",
                ok ? "OK" : "FAIL", id);
        Map<String, Object> data = new HashMap<>();
        data.put("healthy", ok);
        data.put("envMode", c.getEnvMode());
        data.put("appMode", dcepConfig.getMode());
        return Result.success(data);
    }

    private boolean pingHealth(String url) {
        try {
            // 简化：实际替换为 HTTP 探测
            return url != null && (url.startsWith("https://") || url.startsWith("http://"));
        } catch (Exception e) {
            return false;
        }
    }

    // ====================================================================
    // 预支付 / 支付 / 关闭 / 退款
    // ====================================================================

    @PostMapping("/prepay")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> prepay(@RequestBody Map<String, Object> body) {
        Long bizOrderId = body.get("bizOrderId") == null ? null : ((Number) body.get("bizOrderId")).longValue();
        String bizOrderNo = (String) body.get("bizOrderNo");
        Long amountCents = body.get("amountCents") == null ? null : ((Number) body.get("amountCents")).longValue();
        String channelCode = (String) body.getOrDefault("channelCode", "DCEP-DCBC-MOCK");
        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();

        if (bizOrderId == null || amountCents == null) return Result.error(BizCode.BAD_REQUEST, "bizOrderId / amountCents 必填");

        // 通道
        PayDcepChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<PayDcepChannel>()
                .eq(PayDcepChannel::getTenantId, TenantContextHolder.get().getTenantId())
                .eq(PayDcepChannel::getChannelCode, channelCode)
                .eq(PayDcepChannel::getStatus, 1)
                .last("LIMIT 1"));
        if (channel == null) return Result.error(BizCode.NOT_FOUND, "通道不存在或未启用");

        // 查 brand_id（兼容旧版：pos_store 可能没有 brand_id 列，则置 NULL）
        Long brandId = null;
        try {
            brandId = jdbc.queryForObject("SELECT brand_id FROM pos_store WHERE id = ?", Long.class, storeId);
        } catch (org.springframework.dao.DataAccessException ignore) {
            // pos_store 没 brand_id 列（R2.0 阶段尚未实施三级模型），置 NULL
            log.debug("[DCEP] pos_store.brand_id not present, use NULL");
        }

        PayDcepOrder order = new PayDcepOrder();
        order.setTenantId(TenantContextHolder.get().getTenantId());
        order.setBrandId(brandId);
        order.setStoreId(storeId);
        order.setBizOrderId(bizOrderId);
        order.setBizOrderNo(bizOrderNo);
        order.setDcepOrderNo("DCEP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        order.setAmountCents(amountCents);
        order.setChannelId(channel.getId());
        order.setChannelCode(channel.getChannelCode());
        order.setWalletType((String) body.getOrDefault("walletType", "PERSONAL"));
        order.setPayerPhone((String) body.get("payerPhone"));
        order.setStatus(1);
        order.setExpireAt(LocalDateTime.now().plusMinutes(15));
        order.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        orderMapper.insert(order);

        // 调用通道 prePay（依据 app.dcep.mode + 通道 envMode）
        // P0-T13 Phase 1：MOCK 通道走原 mockPrepay() 私有方法（完全保留 T12 行为）；
        //                SANDBOX/PROD 通道走 dcepAdapterFactory.getAdapter()。
        Map<String, Object> prepayResp;
        if (isMockChannel(channel)) {
            prepayResp = mockPrepay(channel, order);
        } else {
            prepayResp = prepayViaAdapter(channel, order);
        }

        order.setPrepayId((String) prepayResp.get("prepayId"));
        order.setPrepayAt(LocalDateTime.now());
        if (!"OK".equals(prepayResp.get("status"))) {
            order.setStatus(5);
            order.setErrorCode((String) prepayResp.get("errorCode"));
            order.setErrorMsg((String) prepayResp.get("errorMsg"));
        }
        orderMapper.updateById(order);

        Map<String, Object> data = new HashMap<>();
        data.put("dcepOrderId", order.getId());
        data.put("dcepOrderNo", order.getDcepOrderNo());
        data.put("prepayId", order.getPrepayId());
        data.put("status", order.getStatus());
        data.put("qrCode", prepayResp.get("qrCode"));
        data.put("expireAt", order.getExpireAt());
        data.put("appMode", dcepConfig.getMode());
        return Result.success(data);
    }

    /**
     * 模拟/真实通道 prepay
     *
     * <p>规则（P0-MONEY-001）：
     * <ul>
     *   <li>app.dcep.mode=prod → 不允许任何 Mock 行为，统一返回 FAIL / NOT_IMPLEMENTED</li>
     *   <li>app.dcep.mode=dev → 通道 envMode=MOCK 时走 Mock（直接成功）；envMode!=MOCK 时返回 FAIL</li>
     * </ul>
     */
    private Map<String, Object> mockPrepay(PayDcepChannel channel, PayDcepOrder order) {
        Map<String, Object> res = new HashMap<>();

        // 严格生产模式：禁止 Mock 行为
        if (dcepConfig.isStrictProd()) {
            log.warn("[DCEP] prod mode blocks mockPrepay for channel={} order={}",
                    channel.getChannelCode(), order.getDcepOrderNo());
            res.put("status", "FAIL");
            res.put("errorCode", "NOT_IMPLEMENTED");
            res.put("errorMsg", "生产模式未对接真实通道 SDK，请配置 app.dcep.mode=dev 或对接银行 SDK");
            return res;
        }

        // dev 模式：通道为 MOCK 则直接成功
        if ("MOCK".equalsIgnoreCase(channel.getEnvMode())) {
            res.put("status", "OK");
            res.put("prepayId", "PREPAY-" + order.getDcepOrderNo());
            res.put("qrCode", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
            return res;
        }

        // dev 模式但通道非 MOCK（如 SANDBOX）：返回 FAIL 等待真实对接
        res.put("status", "FAIL");
        res.put("errorCode", "NOT_IMPLEMENTED");
        res.put("errorMsg", "SANDBOX/PROD 通道尚未对接");
        return res;
    }

    // ====================================================================
    // P0-T13 Phase 1：Adapter Runtime 路由层
    // ====================================================================

    /**
     * 判断通道是否走 MOCK 路径（T12 兼容）。
     *
     * <p>规则：仅当 envMode = MOCK 时走 T12 私有 mockPrepay() / verifySign() 路径；
     * 其余（SANDBOX/PROD/null/小写 mock 等）一律走 dcepAdapterFactory。
     */
    private boolean isMockChannel(PayDcepChannel channel) {
        return channel != null && "MOCK".equalsIgnoreCase(channel.getEnvMode());
    }

    /**
     * P0-T13 Phase 1：SANDBOX/PROD 通道走 adapter.prepay()。
     *
     * <p>关键约束：
     * <ul>
     *   <li>禁止 fallback 成功 —— adapter 返回失败必须透传</li>
     *   <li>禁止伪造支付成功 —— 不调用 mockPrepay()</li>
     *   <li>adapter 不存在 / channelCode 未注册时返回 NOT_IMPLEMENTED 错误</li>
     *   <li>adapter 暂未真实实现时返回其原生 NOT_IMPLEMENTED</li>
     * </ul>
     *
     * <p>返回 Map shape 与 mockPrepay() 一致（status/prepayId/qrCode/errorCode/errorMsg）。
     */
    private Map<String, Object> prepayViaAdapter(PayDcepChannel channel, PayDcepOrder order) {
        Map<String, Object> res = new HashMap<>();
        try {
            if (dcepAdapterFactory == null) {
                log.error("[DCEP] dcepAdapterFactory not wired; cannot route SANDBOX/PROD channel={}",
                        channel.getChannelCode());
                res.put("status", "FAIL");
                res.put("errorCode", "DCEP_RUNTIME_NOT_READY");
                res.put("errorMsg", "DCEP adapter factory not initialized");
                return res;
            }

            DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channel.getChannelCode());
            PrepayRequest req = PrepayRequest.builder()
                    .merchantId(channel.getMerchantId())
                    .appId(channel.getAppId())
                    .merchantPrivateKey(channel.getPrivateKeyPath())
                    .bankPublicKey(channel.getPublicKeyPath())
                    .channelCode(channel.getChannelCode())
                    .dcepOrderNo(order.getDcepOrderNo())
                    .bizOrderNo(order.getBizOrderNo())
                    .amountCents(order.getAmountCents())
                    .payerPhone(order.getPayerPhone())
                    .walletType(order.getWalletType())
                    .notifyUrl(channel.getNotifyUrl())
                    .expireAt(order.getExpireAt() == null ? null : order.getExpireAt().toString())
                    .envMode(channel.getEnvMode())
                    .build();
            var result = adapter.prepay(req);
            if (result == null) {
                res.put("status", "FAIL");
                res.put("errorCode", "DCEP_PROVIDER_NOT_READY");
                res.put("errorMsg", "adapter returned null for channel=" + channel.getChannelCode());
                return res;
            }
            res.put("status", result.isSuccess() ? "OK" : "FAIL");
            res.put("prepayId", result.getPrepayId());
            res.put("qrCode", result.getQrCode() != null ? result.getQrCode() : result.getH5Url());
            res.put("errorCode", result.getErrorCode());
            res.put("errorMsg", result.getErrorMsg());
            return res;
        } catch (IllegalArgumentException noAdapter) {
            log.warn("[DCEP] no adapter registered for channel={}: {}",
                    channel.getChannelCode(), noAdapter.getMessage());
            res.put("status", "FAIL");
            res.put("errorCode", "DCEP_PROVIDER_NOT_REGISTERED");
            res.put("errorMsg", noAdapter.getMessage());
            return res;
        } catch (Exception e) {
            log.error("[DCEP] adapter.prepay() threw for channel={}: {}",
                    channel.getChannelCode(), e.toString());
            res.put("status", "FAIL");
            res.put("errorCode", "DCEP_PROVIDER_NOT_READY");
            res.put("errorMsg", e.getMessage() == null ? e.toString() : e.getMessage());
            return res;
        }
    }

    /**
     * P0-T13 Phase 1：prod 通知验签走 adapter.parseAndVerifyNotify()。
     *
     * <p>返回值：true 表示 signature + timestamp 都有效（dev/SANDBOX 仍走 verifySign 私有方法）。
     */
    private boolean verifySignViaAdapter(String channelCode, Map<String, Object> payload, String sign) {
        if (dcepAdapterFactory == null) {
            log.error("[DCEP] dcepAdapterFactory not wired; cannot verify notify for channel={}",
                    channelCode);
            return false;
        }
        try {
            DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channelCode);
            String rawBody = payload.toString();
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            if (sign != null) headers.put("X-Dcep-Sign", sign);
            var nr = adapter.parseAndVerifyNotify(rawBody, headers);
            return nr != null && nr.isSignatureValid() && nr.isTimestampValid();
        } catch (Exception e) {
            log.warn("[DCEP] adapter.parseAndVerifyNotify threw for channel={}: {}",
                    channelCode, e.toString());
            return false;
        }
    }

    /**
     * P0-T13 Phase 1：SANDBOX/PROD 退款走 adapter.refund()。
     * 成功 → true；失败（adapter 抛错 / 返回 success=false）→ false。
     */
    private boolean refundViaAdapter(PayDcepChannel channel, PayDcepOrder order,
                                     long amountCents, String refundOrderNo) {
        try {
            DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channel.getChannelCode());
            RefundRequest req = RefundRequest.builder()
                    .merchantId(channel.getMerchantId())
                    .dcepOrderNo(order.getDcepOrderNo())
                    .refundOrderNo(refundOrderNo)
                    .amountCents(amountCents)
                    .reason("customer requested")
                    .notifyUrl(channel.getNotifyUrl())
                    .envMode(channel.getEnvMode())
                    .build();
            var result = adapter.refund(req);
            if (result == null || !result.isSuccess()) {
                log.warn("[DCEP] adapter.refund failed channel={} err={}",
                        channel.getChannelCode(),
                        result == null ? "null result" : result.getErrorCode());
                return false;
            }
            log.info("[DCEP] adapter.refund success channel={} refundId={}",
                    channel.getChannelCode(), result.getRefundId());
            return true;
        } catch (Exception e) {
            log.error("[DCEP] adapter.refund threw channel={}: {}",
                    channel.getChannelCode(), e.toString());
            return false;
        }
    }

    /**
     * P0-T13 Phase 1：SANDBOX/PROD 关闭订单走 adapter.closeOrder()。
     */
    private boolean closeViaAdapter(PayDcepChannel channel, PayDcepOrder order) {
        try {
            DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channel.getChannelCode());
            CloseRequest req = CloseRequest.builder()
                    .merchantId(channel.getMerchantId())
                    .dcepOrderNo(order.getDcepOrderNo())
                    .envMode(channel.getEnvMode())
                    .build();
            var result = adapter.closeOrder(req);
            return result != null && result.isSuccess();
        } catch (Exception e) {
            log.error("[DCEP] adapter.closeOrder threw channel={}: {}",
                    channel.getChannelCode(), e.toString());
            return false;
        }
    }

    /**
     * P0-T13 Phase 1：主动查询订单走 adapter.query()。
     */
    private QueryResult queryViaAdapter(PayDcepChannel channel, String dcepOrderNo) {
        try {
            DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channel.getChannelCode());
            OrderQueryRequest req = OrderQueryRequest.builder()
                    .merchantId(channel.getMerchantId())
                    .dcepOrderNo(dcepOrderNo)
                    .envMode(channel.getEnvMode())
                    .build();
            return adapter.query(req);
        } catch (Exception e) {
            log.error("[DCEP] adapter.query threw channel={}: {}",
                    channel.getChannelCode(), e.toString());
            return null;
        }
    }

    @PostMapping("/order/{id}/query")
    public Result<Map<String, Object>> queryOrder(@PathVariable Long id) {
        PayDcepOrder o = orderMapper.selectById(id);
        if (o == null) return Result.error(BizCode.NOT_FOUND, "订单不存在");
        // P0-T13 Phase 1：MOCK 通道本地查表；SANDBOX/PROD 通道走 adapter 远程查询
        PayDcepChannel channel = o.getChannelId() == null ? null
                : channelMapper.selectById(o.getChannelId());
        if (channel == null || isMockChannel(channel)) {
            Map<String, Object> data = new HashMap<>();
            data.put("dcepOrderId", o.getId());
            data.put("dcepOrderNo", o.getDcepOrderNo());
            data.put("status", o.getStatus());
            data.put("amountCents", o.getAmountCents());
            data.put("channelCode", o.getChannelCode());
            data.put("source", "LOCAL");
            return Result.success(data);
        }
        QueryResult qr = queryViaAdapter(channel, o.getDcepOrderNo());
        if (qr == null) {
            return Result.error(BizCode.BAD_REQUEST,
                    "adapter.query failed for channel=" + channel.getChannelCode());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("dcepOrderId", o.getId());
        data.put("dcepOrderNo", o.getDcepOrderNo());
        data.put("channelCode", o.getChannelCode());
        data.put("remoteStatus", qr.getStatus());
        data.put("remoteAmount", qr.getAmountCents());
        data.put("remoteTransactionId", qr.getTransactionId());
        data.put("source", "ADAPTER");
        return Result.success(data);
    }

    @PostMapping("/order/{id}/close")
    public Result<Void> closeOrder(@PathVariable Long id) {
        PayDcepOrder o = orderMapper.selectById(id);
        if (o == null) return Result.error(BizCode.NOT_FOUND, "订单不存在");
        if (o.getStatus() != 1) return Result.error(BizCode.CONFLICT, "仅待支付订单可关闭");
        // P0-T13 Phase 1：根据通道 envMode 决定是否走 adapter
        PayDcepChannel channel = o.getChannelId() == null ? null
                : channelMapper.selectById(o.getChannelId());
        if (channel != null && !isMockChannel(channel)) {
            // SANDBOX/PROD：必须走 adapter 真实关闭，禁止直接 update status
            boolean adapterOk = closeViaAdapter(channel, o);
            if (!adapterOk) {
                return Result.error(BizCode.BAD_REQUEST,
                        "DCEP close via adapter failed (channel=" + channel.getChannelCode() + ")");
            }
        }
        o.setStatus(3);
        o.setCloseAt(LocalDateTime.now());
        orderMapper.updateById(o);
        return Result.success("已关闭", null);
    }

    @PostMapping("/order/{id}/refund")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refundOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PayDcepOrder o = orderMapper.selectById(id);
        if (o == null) return Result.error(BizCode.NOT_FOUND, "订单不存在");
        if (o.getStatus() != 2) return Result.error(BizCode.CONFLICT, "仅已支付订单可退款");
        Long amountCents = body.get("amountCents") == null ? o.getAmountCents() : ((Number) body.get("amountCents")).longValue();
        if (amountCents > o.getAmountCents()) return Result.error(BizCode.BAD_REQUEST, "退款金额超过原订单");
        // P0-T13 Phase 1：SANDBOX/PROD 通道必须先经过 adapter 真实退款
        PayDcepChannel channel = o.getChannelId() == null ? null
                : channelMapper.selectById(o.getChannelId());
        String refundTransactionId = (String) body.get("refundTransactionId");
        if (channel != null && !isMockChannel(channel)) {
            String refundOrderNo = "REFUND-" + o.getDcepOrderNo() + "-" + System.currentTimeMillis();
            boolean adapterOk = refundViaAdapter(channel, o, amountCents, refundOrderNo);
            if (!adapterOk) {
                return Result.error(BizCode.BAD_REQUEST,
                        "DCEP refund via adapter failed (channel=" + channel.getChannelCode() + ")");
            }
            if (refundTransactionId == null || refundTransactionId.isEmpty()) {
                refundTransactionId = refundOrderNo;
            }
        }
        o.setStatus(4);
        o.setRefundAt(LocalDateTime.now());
        o.setTransactionId(refundTransactionId);
        orderMapper.updateById(o);
        return Result.success("已退款", null);
    }

    @GetMapping("/order/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> orderPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String bizOrderNo) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT o.id, o.biz_order_id, o.biz_order_no, o.dcep_order_no, o.prepay_id, o.transaction_id, " +
            "       o.amount_cents, o.channel_code, o.wallet_type, o.payer_phone, o.status, " +
            "       o.prepay_at, o.paid_at, o.close_at, o.refund_at, o.expire_at, " +
            "       o.error_code, o.error_msg, o.notify_count, o.create_time " +
            "FROM pay_dcep_order o WHERE o.deleted = 0 AND o.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        List<Object> cargs = new ArrayList<>();
        args.add(tenantId);
        cargs.add(tenantId);
        if (status != null) {
            sql.append("AND o.status = ? "); args.add(status); cargs.add(status);
        }
        if (channelCode != null && !channelCode.isEmpty()) {
            sql.append("AND o.channel_code = ? "); args.add(channelCode); cargs.add(channelCode);
        }
        if (bizOrderNo != null && !bizOrderNo.isEmpty()) {
            sql.append("AND o.biz_order_no LIKE ? "); args.add("%" + bizOrderNo + "%"); cargs.add("%" + bizOrderNo + "%");
        }
        sql.append("ORDER BY o.id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM pay_dcep_order o WHERE o.deleted = 0 AND o.tenant_id = ? ");
        if (status != null) { csql.append("AND o.status = ? "); }
        if (channelCode != null && !channelCode.isEmpty()) { csql.append("AND o.channel_code = ? "); }
        if (bizOrderNo != null && !bizOrderNo.isEmpty()) { csql.append("AND o.biz_order_no LIKE ? "); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(com.meiyetec.util.Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    // ====================================================================
    // 异步回调（验签 + 处理）
    // ====================================================================

    /**
     * 银行 → 我方回调（notify_url）
     * 必做：验签 → 防重 → 查订单 → 更新状态 → 幂等处理
     */
    @PostMapping("/notify/{channelCode}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> notify(@PathVariable String channelCode, @RequestBody Map<String, Object> payload) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        log.info("[DCEP NOTIFY] channel={} payload={}", channelCode, payload);

        // 1. 验签（依据 app.dcep.mode + 通道 envMode）
        //    P0-T13 Phase 1：
        //      - dev + MOCK 通道  → 走 verifySign() 私有方法（T12 完全保留，仅接受 MOCK- 前缀）
        //      - prod + SANDBOX/PROD 通道 → 走 adapter.parseAndVerifyNotify()
        //      - dev + SANDBOX/PROD 通道   → 仍走 verifySign()（T12 限定 demo-only）
        String sign = (String) payload.get("sign");
        boolean signOk;
        if (dcepConfig.isStrictProd()) {
            signOk = verifySignViaAdapter(channelCode, payload, sign);
        } else {
            signOk = verifySign(channelCode, payload, sign);
        }

        // 2. 防重（notify_id 幂等）
        String notifyId = (String) payload.get("notifyId");
        if (notifyId != null) {
            Long dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pay_dcep_notify WHERE notify_id = ?",
                Long.class, notifyId);
            if (dup != null && dup > 0) {
                Map<String, Object> r = new HashMap<>();
                r.put("code", "SUCCESS");
                r.put("message", "duplicate ignored");
                return Result.success(r);
            }
        }

        // 3. 查订单
        String dcepOrderNo = (String) payload.get("dcepOrderNo");
        PayDcepOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PayDcepOrder>()
                .eq(PayDcepOrder::getTenantId, tenantId)
                .eq(PayDcepOrder::getDcepOrderNo, dcepOrderNo));
        if (order == null) {
            log.warn("[DCEP] order not found: {}", dcepOrderNo);
            return Result.error(BizCode.NOT_FOUND, "order not found");
        }

        // 4. 记录 notify
        PayDcepNotify notify = new PayDcepNotify();
        notify.setTenantId(tenantId);
        notify.setDcepOrderId(order.getId());
        notify.setNotifyId(notifyId);
        notify.setEventType((String) payload.get("eventType"));
        notify.setPayload(payload.toString());
        notify.setSignVerified(signOk ? 1 : 0);
        notify.setSignAlgo((String) payload.getOrDefault("signAlgo", "RSA-SHA256"));
        notify.setProcessed(1);
        notify.setNotifyAt(LocalDateTime.now());
        notify.setProcessAt(LocalDateTime.now());
        notify.setProcessMsg("processed");
        notifyMapper.insert(notify);

        if (!signOk) {
            return Result.error(BizCode.FORBIDDEN, "signature invalid");
        }

        // 5. 更新订单
        String event = (String) payload.get("eventType");
        boolean paidEvent = false;
        if ("PAY_SUCCESS".equals(event)) {
            order.setStatus(2);
            order.setPaidAt(LocalDateTime.now());
            order.setTransactionId((String) payload.get("transactionId"));
            paidEvent = true;
        } else if ("REFUND_SUCCESS".equals(event)) {
            order.setStatus(4);
            order.setRefundAt(LocalDateTime.now());
        } else if ("CLOSE".equals(event)) {
            order.setStatus(3);
            order.setCloseAt(LocalDateTime.now());
        }
        order.setNotifyCount((order.getNotifyCount() == null ? 0 : order.getNotifyCount()) + 1);
        order.setNotifyRaw(payload.toString());
        orderMapper.updateById(order);

        // 6. 回调业务系统（订单状态）— P0-MONEY-001 改造点：
        //    之前：直接 jdbc.update("UPDATE crm_order ...")（表名错误：实际是 pos_order）
        //    现在：通过 PosOrderMapper 查询订单，发布 OrderPaidEvent 事件
        //    优势：业务侧（OrderPaidEventListener）以事务后监听方式处理，可扩展、可测试
        if (paidEvent && order.getBizOrderId() != null) {
            PosOrder posOrder = posOrderMapper.selectById(order.getBizOrderId());
            if (posOrder != null) {
                posOrder.setStatus(2); // 2 = 已支付
                posOrder.setPayTime(LocalDateTime.now());
                posOrderMapper.updateById(posOrder);
                eventPublisher.publishEvent(new OrderPaidEvent(this, posOrder));
                log.info("[DCEP NOTIFY] published OrderPaidEvent for order={}", posOrder.getId());
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("code", "SUCCESS");
        res.put("message", "ok");
        return Result.success(res);
    }

    /**
     * 验签
     *
     * <p>规则（P0-MONEY-001 + P0-T12 收紧）：
     * <ul>
     *   <li>{@code app.dcep.mode=prod} → 严格模式：拒绝任何 {@code MOCK-} 前缀签名；非 MOCK- 真签名走 SHA-256 验签</li>
     *   <li>{@code app.dcep.mode=dev}  → 开发模式（demo-only）：<b>仅接受</b> {@code MOCK-} 前缀签名；任何非 MOCK- 前缀签名一律 fail-fast（包括正确 SHA-256 真银行签名）</li>
     * </ul>
     *
     * <p>P0-T12 设计意图：dev 模式必须完全 demo-only。如果真实银行回调（真 SHA-256 签名）误发到 dev 环境，
     * 必须 fail-fast 阻断 —— 避免"用 dev 模式挂着正式域名接受真银行支付"的安全风险。</p>
     *
     * <p>生产：根据 channelCode 选 RSA/SM2 公钥验签。
     */
    boolean verifySign(String channelCode, Map<String, Object> payload, String sign) {
        if (sign == null) return false;

        // 严格生产模式：禁止 MOCK- 前缀签名
        if (dcepConfig.isStrictProd()) {
            if (sign.startsWith("MOCK-")) {
                log.warn("[DCEP] prod mode rejects MOCK- prefixed sign from channel={}", channelCode);
                return false;
            }
            // 真实验签：此处应调用银行公钥验签（RSA/SM2）
            // 当前简化实现：使用 SHA-256 摘要比对
            return verifySignWithSha256(channelCode, payload, sign);
        }

        // P0-T12：dev / sandbox 模式 — 完全 demo-only
        // 仅接受 MOCK- 前缀签名；任何非 MOCK- 前缀签名（含真 SHA-256）一律 fail-fast。
        if (sign.startsWith("MOCK-")) {
            log.debug("[DCEP] dev mode accepts MOCK- prefixed sign from channel={}", channelCode);
            return true;
        }
        log.warn("[DCEP] dev mode rejects non-MOCK- sign from channel={} (fail-fast): " +
                "dev 模式只接受 demo 签名，禁止接受任何外部真实签名", channelCode);
        return false;
    }

    /**
     * SHA-256 验签工具：sign = SHA-256(channelCode + notifyId + dcepOrderNo)
     */
    private boolean verifySignWithSha256(String channelCode, Map<String, Object> payload, String sign) {
        try {
            String src = channelCode + (payload.get("notifyId") == null ? "" : payload.get("notifyId"))
                    + (payload.get("dcepOrderNo") == null ? "" : payload.get("dcepOrderNo"));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sign.equalsIgnoreCase(sb.toString());
        } catch (Exception e) {
            return false;
        }
    }

    // 包级辅助方法（仅测试用）
    String computeSha256Sign(String channelCode, String notifyId, String dcepOrderNo) {
        try {
            String src = channelCode + (notifyId == null ? "" : notifyId) + (dcepOrderNo == null ? "" : dcepOrderNo);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
