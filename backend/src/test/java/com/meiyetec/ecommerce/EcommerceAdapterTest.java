package com.meiyetec.ecommerce;

import com.meiyetec.entity.EcommercePlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXT-16：电商适配器单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>YouzanEcommerceAdapter URL / body 构造（含中文地址 URL 编码）</li>
 *   <li>MockEcommerceAdapter 全流程：auth / fetchProducts / fetchOrders / healthCheck</li>
 *   <li>EcommerceAdapterRouter 路由（含 fallback MOCK）</li>
 *   <li>EcommerceAdapter DTO 工厂方法</li>
 * </ul>
 *
 * <p>真实 HTTP 调用不在单测覆盖范围（需 mock server / 联调环境）。
 */
@DisplayName("EXT-16 Ecommerce Adapter")
class EcommerceAdapterTest {

    // ==================== Youzan URL / body 构造 ====================

    @Test
    @DisplayName("Youzan: OAuth2 token 请求 body 合法（含 client_id/client_secret/grant_type）")
    void youzanTokenBody() {
        String body = YouzanEcommerceAdapter.buildTokenRequestBody("client_xyz", "secret_abc");
        assertTrue(body.contains("client_id=client_xyz"));
        assertTrue(body.contains("client_secret=secret_abc"));
        assertTrue(body.contains("grant_type=client_credential"));
    }

    @Test
    @DisplayName("Youzan: items/list URL 合法（含 kdtapi 入口 + access_token）")
    void youzanItemsUrl() {
        String url = YouzanEcommerceAdapter.buildItemsListUrl(
                "https://openapi.youzan.com", "tk-123", 1, 50);
        assertTrue(url.startsWith("https://openapi.youzan.com/api/entry/kdtapi/v1.0/items/list"));
        assertTrue(url.contains("page_no=1"));
        assertTrue(url.contains("page_size=50"));
        assertTrue(url.contains("access_token=tk-123"));
    }

    @Test
    @DisplayName("Youzan: trades/sold_get URL 含时间窗 + 分页 + token")
    void youzanTradesUrl() {
        String url = YouzanEcommerceAdapter.buildTradesSoldGetUrl(
                "https://openapi.youzan.com", "tk-xyz",
                "2026-09-01 00:00:00", "2026-09-05 00:00:00", 2, 30);
        assertTrue(url.startsWith("https://openapi.youzan.com/api/entry/kdtapi/v1.0/trades/sold_get"));
        assertTrue(url.contains("start_update_time=2026-09-01+00%3A00%3A00"), "空格应编码为 +");
        assertTrue(url.contains("end_update_time=2026-09-05+00%3A00%3A00"));
        assertTrue(url.contains("page_no=2"));
        assertTrue(url.contains("page_size=30"));
        assertTrue(url.contains("access_token=tk-xyz"));
    }

    @Test
    @DisplayName("Youzan: pageSize 超过 100 时被截断为 100")
    void youzanPageSizeClamped() {
        String url = YouzanEcommerceAdapter.buildItemsListUrl("https://x", "tk", 1, 999);
        assertTrue(url.contains("page_size=100"), "page_size 应被 clamp 到 100");
    }

    // ==================== Mock 网关全流程 ====================

    @Test
    @DisplayName("Mock 网关：auth 成功 + token 不为空 + expiresIn=7200")
    void mockAuth() {
        MockEcommerceAdapter m = new MockEcommerceAdapter();
        EcommerceAdapter.AuthResult r = m.authenticate(new EcommercePlatform());
        assertTrue(r.success);
        assertNotNull(r.accessToken);
        assertTrue(r.accessToken.startsWith("MOCK-TOKEN-"));
        assertEquals(7200L, r.expiresIn);
        assertEquals("MOCK", m.platformCode());
    }

    @Test
    @DisplayName("Mock 网关：fetchProducts 返回 3 条样本")
    void mockFetchProducts() {
        MockEcommerceAdapter m = new MockEcommerceAdapter();
        EcommerceAdapter.ProductPage page = m.fetchProducts(new EcommercePlatform(), 1, 50);
        assertEquals(3, page.items.size());
        assertEquals(3, page.totalCount);
        assertFalse(page.hasNext);
        assertNotNull(page.items.get(0).get("title"));
    }

    @Test
    @DisplayName("Mock 网关：fetchOrders 返回 2 条样本 + 时间窗被忽略")
    void mockFetchOrders() {
        MockEcommerceAdapter m = new MockEcommerceAdapter();
        EcommerceAdapter.OrderPage page = m.fetchOrders(new EcommercePlatform(),
                "2026-09-01 00:00:00", "2026-09-05 00:00:00", 1, 50);
        assertEquals(2, page.orders.size());
        assertEquals(2, page.totalCount);
        assertNotNull(page.orders.get(0).get("tid"));
    }

    @Test
    @DisplayName("Mock 网关：healthCheck healthy=true")
    void mockHealth() {
        MockEcommerceAdapter m = new MockEcommerceAdapter();
        EcommerceAdapter.HealthStatus s = m.healthCheck(new EcommercePlatform());
        assertTrue(s.healthy);
        assertEquals("MOCK_OK", s.stage);
    }

    // ==================== Router ====================

    @Test
    @DisplayName("Router：根据 platformCode 路由到正确实现 + 未知平台 fallback MOCK")
    void routerRouting() {
        EcommerceAdapterRouter router = new EcommerceAdapterRouter(List.of(
                new YouzanEcommerceAdapter(), new MockEcommerceAdapter()));
        assertEquals("YOUZAN", router.getAdapter("YOUZAN").platformCode());
        assertEquals("YOUZAN", router.getAdapter("youzan").platformCode());
        assertEquals("MOCK", router.getAdapter("MOCK").platformCode());
        // 未知平台 → fallback MOCK
        assertEquals("MOCK", router.getAdapter("WEIDIAN").platformCode());
        assertTrue(router.hasAdapter("YOUZAN"));
        assertFalse(router.hasAdapter("NOPE"));
        assertEquals(2, router.listPlatforms().size());
    }

    // ==================== DTO 工厂 ====================

    @Test
    @DisplayName("AuthResult.ok() / fail() 工厂")
    void authResultFactories() {
        EcommerceAdapter.AuthResult ok = EcommerceAdapter.AuthResult.ok("tk", 3600L);
        assertTrue(ok.success);
        assertEquals("tk", ok.accessToken);
        assertEquals(3600L, ok.expiresIn);

        EcommerceAdapter.AuthResult fail = EcommerceAdapter.AuthResult.fail("E001", "no key");
        assertFalse(fail.success);
        assertEquals("E001", fail.errorCode);
    }
}
