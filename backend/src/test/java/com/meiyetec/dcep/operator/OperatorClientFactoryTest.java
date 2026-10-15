package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-03/04 阶段 B：OperatorClientFactory 单元测试
 */
@DisplayName("DCEP-03/04 阶段 B OperatorClientFactory")
class OperatorClientFactoryTest {

    @Test
    @DisplayName("REST 协议 → HttpOperatorClient")
    void restProtocol_returnsHttpClient() {
        HttpOperatorClient http = new HttpOperatorClient();
        OperatorClientFactory factory = new OperatorClientFactory(http);
        Operator op = new Operator();
        op.setProtocol("REST");
        OperatorClient client = factory.getClient(op);
        assertEquals(OperatorClient.Protocol.REST, client.protocol());
        assertTrue(client instanceof HttpOperatorClient);
    }

    @Test
    @DisplayName("NETTY_LONG 协议 → 占位客户端（NETTY_NOT_IMPLEMENTED）")
    void nettyProtocol_returnsPlaceholder() {
        HttpOperatorClient http = new HttpOperatorClient();
        OperatorClientFactory factory = new OperatorClientFactory(http);
        Operator op = new Operator();
        op.setProtocol("NETTY_LONG");
        OperatorClient client = factory.getClient(op);
        assertEquals(OperatorClient.Protocol.NETTY_LONG, client.protocol());

        // 调用占位实现应返回 NOT_IMPLEMENTED
        OperatorResponse resp = client.invoke(op, "/pay/prepay", new HashMap<>());
        assertFalse(resp.isSuccess());
        assertEquals("NETTY_NOT_IMPLEMENTED", resp.getErrorCode());
    }

    @Test
    @DisplayName("protocol 未配置 → 默认为 REST")
    void nullProtocol_defaultsToRest() {
        HttpOperatorClient http = new HttpOperatorClient();
        OperatorClientFactory factory = new OperatorClientFactory(http);
        Operator op = new Operator();
        op.setProtocol(null);
        OperatorClient client = factory.getClient(op);
        assertEquals(OperatorClient.Protocol.REST, client.protocol());
    }

    @Test
    @DisplayName("不支持的协议 → IllegalArgumentException")
    void unsupportedProtocol() {
        HttpOperatorClient http = new HttpOperatorClient();
        OperatorClientFactory factory = new OperatorClientFactory(http);
        Operator op = new Operator();
        op.setProtocol("GRPC");
        assertThrows(IllegalArgumentException.class, () -> factory.getClient(op));
    }

    @Test
    @DisplayName("supportedProtocols 至少含 REST + NETTY_LONG")
    void supportedProtocols() {
        HttpOperatorClient http = new HttpOperatorClient();
        OperatorClientFactory factory = new OperatorClientFactory(http);
        List<String> protos = factory.supportedProtocols();
        assertTrue(protos.contains("REST"));
        assertTrue(protos.contains("NETTY_LONG"));
    }

    @Test
    @DisplayName("占位 NettyClient.isConnected 返回 false / shutdown 不抛异常")
    void nettyPlaceholder_lifecycle() {
        HttpOperatorClient http = new HttpOperatorClient();
        OperatorClientFactory factory = new OperatorClientFactory(http);
        Operator op = new Operator();
        op.setProtocol("NETTY_LONG");
        OperatorClient client = factory.getClient(op);
        assertFalse(client.isConnected("ICBC"));
        assertDoesNotThrow(client::shutdown);
    }

    @Test
    @DisplayName("HttpOperatorClient.isConnected 始终返回 true（短连接）")
    void httpIsConnected() {
        HttpOperatorClient http = new HttpOperatorClient();
        assertTrue(http.isConnected("ICBC"));
        assertDoesNotThrow(http::shutdown);
    }
}
