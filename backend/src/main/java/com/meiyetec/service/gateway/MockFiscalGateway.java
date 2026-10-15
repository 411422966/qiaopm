package com.meiyetec.service.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FISCAL-001：Mock 财税网关
 *
 * <p>激活条件：{@code fiscal.mode=mock}（默认）
 *
 * <p>沙箱/无凭据环境的安全默认值：模拟百望开票接口的协议格式返回，但所有数据都是模拟。
 * 用于开发测试 + 联调，避免因无真实凭据而阻断流程。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "fiscal", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockFiscalGateway implements FiscalGateway {

    @Override
    public IssueResult issue(IssueRequest req) {
        if (req == null) {
            return IssueResult.fail("BAD_REQUEST", "request is null", null);
        }
        // 模拟网络延迟
        try { Thread.sleep(80L + (long) (Math.random() * 200)); } catch (InterruptedException ignored) {}

        String invNo = "MOCK-BW-" + System.currentTimeMillis();
        String pdfUrl = "https://mock-fiscal.local/invoice/" + invNo + ".pdf";
        String raw = buildMockRawResponse(invNo, pdfUrl);
        log.info("[FISCAL/MOCK] issue orderNo={} invoiceNo={}", req.getOrderNo(), invNo);
        return IssueResult.ok(invNo, pdfUrl, raw);
    }

    @Override
    public VoidResult voidInvoice(VoidRequest req) {
        if (req == null || req.getPlatformInvoiceNo() == null) {
            return VoidResult.fail("BAD_REQUEST", "缺少 platformInvoiceNo", null);
        }
        try { Thread.sleep(80L + (long) (Math.random() * 200)); } catch (InterruptedException ignored) {}

        String voidNo = "MOCK-RED-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        Map<String, Object> r = new HashMap<>();
        r.put("code", "0000");
        r.put("message", "success");
        r.put("voidInvoiceNo", voidNo);
        r.put("originalInvoiceNo", req.getPlatformInvoiceNo());
        log.info("[FISCAL/MOCK] voidInvoice invNo={} voidNo={}", req.getPlatformInvoiceNo(), voidNo);
        return VoidResult.ok(voidNo, MiniJson.stringify(r));
    }

    @Override
    public String mode() {
        return "MOCK";
    }

    private String buildMockRawResponse(String invNo, String pdfUrl) {
        Map<String, Object> r = new HashMap<>();
        r.put("code", "0000");
        r.put("message", "success");
        r.put("invoiceNo", invNo);
        r.put("pdfUrl", pdfUrl);
        r.put("issuedAt", java.time.Instant.now().toString());
        return MiniJson.stringify(r);
    }

    /**
     * 极简 JSON 序列化（与 BaiwangFiscalGateway 中的一致）
     */
    static class MiniJson {
        static String stringify(Object o) {
            if (o == null) return "null";
            if (o instanceof Number || o instanceof Boolean) return o.toString();
            if (o instanceof Map) {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\"").append(e.getKey()).append("\":");
                    sb.append(stringify(e.getValue()));
                }
                return sb.append("}").toString();
            }
            if (o instanceof Iterable) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object x : (Iterable<?>) o) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append(stringify(x));
                }
                return sb.append("]").toString();
            }
            return "\"" + o.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
    }
}
