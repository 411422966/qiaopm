package com.meiyetec.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * IOT-006：HiveMQ MQTT 5 客户端服务
 *
 * <p>负责：
 * <ul>
 *   <li>维护与 MQTT broker 的长连接</li>
 *   <li>下发命令到设备 Topic（QoS 1，最少一次）</li>
 *   <li>订阅设备响应（QoS 1）</li>
 * </ul>
 *
 * <p>激活方式：{@code iot.mqtt.enabled=true} 且 {@code iot.mqtt.brokerUri} 配置。
 *
 * <p>MQTT Topic 命名约定：
 * <pre>
 *   下行（服务器 → 设备）：devices/{tenantId}/{deviceCode}/cmd
 *   上行（设备 → 服务器）：devices/{tenantId}/{deviceCode}/ack
 *   状态：                devices/{tenantId}/{deviceCode}/status
 * </pre>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "iot.mqtt", name = "enabled", havingValue = "true")
public class MqttClientService {

    @Autowired(required = false)
    private MqttProperties properties;

    private Mqtt5AsyncClient client;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 初始化客户端（在第一次调用时按需连接）
     */
    private synchronized Mqtt5AsyncClient ensureClient() {
        if (client != null) return client;
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException("MQTT 未启用：缺少 iot.mqtt.brokerUri 配置");
        }
        com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder builder =
                com.hivemq.client.mqtt.mqtt5.Mqtt5Client.builder()
                .identifier(properties.getClientId() != null ? properties.getClientId()
                        : "backend-" + UUID.randomUUID().toString().substring(0, 8))
                .serverHost(properties.getHost())
                .serverPort(properties.getPort())
                .automaticReconnectWithDefaultConfig();
        if (properties.getUsername() != null && !properties.getUsername().isEmpty()) {
            builder.simpleAuth()
                    .username(properties.getUsername())
                    .password(properties.getPassword().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
        client = builder.buildAsync();
        try {
            client.connect().get(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("MQTT 连接被中断", e);
        } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
            throw new RuntimeException("MQTT 连接失败：" + e.getMessage(), e);
        }
        log.info("[IOT-006] MQTT 客户端已连接：broker={}:{} clientId={}",
                properties.getHost(), properties.getPort(), properties.getClientId());
        // 连接成功时，恢复已注册的订阅
        if (!listeners.isEmpty()) {
            subscribe0(properties.getInboundTopicPattern());
            if (properties.getStatusTopicPattern() != null
                    && !properties.getStatusTopicPattern().isEmpty()) {
                subscribe0(properties.getStatusTopicPattern());
            }
        }
        return client;
    }

    /**
     * 下发命令到设备
     *
     * @param device 目标设备（必须含 mqttTopic）
     * @param command 命令内容
     * @return 下发结果
     */
    public DeviceCommandResult publish(com.meiyetec.entity.IotDevice device, DeviceCommand command) {
        long start = System.currentTimeMillis();
        if (device == null || device.getMqttTopic() == null || device.getMqttTopic().isEmpty()) {
            return DeviceCommandResult.fail("DEVICE_NO_TOPIC", "设备未配置 MQTT Topic");
        }
        try {
            String payload = json.writeValueAsString(command);
            Mqtt5AsyncClient c = ensureClient();
            String topic = buildDownlinkTopic(device, command.getCommandType());
            Mqtt5Publish pub = Mqtt5Publish.builder()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .retain(false)
                    .build();
            CompletableFuture<Mqtt5PublishResult> future = c.publish(pub);
            Mqtt5PublishResult result = future.get(
                    command.getTimeoutSeconds() != null
                            ? command.getTimeoutSeconds() * 1000L
                            : 5000L,
                    TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - start;
            log.info("[IOT-006] MQTT 命令已下发：device={} topic={} cmd={} ackCode={}",
                    device.getDeviceCode(), topic, command.getCommandType(),
                    result.getError() == null ? "OK" : result.getError());
            return DeviceCommandResult.ok(
                    "mqtt-" + UUID.randomUUID().toString().substring(0, 8),
                    topic, null, duration);
        } catch (TimeoutException e) {
            return DeviceCommandResult.fail("MQTT_TIMEOUT", "下发超时：" + e.getMessage());
        } catch (Exception e) {
            return DeviceCommandResult.fail("MQTT_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构建下行 Topic：devices/{tenantId}/{deviceCode}/cmd/{commandType}
     */
    private String buildDownlinkTopic(com.meiyetec.entity.IotDevice device, String cmdType) {
        String base = device.getMqttTopic();
        String t = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return t + "/cmd/" + (cmdType == null ? "raw" : cmdType);
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            try {
                client.disconnect().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[IOT-006] MQTT 客户端关闭异常：{}", e.getMessage());
            }
            client = null;
        }
    }

    /**
     * MQTT 配置
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "iot.mqtt")
    public static class MqttProperties {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 1883;
        private String clientId;
        private String username;
        private String password;
        private int connectTimeoutMs = 5000;
        private int keepAliveSeconds = 30;
        /** 上行 Topic 通配符（设备 → 服务器），默认订阅所有设备的上报 */
        private String inboundTopicPattern = "devices/+/+/ack";
        /** 状态 Topic 通配符 */
        private String statusTopicPattern = "devices/+/+/status";
    }

    // ==================== 上行订阅 ====================
    // 设备上报消费者（支持多消费者注册）
    private final CopyOnWriteArrayList<MqttMessageListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 设备上报消息监听器
     */
    @FunctionalInterface
    public interface MqttMessageListener {
        void onMessage(String topic, String payload);
    }

    /**
     * 注册设备上行消息消费者。
     * <p>首次调用时自动连接并订阅设备上行 Topic。
     *
     * @param listener  消息处理器
     * @return 注销句柄（调用以取消订阅）
     */
    public synchronized Runnable subscribe(MqttMessageListener listener) {
        listeners.add(listener);
        // 已连接 → 立即触发订阅
        if (client != null && client.getState().isConnected()) {
            subscribe0(getProperties().getInboundTopicPattern());
        }
        return () -> {
            listeners.remove(listener);
            log.info("[IOT-006] 取消订阅 MQTT 上行消息");
        };
    }

    /**
     * 内部订阅入口（按 Topic 通配符订阅）
     */
    private void subscribe0(String topicPattern) {
        Mqtt5Subscription sub = Mqtt5Subscription.builder()
                .topicFilter(topicPattern)
                .qos(MqttQos.AT_LEAST_ONCE)
                .build();
        Mqtt5Subscribe subscribe = Mqtt5Subscribe.builder()
                .addSubscription(sub)
                .build();
        client.subscribe(subscribe, (publish) -> {
            String topic = publish.getTopic().toString();
            String payload = new String(publish.getPayload()
                    .orElseThrow(() -> new IllegalStateException("mqtt payload buffer empty")).asReadOnlyBuffer().array(),
                    StandardCharsets.UTF_8);
            // 分发给所有注册的消费者
            for (MqttMessageListener l : listeners) {
                try {
                    l.onMessage(topic, payload);
                } catch (Exception e) {
                    log.error("[IOT-006] MQTT 消息处理异常：topic={} err={}", topic, e.getMessage());
                }
            }
        });
        log.info("[IOT-006] 已订阅 MQTT Topic：{}", topicPattern);
    }

    private MqttProperties getProperties() {
        return properties;
    }
}
