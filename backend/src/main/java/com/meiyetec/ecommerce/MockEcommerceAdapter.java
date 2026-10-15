package com.meiyetec.ecommerce;

import com.meiyetec.entity.EcommercePlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * EXT-16：Mock 电商适配器（dev fallback，无凭据时启用）
 *
 * <p>本 Adapter 始终注册（无条件 {@code @Component}），
 * 按 {@code platform=MOCK} 路由时使用，供本地开发 + 联调，
 * 避免无真实凭据导致排程阻塞。
 *
 * <p>历史：曾标注 {@code @ConditionalOnMissingBean(name="youzanEcommerceAdapter")}，
 * 但 YouzanEcommerceAdapter 无条件 {@code @Component}，
 * 导致 Mock 永不注册、{@code EcommerceAdapterRouter.getAdapter("MOCK")} 返回 null → NPE（详见 I087）。
 * 该注解已于 commit 298f320（2026-09-23）移除。
 */
@Slf4j
@Component
public class MockEcommerceAdapter implements EcommerceAdapter {

    private static final List<Map<String, Object>> SAMPLE_PRODUCTS = List.of(
            Map.of("item_id", 1001, "title", "焕颜保湿面膜", "price", "128.00", "quantity", 50, "status", "ON"),
            Map.of("item_id", 1002, "title", "头皮舒缓精华", "price", "288.00", "quantity", 30, "status", "ON"),
            Map.of("item_id", 1003, "title", "经典美甲护理", "price", "168.00", "quantity", 80, "status", "ON")
    );

    private static final List<Map<String, Object>> SAMPLE_ORDERS = List.of(
            Map.of("tid", "E20260905001", "status", "WAIT_SELLER_SEND_GOODS",
                    "total_amount", "128.00", "update_time", "2026-09-05 10:30:00"),
            Map.of("tid", "E20260905002", "status", "TRADE_SUCCESS",
                    "total_amount", "456.00", "update_time", "2026-09-05 12:15:00")
    );

    @Override
    public String platformCode() {
        return "MOCK";
    }

    @Override
    public AuthResult authenticate(EcommercePlatform config) {
        sleep(50);
        return AuthResult.ok("MOCK-TOKEN-" + UUID.randomUUID(), 7200L);
    }

    @Override
    public ProductPage fetchProducts(EcommercePlatform config, int pageNo, int pageSize) {
        sleep(80);
        ProductPage page = new ProductPage();
        page.items = new ArrayList<>(SAMPLE_PRODUCTS);
        page.totalCount = SAMPLE_PRODUCTS.size();
        page.hasNext = false;
        return page;
    }

    @Override
    public OrderPage fetchOrders(EcommercePlatform config,
                                 String startTime, String endTime,
                                 int pageNo, int pageSize) {
        sleep(80);
        OrderPage page = new OrderPage();
        page.orders = new ArrayList<>(SAMPLE_ORDERS);
        page.totalCount = SAMPLE_ORDERS.size();
        page.hasNext = false;
        return page;
    }

    @Override
    public HealthStatus healthCheck(EcommercePlatform config) {
        HealthStatus s = new HealthStatus();
        s.healthy = true;
        s.stage = "MOCK_OK";
        s.message = "Mock 电商链路 OK";
        s.apiBaseUrl = "https://mock-ecommerce.local";
        s.elapsedMs = 50L;
        return s;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
