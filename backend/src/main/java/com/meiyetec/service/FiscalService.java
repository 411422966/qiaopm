package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.FiscalConfig;
import com.meiyetec.entity.FiscalInvoice;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.FiscalInvoiceMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.gateway.FiscalGateway;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 财税直连 — FISCAL-001 真实流程版
 *
 * <p>原 P3-23 接口协议保留（{@code issue/listInvoices/getInvoice/voidInvoice/listChannels/dashboard}），
 * 现在所有开票/红冲请求都委派给 {@link FiscalGateway}：
 * <ul>
 *   <li>{@code fiscal.mode=mock}（默认）→ {@link com.meiyetec.service.gateway.MockFiscalGateway}</li>
 *   <li>{@code fiscal.mode=baiwang}        → {@link com.meiyetec.service.gateway.BaiwangFiscalGateway}</li>
 * </ul>
 *
 * <p>开票状态机：
 * <pre>
 *   调用网关
 *      │
 *      ▼
 *   PENDING  ──网关成功──▶  ISSUED（写 platformInvoiceNo + pdfUrl）
 *      │
 *      └──网关失败──▶  FAILED（写 errorMessage + retry_count）
 *
 *   ISSUED  ──红冲成功──▶  VOIDED（写 platformVoidNo）
 *      │
 *      └──红冲失败──▶  保持 ISSUED + errorMessage
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/fiscal")
@RequiredArgsConstructor
public class FiscalService {

    private final FiscalInvoiceMapper invoiceMapper;
    private final FiscalConfigService fiscalConfigService;
    /** 注入唯一的 FiscalGateway（Spring 按 fiscal.mode 自动选实现） */
    private final FiscalGateway fiscalGateway;

    @Autowired(required = false)
    private ObjectMapper jackson;

    /**
     * 真实开票流程：
     * 1. 落库 PENDING 发票
     * 2. 调用 FiscalGateway
     * 3. 成功 → ISSUED 并写 platformInvoiceNo / pdfUrl
     * 4. 失败 → FAILED 并写 errorMessage
     */
    @PostMapping("/issue")
    @Transactional(rollbackFor = Exception.class)
    public Result<FiscalInvoice> issue(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long orderId = body.get("orderId") == null ? null
                : ((Number) body.get("orderId")).longValue();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String channel = (String) body.getOrDefault("channel", "BAI_WANG");
        String requestId = body.get("requestId") == null
                ? "REQ-" + UUID.randomUUID().toString()
                : body.get("requestId").toString();

        // 1. 取 fiscal_config（按 channel）
        FiscalConfig cfg = fiscalConfigService.getOne(new LambdaQueryWrapper<FiscalConfig>()
                .eq(FiscalConfig::getTenantId, tenantId)
                .eq(FiscalConfig::getChannel, channel));
        if (cfg == null || cfg.getEnabled() == null || cfg.getEnabled() == 0) {
            return Result.error(BizCode.CONFIG_MISSING,
                    "渠道 " + channel + " 未配置或未启用，请先在 /fiscal/config 保存");
        }

        // 2. 构造请求体（组装 PENDING 记录）
        FiscalInvoice inv = new FiscalInvoice();
        inv.setTenantId(tenantId);
        inv.setInvoiceNo("INV" + System.currentTimeMillis());
        inv.setOrderId(orderId);
        inv.setChannel(channel);
        inv.setInvoiceType((String) body.getOrDefault("invoiceType", "GENERAL"));
        inv.setAmount(amount);
        BigDecimal taxRate = body.get("taxRate") == null
                ? new BigDecimal("0.06")
                : new BigDecimal(body.get("taxRate").toString());
        inv.setTaxRate(taxRate);
        BigDecimal taxAmount = amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        inv.setTaxAmount(taxAmount);
        inv.setTotalAmount(amount.add(taxAmount));
        inv.setBuyerName((String) body.get("buyerName"));
        inv.setBuyerTaxNo((String) body.get("buyerTaxNo"));
        inv.setBuyerAddress((String) body.get("buyerAddress"));
        inv.setBuyerPhone((String) body.get("buyerPhone"));
        inv.setBuyerEmail((String) body.get("buyerEmail"));
        inv.setBuyerBank((String) body.get("buyerBank"));
        inv.setBuyerAccount((String) body.get("buyerAccount"));
        inv.setInvoiceStatus("PENDING");
        inv.setIssuedAt(null);
        inv.setRequestMode(fiscalGateway.mode());
        inv.setPlatformRequestId(requestId);
        inv.setRequestStartedAt(LocalDateTime.now());
        invoiceMapper.insert(inv);
        log.info("[FISCAL] PENDING inserted: invoiceNo={} channel={} amount={}",
                inv.getInvoiceNo(), channel, amount);

        // 3. 构造网关请求
        FiscalGateway.IssueRequest ireq = new FiscalGateway.IssueRequest();
        ireq.setOrderNo(orderId == null ? inv.getInvoiceNo() : String.valueOf(orderId));
        ireq.setSellerTaxNo(cfg.getTaxNo());
        ireq.setSellerName(cfg.getMerchantName());
        ireq.setBuyerName(inv.getBuyerName());
        ireq.setBuyerTaxNo(inv.getBuyerTaxNo());
        ireq.setBuyerAddress(inv.getBuyerAddress());
        ireq.setBuyerPhone(inv.getBuyerPhone());
        ireq.setBuyerEmail(inv.getBuyerEmail());
        ireq.setBuyerBank(inv.getBuyerBank());
        ireq.setBuyerAccount(inv.getBuyerAccount());
        ireq.setInvoiceType(inv.getInvoiceType());
        ireq.setAppKey(cfg.getAppKey());
        ireq.setAppSecret(cfg.getAppSecret());
        ireq.setRequestId(requestId);
        ireq.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        ireq.setRemark((String) body.getOrDefault("remark", ""));
        ireq.setItems(extractItems(body));

        // 保存请求载荷便于人工排查
        inv.setRequestPayload(toJson(ireq.toPlatformPayload()));

        // 4. 调用网关
        FiscalGateway.IssueResult result;
        try {
            result = fiscalGateway.issue(ireq);
        } catch (Exception e) {
            log.warn("[FISCAL] gateway exception: invoiceNo={} err={}", inv.getInvoiceNo(), e.getMessage());
            result = FiscalGateway.IssueResult.fail("GATEWAY_EXCEPTION", e.getMessage(), null);
        }
        inv.setRequestCompletedAt(LocalDateTime.now());
        inv.setPlatformResponse(result.getRawResponse());

        // 5. 状态机分支
        if (result.isSuccess()) {
            inv.setInvoiceStatus("ISSUED");
            inv.setIssuedAt(LocalDateTime.now());
            inv.setPlatformInvoiceNo(result.getPlatformInvoiceNo());
            inv.setPlatformPdfUrl(result.getPdfUrl());
            inv.setPdfUrl(result.getPdfUrl());
            inv.setErrorMessage(null);
            log.info("[FISCAL] ISSUED: invoiceNo={} platformInvoiceNo={} mode={}",
                    inv.getInvoiceNo(), result.getPlatformInvoiceNo(), fiscalGateway.mode());
        } else {
            inv.setInvoiceStatus("FAILED");
            inv.setErrorMessage(result.getErrorCode() + ": " + result.getErrorMessage());
            inv.setRetryCount(inv.getRetryCount() == null ? 1 : inv.getRetryCount() + 1);
            log.warn("[FISCAL] FAILED: invoiceNo={} err={}",
                    inv.getInvoiceNo(), inv.getErrorMessage());
        }
        invoiceMapper.updateById(inv);

        if (!result.isSuccess()) {
            return Result.error(BizCode.FISCAL_GATEWAY_ERROR,
                    inv.getErrorMessage(), inv);
        }
        return Result.success("已开具", inv);
    }

    /**
     * 红冲 / 作废
     *
     * <p>需要 {@code platform_invoice_no} 已存在（必须真实开过票）。
     */
    @PostMapping("/{id}/void")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> voidInvoice(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        FiscalInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) return Result.error(BizCode.NOT_FOUND, "发票不存在");
        if (!tenantId.equals(inv.getTenantId())) {
            return Result.error(BizCode.TENANT_DENIED, "跨租户访问被拒绝");
        }
        if ("VOIDED".equals(inv.getInvoiceStatus())) {
            return Result.error(BizCode.BAD_REQUEST, "已红冲，请勿重复操作");
        }
        if (!"ISSUED".equals(inv.getInvoiceStatus())) {
            return Result.error(BizCode.FISCAL_STATUS_INVALID,
                    "仅 ISSUED 状态可红冲，当前：" + inv.getInvoiceStatus());
        }
        if (inv.getPlatformInvoiceNo() == null || inv.getPlatformInvoiceNo().isEmpty()) {
            return Result.error(BizCode.FISCAL_STATUS_INVALID,
                    "缺少 platformInvoiceNo，无法红冲（开发环境 Mock 数据可能未写入）");
        }

        // 取渠道配置
        FiscalConfig cfg = fiscalConfigService.getOne(new LambdaQueryWrapper<FiscalConfig>()
                .eq(FiscalConfig::getTenantId, tenantId)
                .eq(FiscalConfig::getChannel, inv.getChannel()));
        if (cfg == null) {
            return Result.error(BizCode.CONFIG_MISSING,
                    "渠道 " + inv.getChannel() + " 配置已删除，无法红冲");
        }

        FiscalGateway.VoidRequest vreq = new FiscalGateway.VoidRequest();
        vreq.setInvoiceId(id);
        vreq.setPlatformInvoiceNo(inv.getPlatformInvoiceNo());
        vreq.setReason(body == null ? "用户申请" : String.valueOf(body.getOrDefault("reason", "用户申请")));
        vreq.setRequestId("VOID-" + UUID.randomUUID());
        vreq.setAppKey(cfg.getAppKey());
        vreq.setAppSecret(cfg.getAppSecret());

        FiscalGateway.VoidResult result;
        try {
            result = fiscalGateway.voidInvoice(vreq);
        } catch (Exception e) {
            result = FiscalGateway.VoidResult.fail("GATEWAY_EXCEPTION", e.getMessage(), null);
        }
        inv.setRequestCompletedAt(LocalDateTime.now());
        inv.setPlatformResponse(result.getRawResponse());

        if (result.isSuccess()) {
            inv.setInvoiceStatus("VOIDED");
            inv.setPlatformVoidNo(result.getPlatformVoidNo());
            inv.setErrorMessage(null);
            invoiceMapper.updateById(inv);
            log.info("[FISCAL] VOIDED: invoiceNo={} platformInvoiceNo={} → voidNo={}",
                    inv.getInvoiceNo(), inv.getPlatformInvoiceNo(), result.getPlatformVoidNo());
            return Result.success("已红冲", null);
        }
        inv.setErrorMessage(result.getErrorCode() + ": " + result.getErrorMessage());
        inv.setRetryCount(inv.getRetryCount() == null ? 1 : inv.getRetryCount() + 1);
        invoiceMapper.updateById(inv);
        return Result.error(BizCode.FISCAL_GATEWAY_ERROR, inv.getErrorMessage());
    }

    @GetMapping("/list")
    public Result<List<FiscalInvoice>> listInvoices(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long orderId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<FiscalInvoice> qw = new LambdaQueryWrapper<FiscalInvoice>()
                .eq(FiscalInvoice::getTenantId, tenantId)
                .orderByDesc(FiscalInvoice::getId);
        if (status != null && !status.isEmpty()) qw.eq(FiscalInvoice::getInvoiceStatus, status);
        if (orderId != null) qw.eq(FiscalInvoice::getOrderId, orderId);
        return Result.success(invoiceMapper.selectList(qw));
    }

    @GetMapping("/{id}")
    public Result<FiscalInvoice> getInvoice(@PathVariable Long id) {
        FiscalInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) return Result.error(BizCode.NOT_FOUND, "发票不存在");
        return Result.success(inv);
    }

    @GetMapping("/channel/list")
    public Result<List<Map<String, Object>>> listChannels() {
        List<Map<String, Object>> channels = List.of(
                Map.of("code", "BAI_WANG", "name", "百望电子发票",
                        "fee", "0.06", "url", "https://eapi.baiwang.com/openapi/v1"),
                Map.of("code", "YIN_TIAN", "name", "航天信息",
                        "fee", "0.05", "url", "https://open.hanwang.info"),
                Map.of("code", "MOCK", "name", "Mock 模拟", "fee", "0",
                        "url", "https://mock-fiscal.local")
        );
        return Result.success(channels);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("totalIssued", invoiceMapper.selectCount(new LambdaQueryWrapper<FiscalInvoice>()
                    .eq(FiscalInvoice::getTenantId, tenantId)
                    .eq(FiscalInvoice::getInvoiceStatus, "ISSUED")));
            data.put("totalVoided", invoiceMapper.selectCount(new LambdaQueryWrapper<FiscalInvoice>()
                    .eq(FiscalInvoice::getTenantId, tenantId)
                    .eq(FiscalInvoice::getInvoiceStatus, "VOIDED")));
            data.put("totalPending", invoiceMapper.selectCount(new LambdaQueryWrapper<FiscalInvoice>()
                    .eq(FiscalInvoice::getTenantId, tenantId)
                    .eq(FiscalInvoice::getInvoiceStatus, "PENDING")));
            data.put("totalFailed", invoiceMapper.selectCount(new LambdaQueryWrapper<FiscalInvoice>()
                    .eq(FiscalInvoice::getTenantId, tenantId)
                    .eq(FiscalInvoice::getInvoiceStatus, "FAILED")));
            data.put("gatewayMode", fiscalGateway.mode());
        } catch (Exception ignore) {}
        return Result.success(data);
    }

    // ============= 内部工具 =============

    private List<FiscalGateway.LineItem> extractItems(Map<String, Object> body) {
        Object items = body.get("items");
        if (!(items instanceof List)) return List.of();
        List<FiscalGateway.LineItem> out = new ArrayList<>();
        for (Object o : (List<?>) items) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            FiscalGateway.LineItem li = new FiscalGateway.LineItem();
            li.setName(m.get("name") == null ? "服务费" : m.get("name").toString());
            li.setCode(m.get("code") == null ? "30100000000000000" : m.get("code").toString());
            li.setUnitPrice(m.get("unitPrice") == null ? BigDecimal.ZERO
                    : new BigDecimal(m.get("unitPrice").toString()));
            li.setQuantity(m.get("quantity") == null ? BigDecimal.ONE
                    : new BigDecimal(m.get("quantity").toString()));
            li.setTaxRate(m.get("taxRate") == null ? new BigDecimal("0.06")
                    : new BigDecimal(m.get("taxRate").toString()));
            li.setUnit(m.get("unit") == null ? "次" : m.get("unit").toString());
            out.add(li);
        }
        return out;
    }

    private String toJson(Object o) {
        if (jackson != null) {
            try { return jackson.writeValueAsString(o); }
            catch (Exception ignored) {}
        }
        // fallback
        return String.valueOf(o);
    }
}
