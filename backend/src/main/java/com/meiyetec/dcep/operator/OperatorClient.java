package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;

import java.util.Map;

/**
 * DCEP-03/04 阶段 B：运营机构客户端抽象
 *
 * <p>支持两种协议：
 * <ul>
 *   <li>{@link Protocol#REST}：HTTP/HTTPS + JSON（ICBC 开放平台）</li>
 *   <li>{@link Protocol#NETTY_LONG}：TCP 长连接 + 自定义二进制协议（CCB 对公网银 V14.1）</li>
 * </ul>
 *
 * <p>每个 Operator 一个实例，由 {@link OperatorClientFactory} 工厂创建。
 */
public interface OperatorClient {

    enum Protocol { REST, NETTY_LONG }

    /**
     * 发送请求到运营机构
     *
     * @param operator 运营机构配置
     * @param endpoint 端点（如 /api/pay/prepay）
     * @param payload  业务字段（key → String 值）
     * @return 响应字段（key → Object 值，含 String / Long / Boolean / List）
     */
    OperatorResponse invoke(Operator operator, String endpoint, Map<String, Object> payload);

    /** 协议标识 */
    Protocol protocol();

    /** 连接是否就绪（Netty 长连接场景需要心跳检查） */
    boolean isConnected(String operatorKey);

    /** 关闭连接（Spring 容器关闭时调用） */
    void shutdown();
}
