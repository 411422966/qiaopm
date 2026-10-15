package com.meiyetec.service;

import com.meiyetec.config.DcepConfigProperties;
import com.meiyetec.entity.PayDcepChannel;
import com.meiyetec.entity.PayDcepNotify;
import com.meiyetec.entity.PayDcepOrder;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-001 · DcepNotifyTest：异步回调测试
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcepNotifyTest {

    @Autowired
    private DcepService dcepService;

    @Autowired
    private DcepConfigProperties dcepConfig;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private Long channelId;
    private Long posOrderId;
    private Long dcepOrderId;
    private String dcepOrderNo;
    private String notifyId;

    public static final AtomicInteger eventCount = new AtomicInteger(0);
    public static final AtomicReference<PosOrder> lastEventOrder = new AtomicReference<>();

    @BeforeAll
    void setupClass() {
        TenantContext tenantCtx = new TenantContext();
        tenantCtx.setTenantId(1L);
        tenantCtx.setUserId(1L);
        TenantContextHolder.set(tenantCtx);

        // Create channel (clean residue first)
        jdbc.update("DELETE FROM pay_dcep_channel WHERE tenant_id = 1 AND channel_code = 'DCEP-NOTIFY-TEST'");
        jdbc.update("INSERT INTO pay_dcep_channel (tenant_id, channel_code, channel_name, env_mode, merchant_id, api_base_url, status, create_time, deleted) " +
                "VALUES (1, 'DCEP-NOTIFY-TEST', 'NotifyTest', 'MOCK', 'TEST-MERCHANT-NOTIFY', 'http://localhost:8080/mock', 1, NOW(), 0)");
        channelId = jdbc.queryForObject(
            "SELECT id FROM pay_dcep_channel WHERE channel_code = 'DCEP-NOTIFY-TEST' ORDER BY id DESC LIMIT 1",
            Long.class);

        // Create POS order (cashier_id is NOT NULL)
        String orderNo = "TEST-DCEP-NOTIFY-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO pos_order (tenant_id, store_id, order_no, type, total_amount, final_amount, cashier_id, status, create_time, update_time, deleted) " +
                "VALUES (1, 1, ?, 1, 100.00, 100.00, 1, 1, NOW(), NOW(), 0)", orderNo);
        posOrderId = jdbc.queryForObject("SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);

        // Create DCEP order
        dcepOrderNo = "DCEP-NOTIFY-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO pay_dcep_order (tenant_id, channel_id, channel_code, dcep_order_no, biz_order_id, biz_order_no, amount_cents, status, expire_at, create_time, deleted) " +
                "VALUES (1, ?, 'DCEP-NOTIFY-TEST', ?, ?, ?, 10000, 1, ?, NOW(), 0)",
                channelId, dcepOrderNo, posOrderId, orderNo, LocalDateTime.now().plusMinutes(15));
        dcepOrderId = jdbc.queryForObject("SELECT id FROM pay_dcep_order WHERE dcep_order_no = ?", Long.class, dcepOrderNo);

        eventCount.set(0);
    }

    @AfterAll
    void tearDownClass() {
        jdbc.update("DELETE FROM pay_dcep_notify WHERE dcep_order_id = ?", dcepOrderId);
        jdbc.update("UPDATE pay_dcep_order SET deleted = 1 WHERE id = ?", dcepOrderId);
        jdbc.update("UPDATE pay_dcep_channel SET deleted = 1 WHERE id = ?", channelId);
        jdbc.update("UPDATE pos_order SET deleted = 1 WHERE id = ?", posOrderId);
        TenantContextHolder.clear();
    }

    private String createPosOrderForRefund() {
        String orderNo = "TEST-DCEP-REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO pos_order (tenant_id, store_id, order_no, type, total_amount, final_amount, cashier_id, status, create_time, update_time, deleted) " +
                "VALUES (1, 1, ?, 1, 100.00, 100.00, 1, 1, NOW(), NOW(), 0)", orderNo);
        return orderNo;
    }

    private Long createDcepOrderForRefund(String orderNo) {
        String dcepNo = "DCEP-REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        Long posId = jdbc.queryForObject("SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);
        jdbc.update("INSERT INTO pay_dcep_order (tenant_id, channel_id, channel_code, dcep_order_no, biz_order_id, biz_order_no, amount_cents, status, expire_at, create_time, deleted) " +
                "VALUES (1, ?, 'DCEP-NOTIFY-TEST', ?, ?, ?, 10000, 1, ?, NOW(), 0)",
                channelId, dcepNo, posId, orderNo, LocalDateTime.now().plusMinutes(15));
        return jdbc.queryForObject("SELECT id FROM pay_dcep_order WHERE dcep_order_no = ?", Long.class, dcepNo);
    }

    private Map<String, Object> buildPayload(String eventType, String sign, String notifyIdOverride) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("notifyId", notifyIdOverride != null ? notifyIdOverride : (notifyId != null ? notifyId : "NOTIFY-" + UUID.randomUUID().toString().substring(0, 8)));
        payload.put("dcepOrderNo", dcepOrderNo);
        payload.put("eventType", eventType);
        payload.put("transactionId", "TX-" + UUID.randomUUID().toString().substring(0, 8));
        payload.put("signAlgo", "RSA-SHA256");
        payload.put("sign", sign);
        return payload;
    }

    @Test
    @Order(1)
    @DisplayName("NOTIFY-1: PAY_SUCCESS + 正确签名 -> 订单 status=2 + pos_order 更新")
    void notify_pay_success_correct_sign() {
        dcepConfig.setMode("dev");
        notifyId = "NOTIFY-PAY-" + UUID.randomUUID().toString().substring(0, 8);
        // P0-T12: dev 模式仅接受 MOCK- 前缀签名（fail-fast 阻断真银行回调）
        String correctSign = "MOCK-PAY-" + notifyId;

        Result<Map<String, Object>> resp = dcepService.notify("DCEP-NOTIFY-TEST", buildPayload("PAY_SUCCESS", correctSign, notifyId));

        assertEquals(200, resp.getCode(), "PAY_SUCCESS + correct sign should succeed");
        assertEquals("SUCCESS", resp.getData().get("code"));

        // DCEP order status
        Integer status = jdbc.queryForObject("SELECT status FROM pay_dcep_order WHERE id = ?", Integer.class, dcepOrderId);
        assertEquals(2, status, "DCEP order should be PAID(2)");
        Integer notifyCount = jdbc.queryForObject("SELECT notify_count FROM pay_dcep_order WHERE id = ?", Integer.class, dcepOrderId);
        assertEquals(1, notifyCount, "notify_count should be 1");

        // POS order status
        Integer posStatus = jdbc.queryForObject("SELECT status FROM pos_order WHERE id = ?", Integer.class, posOrderId);
        assertEquals(2, posStatus, "POS order should be PAID(2)");
        java.sql.Timestamp payTime = jdbc.queryForObject("SELECT pay_time FROM pos_order WHERE id = ?", java.sql.Timestamp.class, posOrderId);
        assertNotNull(payTime, "pay_time should be set");

        // notify log
        Integer notifyCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pay_dcep_notify WHERE dcep_order_id = ? AND notify_id = ?",
            Integer.class, dcepOrderId, notifyId);
        assertEquals(1, notifyCnt, "should have 1 notify log");
    }

    @Test
    @Order(2)
    @DisplayName("NOTIFY-2: wrong sign -> 403 FORBIDDEN, no order update")
    void notify_wrong_sign_rejected() {
        dcepConfig.setMode("dev");
        notifyId = "NOTIFY-WRONG-" + UUID.randomUUID().toString().substring(0, 8);

        Result<Map<String, Object>> resp = dcepService.notify("DCEP-NOTIFY-TEST",
            buildPayload("PAY_SUCCESS", "wrong-signature", notifyId));

        assertEquals(403, resp.getCode(), "wrong sign should return 403 FORBIDDEN");

        Integer status = jdbc.queryForObject("SELECT status FROM pay_dcep_order WHERE id = ?", Integer.class, dcepOrderId);
        assertEquals(2, status, "order status should not change");
    }

    @Test
    @Order(3)
    @DisplayName("NOTIFY-3: duplicate notifyId -> 200 SUCCESS (idempotent)")
    void notify_duplicate_idempotent() {
        dcepConfig.setMode("dev");
        notifyId = "NOTIFY-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        // P0-T12: dev 模式仅接受 MOCK- 前缀签名
        String correctSign = "MOCK-DUP-" + notifyId;

        // First call: success
        Result<Map<String, Object>> first = dcepService.notify("DCEP-NOTIFY-TEST", buildPayload("PAY_SUCCESS", correctSign, notifyId));
        assertEquals(200, first.getCode(), "first call should succeed");

        Integer notifyCountBefore = jdbc.queryForObject(
            "SELECT notify_count FROM pay_dcep_order WHERE id = ?", Integer.class, dcepOrderId);

        // Second call with same notifyId
        Result<Map<String, Object>> second = dcepService.notify("DCEP-NOTIFY-TEST", buildPayload("PAY_SUCCESS", correctSign, notifyId));
        assertEquals(200, second.getCode(), "second call should succeed (idempotent)");
        assertEquals("SUCCESS", second.getData().get("code"));
        assertEquals("duplicate ignored", second.getData().get("message"));

        Integer notifyCountAfter = jdbc.queryForObject(
            "SELECT notify_count FROM pay_dcep_order WHERE id = ?", Integer.class, dcepOrderId);
        assertEquals(notifyCountBefore, notifyCountAfter, "duplicate notify should not increment notify_count");
    }

    @Test
    @Order(4)
    @DisplayName("NOTIFY-4: non-existent dcepOrderNo -> 404 NOT_FOUND")
    void notify_order_not_found() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = new HashMap<>();
        payload.put("notifyId", "NOTIFY-NF-" + UUID.randomUUID().toString().substring(0, 8));
        payload.put("dcepOrderNo", "DCEP-NOT-EXIST-" + UUID.randomUUID().toString().substring(0, 8));
        payload.put("eventType", "PAY_SUCCESS");
        payload.put("sign", "MOCK-anything");
        payload.put("transactionId", "TX-NF");

        Result<Map<String, Object>> resp = dcepService.notify("DCEP-NOTIFY-TEST", payload);

        assertEquals(404, resp.getCode(), "non-existent order should return 404");
    }

    @Test
    @Order(5)
    @DisplayName("NOTIFY-5: REFUND_SUCCESS event -> DCEP order status=4")
    void notify_refund_success() {
        dcepConfig.setMode("dev");

        String orderNo = createPosOrderForRefund();
        Long newDcepOrderId = createDcepOrderForRefund(orderNo);
        String newDcepOrderNo = jdbc.queryForObject(
            "SELECT dcep_order_no FROM pay_dcep_order WHERE id = ?", String.class, newDcepOrderId);

        // Mark as PAID first
        jdbc.update("UPDATE pay_dcep_order SET status = 2 WHERE id = ?", newDcepOrderId);

        String refundNotifyId = "NOTIFY-REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        // P0-T12: dev 模式仅接受 MOCK- 前缀签名
        String sign = "MOCK-REFUND-" + refundNotifyId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("notifyId", refundNotifyId);
        payload.put("dcepOrderNo", newDcepOrderNo);
        payload.put("eventType", "REFUND_SUCCESS");
        payload.put("sign", sign);

        Result<Map<String, Object>> resp = dcepService.notify("DCEP-NOTIFY-TEST", payload);

        assertEquals(200, resp.getCode(), "REFUND should succeed");
        Integer status = jdbc.queryForObject(
            "SELECT status FROM pay_dcep_order WHERE id = ?", Integer.class, newDcepOrderId);
        assertEquals(4, status, "order should be REFUNDED(4)");

        jdbc.update("DELETE FROM pay_dcep_notify WHERE dcep_order_id = ?", newDcepOrderId);
        jdbc.update("UPDATE pay_dcep_order SET deleted = 1 WHERE id = ?", newDcepOrderId);
        jdbc.update("UPDATE pos_order SET deleted = 1 WHERE order_no = ?", orderNo);
    }

    @Test
    @Order(6)
    @DisplayName("NOTIFY-6: CLOSE event -> DCEP order status=3")
    void notify_close() {
        dcepConfig.setMode("dev");

        String orderNo = createPosOrderForRefund();
        Long newDcepOrderId = createDcepOrderForRefund(orderNo);
        String newDcepOrderNo = jdbc.queryForObject(
            "SELECT dcep_order_no FROM pay_dcep_order WHERE id = ?", String.class, newDcepOrderId);

        String closeNotifyId = "NOTIFY-CLOSE-" + UUID.randomUUID().toString().substring(0, 8);
        // P0-T12: dev 模式仅接受 MOCK- 前缀签名
        String sign = "MOCK-CLOSE-" + closeNotifyId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("notifyId", closeNotifyId);
        payload.put("dcepOrderNo", newDcepOrderNo);
        payload.put("eventType", "CLOSE");
        payload.put("sign", sign);

        Result<Map<String, Object>> resp = dcepService.notify("DCEP-NOTIFY-TEST", payload);

        assertEquals(200, resp.getCode(), "CLOSE should succeed");
        Integer status = jdbc.queryForObject(
            "SELECT status FROM pay_dcep_order WHERE id = ?", Integer.class, newDcepOrderId);
        assertEquals(3, status, "order should be CLOSED(3)");

        jdbc.update("DELETE FROM pay_dcep_notify WHERE dcep_order_id = ?", newDcepOrderId);
        jdbc.update("UPDATE pay_dcep_order SET deleted = 1 WHERE id = ?", newDcepOrderId);
        jdbc.update("UPDATE pos_order SET deleted = 1 WHERE order_no = ?", orderNo);
    }
}
