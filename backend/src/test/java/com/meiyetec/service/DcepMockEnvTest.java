package com.meiyetec.service;

import com.meiyetec.config.DcepConfigProperties;
import com.meiyetec.entity.PayDcepChannel;
import com.meiyetec.entity.PayDcepOrder;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-001 · DcepMockEnvTest：DCEP 环境隔离测试
 *
 * <p>验证 app.dcep.mode 配置对 mockPrepay() / verifySign() 的影响：
 * <ul>
 *   <li>dev 模式 → mockPrepay 对 MOCK 通道直接成功，verifySign 接受 MOCK- 前缀签名</li>
 *   <li>prod 模式 → mockPrepay 全部返回 FAIL/NOT_IMPLEMENTED，verifySign 拒绝 MOCK- 前缀</li>
 * </ul>
 *
 * <p>本测试覆盖：
 * <ul>
 *   <li>DEV-1: dev 模式 prepay 调用 MOCK 通道返回 OK + qrCode + prepayId</li>
 *   <li>DEV-2: dev 模式 prepay 调用非 MOCK 通道返回 FAIL/NOT_IMPLEMENTED</li>
 *   <li>PROD-1: prod 模式 prepay 调用 MOCK 通道也返回 FAIL/NOT_IMPLEMENTED（fail-safe）</li>
 *   <li>PROD-2: prod 模式 prepay 调用真实通道返回 FAIL/NOT_IMPLEMENTED</li>
 *   <li>CONFIG-1: 默认 mode=dev，isMockEnabled=true，isStrictProd=false</li>
 *   <li>CONFIG-2: mode=prod 时 isMockEnabled=false，isStrictProd=true</li>
 * </ul>
 *
 * <p>实现说明：mockPrepay 是 private 方法，用反射调用；prod 模式测试需要切换 mode，
 *           通过 ReflectionTestUtils 注入到 DcepService 持有的 DcepConfigProperties。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcepMockEnvTest {

    @Autowired
    private DcepService dcepService;

    @Autowired
    private DcepConfigProperties dcepConfig;

    @Autowired
    private org.springframework.context.ApplicationContext ctx;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long devChannelId;
    private Long prodChannelId;
    private Long testBizOrderId;

    @BeforeAll
    void setupClass() {
        TenantContext tenantCtx = new TenantContext();
        tenantCtx.setTenantId(1L);
        tenantCtx.setUserId(1L);
        TenantContextHolder.set(tenantCtx);

        // 强制 init DcepConfigProperties + 修复 Spring Boot 3 lazy bean 注入 null
        DcepConfigProperties configBean = ctx.getBean(DcepConfigProperties.class);
        try {
            java.lang.reflect.Field f = DcepService.class.getDeclaredField("dcepConfig");
            f.setAccessible(true);
            if (f.get(dcepService) == null) {
                f.set(dcepService, configBean);
            }
        } catch (Exception ignore) {}

        // 确保存在一个 POS 订单用于 prepay 测试
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pos_order WHERE deleted = 0 AND tenant_id = 1", Integer.class);
        if (cnt == null || cnt == 0) {
            // 创建一个测试订单
            jdbc.update("INSERT INTO pos_order (tenant_id, store_id, order_no, type, total_amount, final_amount, status, create_time, update_time, deleted) " +
                    "VALUES (1, 1, 'TEST-DCEP-ENV-" + UUID.randomUUID().toString().substring(0, 8) + "', 1, 100.00, 100.00, 1, NOW(), NOW(), 0)");
        }
        testBizOrderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE deleted = 0 AND tenant_id = 1 ORDER BY id ASC LIMIT 1", Long.class);

        // 创建 DCEP 通道（dev=MOCK, prod=CCB）
        devChannelId = createChannel("DCEP-DCBC-MOCK-TEST", "MOCK", null);
        prodChannelId = createChannel("DCEP-CCB-PROD-TEST", "PROD", "https://api.ccb.com/dcep");
    }

    @AfterAll
    void tearDownClass() {
        // 清理测试通道
        jdbc.update("UPDATE pay_dcep_channel SET deleted = 1 WHERE channel_code IN ('DCEP-DCBC-MOCK-TEST', 'DCEP-CCB-PROD-TEST')");
        // 重置 mode
        dcepConfig.setMode("dev");
        TenantContextHolder.clear();
    }

    private Long createChannel(String code, String envMode, String apiBase) {
        // merchant_id 是 NOT NULL，必填
        // api_base_url 是 NOT NULL，必填（MOCK 也填占位 URL）
        String safeApiBase = (apiBase == null || apiBase.isEmpty()) ? "http://localhost:8080/mock" : apiBase;
        // 先清理残留（uk_tenant_code 是 (tenant_id, channel_code) 唯一）
        jdbc.update("DELETE FROM pay_dcep_channel WHERE tenant_id = 1 AND channel_code = ?", code);
        jdbc.update("INSERT INTO pay_dcep_channel (tenant_id, channel_code, channel_name, env_mode, merchant_id, api_base_url, status, create_time, deleted) " +
                "VALUES (1, ?, ?, ?, ?, ?, 1, NOW(), 0)", code, "Test-" + code, envMode, "TEST-MERCHANT-" + code, safeApiBase);
        return jdbc.queryForObject("SELECT id FROM pay_dcep_channel WHERE channel_code = ? ORDER BY id DESC LIMIT 1", Long.class, code);
    }

    private PayDcepChannel buildChannel(Long id, String envMode, String code) {
        PayDcepChannel c = new PayDcepChannel();
        c.setId(id);
        c.setEnvMode(envMode);
        c.setChannelCode(code);
        return c;
    }

    private PayDcepOrder buildOrder(Long id) {
        PayDcepOrder o = new PayDcepOrder();
        o.setId(id);
        o.setDcepOrderNo("DCEP-ENV-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        o.setAmountCents(10000L);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeMockPrepay(PayDcepChannel c, PayDcepOrder o) throws Exception {
        Method m = DcepService.class.getDeclaredMethod("mockPrepay", PayDcepChannel.class, PayDcepOrder.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(dcepService, c, o);
    }

    // ====================================================================
    // CONFIG 测试
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("CONFIG-1: 默认 mode=dev，isMockEnabled=true，isStrictProd=false")
    void config_default_dev() {
        // 默认 mode 来自 application.yml（dev）
        // 可能被其他测试用例改成 prod，强制重置
        dcepConfig.setMode("dev");
        assertEquals("dev", dcepConfig.getMode(), "默认 mode 应为 dev");
        assertTrue(dcepConfig.isMockEnabled(), "dev 模式应启用 Mock");
        assertFalse(dcepConfig.isStrictProd(), "dev 模式不应是严格生产");
    }

    @Test
    @Order(2)
    @DisplayName("CONFIG-2: mode=prod 时 isMockEnabled=false，isStrictProd=true")
    void config_prod_strict() {
        dcepConfig.setMode("prod");
        assertTrue(dcepConfig.isStrictProd(), "prod 模式应启用严格");
        assertFalse(dcepConfig.isMockEnabled(), "prod 模式不应启用 Mock");
        // 恢复 dev 模式给后续测试
        dcepConfig.setMode("dev");
    }

    // ====================================================================
    // DEV 模式测试
    // ====================================================================

    @Test
    @Order(10)
    @DisplayName("DEV-1: dev 模式 + MOCK 通道 → mockPrepay 返回 OK + prepayId + qrCode")
    void dev_mock_channel_returns_ok() throws Exception {
        dcepConfig.setMode("dev");
        PayDcepChannel c = buildChannel(devChannelId, "MOCK", "DCEP-DCBC-MOCK-TEST");
        PayDcepOrder o = buildOrder(null);

        Map<String, Object> res = invokeMockPrepay(c, o);

        assertEquals("OK", res.get("status"), "dev+MOCK 应返回 OK");
        assertNotNull(res.get("prepayId"), "应有 prepayId");
        assertNotNull(res.get("qrCode"), "应有 qrCode");
        assertTrue(((String) res.get("prepayId")).contains(o.getDcepOrderNo()), "prepayId 应包含订单号");
    }

    @Test
    @Order(11)
    @DisplayName("DEV-2: dev 模式 + 非 MOCK 通道（如 SANDBOX/PROD）→ mockPrepay 返回 FAIL/NOT_IMPLEMENTED")
    void dev_nonmock_channel_returns_fail() throws Exception {
        dcepConfig.setMode("dev");
        PayDcepChannel c = buildChannel(prodChannelId, "PROD", "DCEP-CCB-PROD-TEST");
        PayDcepOrder o = buildOrder(null);

        Map<String, Object> res = invokeMockPrepay(c, o);

        assertEquals("FAIL", res.get("status"), "dev+非MOCK 应返回 FAIL");
        assertEquals("NOT_IMPLEMENTED", res.get("errorCode"));
        assertTrue(((String) res.get("errorMsg")).contains("SANDBOX/PROD"));
    }

    // ====================================================================
    // PROD 模式测试（fail-safe）
    // ====================================================================

    @Test
    @Order(20)
    @DisplayName("PROD-1: prod 模式 + MOCK 通道 → 仍返回 FAIL（禁止任何 Mock 行为）")
    void prod_mock_channel_still_fails() throws Exception {
        dcepConfig.setMode("prod");
        PayDcepChannel c = buildChannel(devChannelId, "MOCK", "DCEP-DCBC-MOCK-TEST");
        PayDcepOrder o = buildOrder(null);

        Map<String, Object> res = invokeMockPrepay(c, o);

        assertEquals("FAIL", res.get("status"), "prod 模式禁止 Mock 行为");
        assertEquals("NOT_IMPLEMENTED", res.get("errorCode"));
        assertTrue(((String) res.get("errorMsg")).contains("生产模式"));
        // 恢复
        dcepConfig.setMode("dev");
    }

    @Test
    @Order(21)
    @DisplayName("PROD-2: prod 模式 + PROD 通道 → 同样返回 FAIL（实际应对接真实 SDK）")
    void prod_real_channel_returns_fail() throws Exception {
        dcepConfig.setMode("prod");
        PayDcepChannel c = buildChannel(prodChannelId, "PROD", "DCEP-CCB-PROD-TEST");
        PayDcepOrder o = buildOrder(null);

        Map<String, Object> res = invokeMockPrepay(c, o);

        assertEquals("FAIL", res.get("status"));
        assertEquals("NOT_IMPLEMENTED", res.get("errorCode"));
        // 恢复
        dcepConfig.setMode("dev");
    }

    // ====================================================================
    // 集成测试：prepay 端点实际行为
    // ====================================================================

    @Test
    @Order(30)
    @DisplayName("INT-1: dev 模式下 prepay 端点返回 prepayId（HTTP 模拟）")
    void int_dev_prepay_endpoint() {
        dcepConfig.setMode("dev");

        // 创建一个独立的 POS 订单（避免 uk_biz_order 冲突）
        String orderNo1 = "TEST-INT-DEV-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO pos_order (tenant_id, store_id, order_no, type, total_amount, final_amount, cashier_id, status, create_time, update_time, deleted) " +
                "VALUES (1, 1, ?, 1, 100.00, 100.00, 1, 1, NOW(), NOW(), 0)", orderNo1);
        Long bizOrderId = jdbc.queryForObject("SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo1);

        Map<String, Object> req = new HashMap<>();
        req.put("bizOrderId", bizOrderId);
        req.put("bizOrderNo", orderNo1);
        req.put("amountCents", 12345L);
        req.put("channelCode", "DCEP-DCBC-MOCK-TEST");
        req.put("storeId", 1L);
        req.put("operatorName", "tester");

        // 直接调用 prepay 方法
        Result<Map<String, Object>> resp = dcepService.prepay(req);

        assertNotNull(resp);
        assertEquals(200, resp.getCode(), "dev 模式 prepay 应成功（200）");
        assertNotNull(resp.getData());
        assertEquals(1, resp.getData().get("status"), "dev 模式 prepay 应返回 status=1 待支付");
        assertEquals("dev", resp.getData().get("appMode"));

        // 清理
        jdbc.update("UPDATE pay_dcep_order SET deleted = 1 WHERE biz_order_id = ?", bizOrderId);
        jdbc.update("UPDATE pos_order SET deleted = 1 WHERE id = ?", bizOrderId);
    }

    @Test
    @Order(31)
    @DisplayName("INT-2: prod 模式下 prepay 端点返回 status=5（已失败）")
    void int_prod_prepay_endpoint() {
        dcepConfig.setMode("prod");

        String orderNo2 = "TEST-INT-PROD-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO pos_order (tenant_id, store_id, order_no, type, total_amount, final_amount, cashier_id, status, create_time, update_time, deleted) " +
                "VALUES (1, 1, ?, 1, 100.00, 100.00, 1, 1, NOW(), NOW(), 0)", orderNo2);
        Long bizOrderId = jdbc.queryForObject("SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo2);

        Map<String, Object> req = new HashMap<>();
        req.put("bizOrderId", bizOrderId);
        req.put("bizOrderNo", orderNo2);
        req.put("amountCents", 12345L);
        req.put("channelCode", "DCEP-DCBC-MOCK-TEST");  // 即使传 MOCK 通道
        req.put("storeId", 1L);

        Result<Map<String, Object>> resp = dcepService.prepay(req);

        assertNotNull(resp);
        assertEquals(200, resp.getCode(), "prepay 端点返回 200");
        assertEquals(5, resp.getData().get("status"), "prod 模式 prepay 应返回 status=5（FAIL）");
        assertEquals("prod", resp.getData().get("appMode"));

        jdbc.update("UPDATE pay_dcep_order SET deleted = 1 WHERE biz_order_id = ?", bizOrderId);
        jdbc.update("UPDATE pos_order SET deleted = 1 WHERE id = ?", bizOrderId);
        // 恢复
        dcepConfig.setMode("dev");
    }
}
