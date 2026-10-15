package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * DCEP-03/04 阶段 B：运营机构客户端工厂
 *
 * <p>根据 {@link Operator#getProtocol()} 选择具体的客户端实现。
 *
 * <h3>支持的协议</h3>
 * <ul>
 *   <li>{@code REST}：{@link HttpOperatorClient}（JDK HttpClient，免依赖）</li>
 *   <li>{@code NETTY_LONG}：当前为占位（真实对接需要 Netty 长连接 SDK）</li>
 * </ul>
 */
@Slf4j
@Component
public class OperatorClientFactory {

    private final Map<String, OperatorClient> protocolClients = new ConcurrentHashMap<>();

    private final HttpOperatorClient httpClient;

    public OperatorClientFactory(HttpOperatorClient httpClient) {
        this.httpClient = httpClient;
        protocolClients.put(OperatorClient.Protocol.REST.name(), httpClient);
        // Netty 长连接客户端：当前为占位
        protocolClients.put(OperatorClient.Protocol.NETTY_LONG.name(), new PlaceholderNettyClient());
        // 签名 holder 在首次使用时通过 lazy init 注入（由 UniversalOperatorDcepAdapter @PostConstruct 完成）
    }

    /** 真实实现里应通过 Spring 注入 OperatorSignatureService */
    private OperatorSignatureService lookupSignatureService() {
        // 由 Spring 自动注入管理
        return SpringContextHolder.getBean(OperatorSignatureService.class);
    }

    public OperatorClient getClient(Operator operator) {
        String proto = operator.getProtocol() == null ? "REST" : operator.getProtocol();
        OperatorClient client = protocolClients.get(proto);
        if (client == null) {
            throw new IllegalArgumentException("不支持的 protocol: " + proto);
        }
        log.debug("[DCEP] operator={} protocol={} 选中客户端：{}",
                operator.getMerchantId(), proto, client.getClass().getSimpleName());
        return client;
    }

    public List<String> supportedProtocols() {
        return List.copyOf(protocolClients.keySet());
    }

    // ============================================================
    // Netty 长连接占位（待真实 SDK 接入）
    // ============================================================

    /**
     * Netty 长连接占位实现（返回 NETWORK_ERROR，不阻塞其他机构运行）
     */
    static class PlaceholderNettyClient implements OperatorClient {
        @Override
        public OperatorResponse invoke(Operator operator, String endpoint, Map<String, Object> payload) {
            log.warn("[DCEP-NETTY] 长连接客户端尚未实现，请注入真实的 NettyClient（基于 Netty SDK）。"
                    + "当前请求：operator={} endpoint={}", operator.getMerchantId(), endpoint);
            return OperatorResponse.builder()
                    .success(false)
                    .errorCode("NETTY_NOT_IMPLEMENTED")
                    .errorMsg("Netty 长连接客户端尚未实现，等待真实 SDK 接入")
                    .build();
        }

        @Override
        public Protocol protocol() {
            return Protocol.NETTY_LONG;
        }

        @Override
        public boolean isConnected(String operatorKey) {
            return false;
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }

    // ============================================================
    // Spring 上下文占位（让 HttpOperatorClient 能获取签名服务）
    // ============================================================

    static class SpringContextHolder {
        private static final Map<Class<?>, Object> BEANS = new HashMap<>();

        public static void register(Object bean) {
            BEANS.put(bean.getClass(), bean);
            // 父类也注册（便于按接口取）
            for (java.lang.reflect.Type t : bean.getClass().getGenericInterfaces()) {
                if (t instanceof Class<?> c) BEANS.put(c, bean);
            }
        }

        @SuppressWarnings("unchecked")
        public static <T> T getBean(Class<T> type) {
            Object bean = BEANS.get(type);
            if (bean == null) {
                throw new IllegalStateException("Bean 未注册：" + type.getSimpleName()
                        + "（应在 Spring 启动时通过 SmartInitializingSingleton 或 @PostConstruct 注册）");
            }
            return (T) bean;
        }
    }

    /**
     * 由调用方在 Spring 容器启动后注入（避免 HttpOperatorClient 的硬依赖）
     */
    public static class BeanRegistrar {
        public static void registerAll(OperatorSignatureService sig) {
            SpringContextHolder.register(sig);
        }
    }
}
