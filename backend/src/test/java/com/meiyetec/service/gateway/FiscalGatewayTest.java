package com.meiyetec.service.gateway;

import com.meiyetec.service.FiscalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FISCAL-001：财税网关单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>MockFiscalGateway 全流程：issue / voidInvoice 必返回 success</li>
 *   <li>FiscalGateway 接口契约：返回 DTO 字段非空校验</li>
 *   <li>FiscalService 接口保留：issue / listInvoices / getInvoice / voidInvoice / listChannels / dashboard</li>
 *   <li>BaiwangFiscalGateway 签名算法：signRequest 产生稳定签名</li>
 * </ul>
 */
@DisplayName("FISCAL-001 财税网关")
class FiscalGatewayTest {

    @Test
    @DisplayName("Mock 网关：issue 返回成功 + 平台发票号 + PDF URL")
    void mockIssueSuccess() {
        MockFiscalGateway gw = new MockFiscalGateway();
        FiscalGateway.IssueRequest req = buildSampleRequest();
        FiscalGateway.IssueResult r = gw.issue(req);
        assertTrue(r.isSuccess());
        assertNotNull(r.getPlatformInvoiceNo());
        assertTrue(r.getPlatformInvoiceNo().startsWith("MOCK-BW-"));
        assertNotNull(r.getPdfUrl());
        assertTrue(r.getPdfUrl().endsWith(".pdf"));
        assertNotNull(r.getRawResponse());
        assertEquals("MOCK", gw.mode());
    }

    @Test
    @DisplayName("Mock 网关：voidInvoice 返回成功 + 红冲号")
    void mockVoidSuccess() {
        MockFiscalGateway gw = new MockFiscalGateway();
        FiscalGateway.VoidRequest req = new FiscalGateway.VoidRequest();
        req.setInvoiceId(123L);
        req.setPlatformInvoiceNo("MOCK-BW-123");
        req.setReason("测试红冲");
        req.setRequestId("V-1");
        FiscalGateway.VoidResult r = gw.voidInvoice(req);
        assertTrue(r.isSuccess());
        assertNotNull(r.getPlatformVoidNo());
        assertTrue(r.getPlatformVoidNo().startsWith("MOCK-RED-"));
        assertNotNull(r.getRawResponse());
    }

    @Test
    @DisplayName("Mock 网关：voidInvoice 缺平台发票号 → BAD_REQUEST")
    void mockVoidMissing() {
        MockFiscalGateway gw = new MockFiscalGateway();
        FiscalGateway.VoidResult r = gw.voidInvoice(new FiscalGateway.VoidRequest());
        assertFalse(r.isSuccess());
        assertEquals("BAD_REQUEST", r.getErrorCode());
    }

    @Test
    @DisplayName("IssueRequest.toPlatformPayload() 生成百望字段")
    void issuePayloadFormat() {
        FiscalGateway.IssueRequest req = buildSampleRequest();
        Map<String, Object> p = req.toPlatformPayload();
        assertEquals("order-1", p.get("orderNo"));
        assertEquals("91110000123456789X", p.get("sellerTaxNo"));
        assertEquals("美业科技有限公司", p.get("buyerName"));
        assertEquals("91110000987654321Y", p.get("buyerTaxNo"));
        assertEquals("01", p.get("invoiceTypeCode"));    // GENERAL
        assertNotNull(p.get("goodsList"));
        assertTrue(p.get("goodsList") instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goods = (List<Map<String, Object>>) p.get("goodsList");
        assertEquals(2, goods.size());
        assertEquals("面部护理", goods.get(0).get("goodsName"));
    }

    @Test
    @DisplayName("IssueRequest.toPlatformPayload() 专票 → 02")
    void issuePayloadSpecialInvoice() {
        FiscalGateway.IssueRequest req = buildSampleRequest();
        req.setInvoiceType("SPECIAL");
        assertEquals("02", req.toPlatformPayload().get("invoiceTypeCode"));
    }

    @Test
    @DisplayName("百望 HMAC 签名：同一输入产生稳定签名")
    void baiwangSignStable() {
        String s1 = BaiwangFiscalGateway.signRequest(
                "POST", "/openapi/v1/invoice/issue", "",
                "{\"orderNo\":\"123\"}", "secret-1",
                "1700000000", "nonce-abc");
        String s2 = BaiwangFiscalGateway.signRequest(
                "POST", "/openapi/v1/invoice/issue", "",
                "{\"orderNo\":\"123\"}", "secret-1",
                "1700000000", "nonce-abc");
        assertEquals(s1, s2);
        assertFalse(s1.isEmpty());
        // 签名是 base64 字符串，长度合理
        assertTrue(s1.length() >= 40 && s1.length() <= 60);
    }

    @Test
    @DisplayName("百望 HMAC 签名：nonce 改变 → 签名变化")
    void baiwangSignNonceChangesSignature() {
        String s1 = BaiwangFiscalGateway.signRequest(
                "POST", "/openapi/v1/invoice/issue", "",
                "{\"orderNo\":\"123\"}", "secret-1",
                "1700000000", "nonce-A");
        String s2 = BaiwangFiscalGateway.signRequest(
                "POST", "/openapi/v1/invoice/issue", "",
                "{\"orderNo\":\"123\"}", "secret-1",
                "1700000000", "nonce-B");
        assertNotEquals(s1, s2);
    }

    @Test
    @DisplayName("FiscalService 兼容旧 API：方法齐全")
    void fiscalServiceBackwardCompat() throws Exception {
        Class<?> c = FiscalService.class;
        assertHasMethod(c, "issue");
        assertHasMethod(c, "listInvoices");
        assertHasMethod(c, "getInvoice");
        assertHasMethod(c, "voidInvoice");
        assertHasMethod(c, "listChannels");
        assertHasMethod(c, "dashboard");
    }

    @Test
    @DisplayName("FiscalGateway 必要方法齐全")
    void gatewayApi() throws Exception {
        Class<?> c = FiscalGateway.class;
        assertHasMethod(c, "issue");
        assertHasMethod(c, "voidInvoice");
        assertHasMethod(c, "mode");
    }

    // ==================== helpers ====================

    private FiscalGateway.IssueRequest buildSampleRequest() {
        FiscalGateway.IssueRequest r = new FiscalGateway.IssueRequest();
        r.setOrderNo("order-1");
        r.setSellerTaxNo("91110000123456789X");
        r.setSellerName("美业旗舰店");
        r.setBuyerName("美业科技有限公司");
        r.setBuyerTaxNo("91110000987654321Y");
        r.setBuyerAddress("北京朝阳区");
        r.setBuyerPhone("13800138000");
        r.setBuyerEmail("buyer@meiyetec.com");
        r.setInvoiceType("GENERAL");
        r.setAppKey("test-app-key");
        r.setAppSecret("test-app-secret");
        r.setRequestId("REQ-1");
        r.setRemark("测试开票");
        r.setItems(List.of(
                new FiscalGateway.LineItem("面部护理", "30100000000000000",
                        new BigDecimal("199.00"), new BigDecimal("1"), new BigDecimal("0.06"), "次"),
                new FiscalGateway.LineItem("身体SPA", "30100000000000001",
                        new BigDecimal("299.00"), new BigDecimal("1"), new BigDecimal("0.06"), "次")
        ));
        return r;
    }

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }
}
