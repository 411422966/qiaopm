package com.meiyetec.ecommerce;

import com.meiyetec.entity.EcommercePlatform;

import java.util.List;
import java.util.Map;

/**
 * EXT-16：电商平台适配器抽象（与 CrmAdapter 同源设计）
 *
 * <p>屏蔽有赞 / 微店 / 淘宝 / Mock 实现差异。运行时由 Spring 注入指定实现。
 *
 * <p>核心职责：
 * <ol>
 *   <li>OAuth2 Client-Credentials 鉴权（clientId + clientSecret → access_token）</li>
 *   <li>商品拉取（分页）</li>
 *   <li>订单拉取（按时间窗）</li>
 *   <li>健康检查</li>
 * </ol>
 */
public interface EcommerceAdapter {

    /**
     * 平台代码：YOUZAN / WEIDIAN / MOCK
     */
    String platformCode();

    /**
     * OAuth2 Client-Credentials 鉴权，返回 access_token + 过期时间（秒）
     */
    AuthResult authenticate(EcommercePlatform config);

    /**
     * 拉商品（分页）
     */
    ProductPage fetchProducts(EcommercePlatform config, int pageNo, int pageSize);

    /**
     * 拉订单（按时间窗 + 分页）
     *
     * @param startTime 起始更新时间（yyyy-MM-dd HH:mm:ss）
     * @param endTime   截止更新时间
     */
    OrderPage fetchOrders(EcommercePlatform config,
                          String startTime, String endTime,
                          int pageNo, int pageSize);

    /**
     * 健康检查（鉴权 + 一次最简接口调用）
     */
    HealthStatus healthCheck(EcommercePlatform config);

    // ==================== DTO ====================

    /**
     * 鉴权结果
     */
    class AuthResult {
        public boolean success;
        public String accessToken;
        /** 过期秒数 */
        public Long expiresIn;
        public String errorCode;
        public String errorMessage;

        public static AuthResult ok(String token, Long expiresIn) {
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = token;
            r.expiresIn = expiresIn;
            return r;
        }

        public static AuthResult fail(String code, String msg) {
            AuthResult r = new AuthResult();
            r.success = false;
            r.errorCode = code;
            r.errorMessage = msg;
            return r;
        }
    }

    /**
     * 商品分页
     */
    class ProductPage {
        public List<Map<String, Object>> items;
        public int totalCount;
        public boolean hasNext;
    }

    /**
     * 订单分页
     */
    class OrderPage {
        public List<Map<String, Object>> orders;
        public int totalCount;
        public boolean hasNext;
    }

    /**
     * 健康检查
     */
    class HealthStatus {
        public boolean healthy;
        public String stage;        // AUTH_OK / QUERY_OK / NETWORK_ERROR
        public String message;
        public Long elapsedMs;
        public String apiBaseUrl;
    }
}
