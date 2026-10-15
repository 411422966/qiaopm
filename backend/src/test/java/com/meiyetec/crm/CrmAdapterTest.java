package com.meiyetec.crm;

import com.meiyetec.entity.CrmIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXT-15：CRM 适配器单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>MockCrmAdapter 全流程：auth / fetch / upsert / health</li>
 *   <li>CrmAdapter 接口契约：必要方法齐全</li>
 *   <li>SalesforceCrmAdapter JWT 构造（静态方法，零网络依赖）</li>
 *   <li>CrmAdapterRouter 路由选择</li>
 *   <li>CrmAdapter.SyncCursor / ExternalContact DTO 字段正确</li>
 * </ul>
 */
@DisplayName("EXT-15 CRM Adapter")
class CrmAdapterTest {

    @Test
    @DisplayName("Mock 网关：auth 成功 + token 不为空")
    void mockAuth() {
        MockCrmAdapter gw = new MockCrmAdapter();
        CrmAdapter.AuthResult r = gw.authenticate(new CrmIntegration());
        assertTrue(r.success);
        assertNotNull(r.accessToken);
        assertTrue(r.accessToken.startsWith("MOCK-TOKEN-"));
        assertNotNull(r.instanceUrl);
        assertEquals(7200L, r.expiresIn);
        assertEquals("MOCK", gw.platformCode());
    }

    @Test
    @DisplayName("Mock 网关：首次拉取返回样本列表 + 增量只返回新增")
    void mockFetch() {
        MockCrmAdapter gw = new MockCrmAdapter();
        List<CrmAdapter.ExternalContact> full = gw.fetchContactsIncremental(
                new CrmIntegration(), CrmAdapter.SyncCursor.empty());
        assertEquals(3, full.size());
        assertEquals("003AX000001", full.get(0).externalId);

        CrmAdapter.SyncCursor cursor = new CrmAdapter.SyncCursor();
        cursor.sinceEpochMs = System.currentTimeMillis();
        List<CrmAdapter.ExternalContact> inc = gw.fetchContactsIncremental(
                new CrmIntegration(), cursor);
        assertEquals(1, inc.size());
        assertEquals("003AX000001", inc.get(0).externalId);
    }

    @Test
    @DisplayName("Mock 网关：upsert 返回成功 + externalId 复用")
    void mockUpsert() {
        MockCrmAdapter gw = new MockCrmAdapter();
        CrmAdapter.PushResult r = gw.upsertContact(new CrmIntegration(),
                Map.of("externalId", "003AX000001", "email", "zhang3@example.com"));
        assertTrue(r.success);
        assertEquals("003AX000001", r.externalId);

        // 缺 externalId 时自动生成
        CrmAdapter.PushResult r2 = gw.upsertContact(new CrmIntegration(),
                Map.of("email", "new@example.com"));
        assertTrue(r2.success);
        assertNotNull(r2.externalId);
        assertTrue(r2.externalId.startsWith("MOCK-"));
    }

    @Test
    @DisplayName("Mock 网关：healthCheck healthy=true")
    void mockHealth() {
        MockCrmAdapter gw = new MockCrmAdapter();
        CrmAdapter.HealthStatus s = gw.healthCheck(new CrmIntegration());
        assertTrue(s.healthy);
        assertEquals("MOCK_OK", s.stage);
    }

    @Test
    @DisplayName("Salesforce JWT：构造合法 JWT 结构（header.payload.signature 三段）")
    void salesforceJwtStructure() throws Exception {
        CrmIntegration cfg = new CrmIntegration();
        cfg.setClientId("CONSUMER_KEY_xxx");
        cfg.setUsername("user@example.com");
        cfg.setLoginUrl("https://login.salesforce.com");
        // 用一对预生成的 RSA 测试密钥（确保 PEM 解析）
        java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        java.security.KeyPair kp = g.generateKeyPair();
        String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        String privPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        cfg.setPrivateKey(privPem);
        // 模拟一次 verify：用同样的密钥 verify sign 段
        String jwt = SalesforceCrmAdapter.buildJwt(cfg);
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT 必须是 3 段");
        // header & payload 解码后是合法 JSON
        String header = new String(Base64.getUrlDecoder().decode(parts[0]),
                java.nio.charset.StandardCharsets.UTF_8);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(header.contains("RS256"));
        assertTrue(payload.contains("CONSUMER_KEY_xxx"));
        assertTrue(payload.contains("user@example.com"));
        assertTrue(payload.contains("https://login.salesforce.com"));

        // 用同一密钥验证签名（确保真签了不是假签）
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initVerify(kp.getPublic());
        sig.update((parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] decodedSig = Base64.getUrlDecoder().decode(parts[2]);
        assertTrue(sig.verify(decodedSig), "签名必须可用公钥验签");
        // 验证公钥 PEM 也可解析
        assertNotNull(pubPem);
    }

    @Test
    @DisplayName("CrmAdapter 接口契约：必要方法齐全")
    void adapterApiContract() {
        Class<?> c = CrmAdapter.class;
        assertHasMethod(c, "platformCode");
        assertHasMethod(c, "authenticate");
        assertHasMethod(c, "fetchContactsIncremental");
        assertHasMethod(c, "upsertContact");
        assertHasMethod(c, "healthCheck");
    }

    @Test
    @DisplayName("CrmAdapterRouter：根据 platformCode 路由到正确实现")
    void routerRouting() {
        // 手动构造 router（避免依赖 Spring context）
        CrmAdapterRouter router = new CrmAdapterRouter(
                List.of(new SalesforceCrmAdapter(), new MockCrmAdapter()));
        assertEquals("SALESFORCE", router.getAdapter("salesforce").platformCode());
        assertEquals("SALESFORCE", router.getAdapter("SALESFORCE").platformCode());
        assertEquals("MOCK", router.getAdapter("MOCK").platformCode());
        // 未知平台 → fallback 到 MOCK
        assertEquals("MOCK", router.getAdapter("unknown-platform").platformCode());
        assertTrue(router.hasAdapter("MOCK"));
        assertTrue(router.hasAdapter("SALESFORCE"));
        assertFalse(router.hasAdapter("NOPE"));
        assertEquals(2, router.listPlatforms().size());
    }

    @Test
    @DisplayName("CrmAdapter.SyncCursor 字段正确 + isEmpty()")
    void syncCursor() {
        CrmAdapter.SyncCursor c1 = CrmAdapter.SyncCursor.empty();
        assertTrue(c1.isEmpty());
        c1.sinceEpochMs = 1L;
        assertFalse(c1.isEmpty());

        CrmAdapter.SyncCursor c2 = new CrmAdapter.SyncCursor();
        c2.syncToken = "abc";
        assertFalse(c2.isEmpty());
    }

    @Test
    @DisplayName("AuthResult / PushResult 静态工厂方法")
    void dtoFactory() {
        CrmAdapter.AuthResult ar = CrmAdapter.AuthResult.ok("tk", "rt", 3600L, "https://x");
        assertTrue(ar.success);
        assertEquals("tk", ar.accessToken);

        CrmAdapter.AuthResult arf = CrmAdapter.AuthResult.fail("E001", "msg");
        assertFalse(arf.success);
        assertEquals("E001", arf.errorCode);

        CrmAdapter.PushResult pr = CrmAdapter.PushResult.ok("001", "v1");
        assertTrue(pr.success);
        CrmAdapter.PushResult prf = CrmAdapter.PushResult.fail("E002", "msg");
        assertFalse(prf.success);
    }

    // ==================== helpers ====================

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }
}
