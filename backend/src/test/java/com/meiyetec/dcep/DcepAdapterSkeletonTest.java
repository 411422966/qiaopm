package com.meiyetec.dcep;

import com.meiyetec.dcep.DcepChannelAdapter.NotifyResult;
import com.meiyetec.dcep.DcepChannelAdapter.PrepayResult;
import com.meiyetec.dcep.DcepChannelAdapter.QueryResult;
import com.meiyetec.dcep.DcepChannelAdapter.RefundResult;
import com.meiyetec.dcep.DcepChannelAdapter.CloseRequest;
import com.meiyetec.dcep.DcepChannelAdapter.OrderQueryRequest;
import com.meiyetec.dcep.DcepChannelAdapter.RefundRequest;
import com.meiyetec.dcep.DcepChannelAdapter.PrepayRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-001 后续 · 适配器骨架测试
 *
 * <p>不依赖任何银行 SDK，验证：
 * <ul>
 *   <li>3 个银行实现都正确注册到 Spring 容器（channelCode 正确）</li>
 *   <li>占位实现在被调用时返回 NOT_IMPLEMENTED（保证 prod 模式安全）</li>
 *   <li>AbstractDcepAdapter 的共享工具方法（mapError / isTimestampValid / computeSign）正确</li>
 * </ul>
 */
class DcepAdapterSkeletonTest {

    @Test
    @DisplayName("ADAPTER-1: 3 个银行适配器 channelCode 正确")
    void channel_codes_correct() {
        assertEquals("DCEP-CCB", new com.meiyetec.dcep.impl.CcbDcepAdapter().channelCode());
        assertEquals("DCEP-ICBC", new com.meiyetec.dcep.impl.IcbcDcepAdapter().channelCode());
        assertEquals("DCEP-ABC", new com.meiyetec.dcep.impl.AbcDcepAdapter().channelCode());
    }

    @Test
    @DisplayName("ADAPTER-2: CCB 占位 prepay 返回 NOT_IMPLEMENTED（prod 模式安全）")
    void ccb_prepay_not_implemented() {
        var adapter = new com.meiyetec.dcep.impl.CcbDcepAdapter();
        PrepayRequest req = PrepayRequest.builder()
            .channelCode("DCEP-CCB")
            .dcepOrderNo("DCEP-TEST-001")
            .amountCents(12345L)
            .envMode("PROD")
            .build();
        PrepayResult result = adapter.prepay(req);
        assertFalse(result.isSuccess(), "占位实现必须 success=false");
        assertEquals("NOT_IMPLEMENTED", result.getErrorCode());
        assertTrue(result.getErrorMsg().contains("尚未对接"));
    }

    @Test
    @DisplayName("ADAPTER-3: ICBC 占位 prepay 返回 NOT_IMPLEMENTED")
    void icbc_prepay_not_implemented() {
        var adapter = new com.meiyetec.dcep.impl.IcbcDcepAdapter();
        PrepayResult result = adapter.prepay(PrepayRequest.builder().dcepOrderNo("DCEP-X").build());
        assertFalse(result.isSuccess());
        assertEquals("NOT_IMPLEMENTED", result.getErrorCode());
    }

    @Test
    @DisplayName("ADAPTER-4: ABC 占位 prepay 返回 NOT_IMPLEMENTED")
    void abc_prepay_not_implemented() {
        var adapter = new com.meiyetec.dcep.impl.AbcDcepAdapter();
        PrepayResult result = adapter.prepay(PrepayRequest.builder().dcepOrderNo("DCEP-X").build());
        assertFalse(result.isSuccess());
        assertEquals("NOT_IMPLEMENTED", result.getErrorCode());
    }

    @Test
    @DisplayName("ADAPTER-5: 占位 query 返回 NOT_IMPLEMENTED")
    void query_not_implemented() {
        OrderQueryRequest req = OrderQueryRequest.builder().dcepOrderNo("DCEP-X").build();
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.CcbDcepAdapter().query(req).getErrorCode());
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.IcbcDcepAdapter().query(req).getErrorCode());
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.AbcDcepAdapter().query(req).getErrorCode());
    }

    @Test
    @DisplayName("ADAPTER-6: 占位 refund 返回 NOT_IMPLEMENTED")
    void refund_not_implemented() {
        RefundRequest req = RefundRequest.builder().dcepOrderNo("DCEP-X").amountCents(100L).build();
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.CcbDcepAdapter().refund(req).getErrorCode());
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.IcbcDcepAdapter().refund(req).getErrorCode());
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.AbcDcepAdapter().refund(req).getErrorCode());
    }

    @Test
    @DisplayName("ADAPTER-7: 占位 closeOrder 返回 NOT_IMPLEMENTED")
    void close_not_implemented() {
        CloseRequest req = CloseRequest.builder().dcepOrderNo("DCEP-X").build();
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.CcbDcepAdapter().closeOrder(req).getErrorCode());
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.IcbcDcepAdapter().closeOrder(req).getErrorCode());
        assertEquals("NOT_IMPLEMENTED", new com.meiyetec.dcep.impl.AbcDcepAdapter().closeOrder(req).getErrorCode());
    }

    @Test
    @DisplayName("ADAPTER-8: 占位 parseAndVerifyNotify 签名无效 / 时间戳无效")
    void parse_notify_signature_invalid() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Sign", "fake-sign");
        NotifyResult ccbResult = new com.meiyetec.dcep.impl.CcbDcepAdapter()
            .parseAndVerifyNotify("{\"foo\":\"bar\"}", headers);
        assertFalse(ccbResult.isSignatureValid());
        assertFalse(ccbResult.isTimestampValid());
        assertEquals("{\"foo\":\"bar\"}", ccbResult.getRawBody());
    }

    @Test
    @DisplayName("ADAPTER-9: AbstractDcepAdapter.mapError 通用错误码映射")
    void abstract_map_error() {
        com.meiyetec.dcep.AbstractDcepAdapter adapter = new com.meiyetec.dcep.AbstractDcepAdapter() {
            @Override public String channelCode() { return "TEST"; }
            @Override public PrepayResult prepay(PrepayRequest req) { return null; }
            @Override public QueryResult query(OrderQueryRequest req) { return null; }
            @Override public RefundResult refund(RefundRequest req) { return null; }
            @Override public CloseResult closeOrder(CloseRequest req) { return null; }
            @Override public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) { return null; }
        };
        assertEquals("OK", adapter.mapError("00000"));
        assertEquals("OK", adapter.mapError("SUCCESS"));
        assertEquals("OK", adapter.mapError("OK"));
        assertEquals("INVALID_SIGN", adapter.mapError("INVALID_SIGNATURE"));
        assertEquals("INVALID_SIGN", adapter.mapError("INVALID_SIGN"));
        assertEquals("INVALID_PARAMS", adapter.mapError("BAD_REQUEST"));
        assertEquals("MERCHANT_NOT_FOUND", adapter.mapError("MERCHANT_NOT_EXIST"));
        assertEquals("INSUFFICIENT_BALANCE", adapter.mapError("BALANCE_NOT_ENOUGH"));
        assertEquals("ORDER_EXPIRED", adapter.mapError("ORDER_EXPIRED"));
        assertEquals("USER_CANCEL", adapter.mapError("USER_CANCELED"));
        assertEquals("RISK_REJECTED", adapter.mapError("RISK_BLOCK"));
        assertEquals("RATE_LIMIT", adapter.mapError("TOO_MANY_REQUESTS"));
        assertEquals("BANK_SYSTEM_ERROR", adapter.mapError("UNKNOWN_CODE"));
        assertEquals("BANK_SYSTEM_ERROR", adapter.mapError(null));
    }

    @Test
    @DisplayName("ADAPTER-10: AbstractDcepAdapter.isTimestampValid 时间戳校验")
    void abstract_is_timestamp_valid() {
        com.meiyetec.dcep.AbstractDcepAdapter adapter = new com.meiyetec.dcep.AbstractDcepAdapter() {
            @Override public String channelCode() { return "TEST"; }
            @Override public PrepayResult prepay(PrepayRequest req) { return null; }
            @Override public QueryResult query(OrderQueryRequest req) { return null; }
            @Override public RefundResult refund(RefundRequest req) { return null; }
            @Override public CloseResult closeOrder(CloseRequest req) { return null; }
            @Override public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) { return null; }
        };
        long now = System.currentTimeMillis() / 1000;
        assertTrue(adapter.isTimestampValid(String.valueOf(now)));
        assertTrue(adapter.isTimestampValid(String.valueOf(now - 100)));
        assertTrue(adapter.isTimestampValid(String.valueOf(now + 100)));
        assertFalse(adapter.isTimestampValid(String.valueOf(now - 600)), "10 分钟前应失效");
        assertFalse(adapter.isTimestampValid(String.valueOf(now + 600)), "10 分钟后应失效");
        assertFalse(adapter.isTimestampValid(null));
        assertFalse(adapter.isTimestampValid("not-a-number"));
    }

    @Test
    @DisplayName("ADAPTER-11: AbstractDcepAdapter.computeSign 确定性 + 不同输入不同输出")
    void abstract_compute_sign() {
        com.meiyetec.dcep.AbstractDcepAdapter adapter = new com.meiyetec.dcep.AbstractDcepAdapter() {
            @Override public String channelCode() { return "TEST"; }
            @Override public PrepayResult prepay(PrepayRequest req) { return null; }
            @Override public QueryResult query(OrderQueryRequest req) { return null; }
            @Override public RefundResult refund(RefundRequest req) { return null; }
            @Override public CloseResult closeOrder(CloseRequest req) { return null; }
            @Override public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) { return null; }
        };
        String sig1 = adapter.computeSign("SHA256", "order=DCEP-001&amount=100", "secret-key");
        String sig2 = adapter.computeSign("SHA256", "order=DCEP-001&amount=100", "secret-key");
        String sig3 = adapter.computeSign("SHA256", "order=DCEP-002&amount=100", "secret-key");
        assertEquals(sig1, sig2, "相同输入应产生相同签名");
        assertNotEquals(sig1, sig3, "不同输入应产生不同签名");
        assertFalse(sig1.isEmpty());
    }

    @Test
    @DisplayName("ADAPTER-12: AbstractDcepAdapter.parseJson 解析 + 容错")
    void abstract_parse_json() {
        com.meiyetec.dcep.AbstractDcepAdapter adapter = new com.meiyetec.dcep.AbstractDcepAdapter() {
            @Override public String channelCode() { return "TEST"; }
            @Override public PrepayResult prepay(PrepayRequest req) { return null; }
            @Override public QueryResult query(OrderQueryRequest req) { return null; }
            @Override public RefundResult refund(RefundRequest req) { return null; }
            @Override public CloseResult closeOrder(CloseRequest req) { return null; }
            @Override public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) { return null; }
        };
        Map<String, Object> m = adapter.parseJson("{\"orderId\":\"DCEP-001\",\"amount\":100}");
        assertEquals("DCEP-001", m.get("orderId"));
        assertEquals(100, m.get("amount"));

        // 空 / null 安全
        Map<String, Object> empty = adapter.parseJson("");
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        // 非法 JSON 安全
        Map<String, Object> safe = adapter.parseJson("not-json{");
        assertNotNull(safe);
        assertTrue(safe.isEmpty());
    }
}
