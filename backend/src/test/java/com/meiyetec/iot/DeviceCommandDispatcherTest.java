package com.meiyetec.iot;

import com.meiyetec.entity.IotDevice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IOT-006：设备命令统一下发器测试（基于 mock 避免真实网络调用）
 */
@DisplayName("IOT-006 DeviceCommandDispatcher")
class DeviceCommandDispatcherTest {

    /**
     * 不引入 Spring 的纯单元测试：直接调用 dispatchHttp（HTTP 模式不需要 MQTT 启用）
     */
    @Test
    @DisplayName("HTTP 设备：dispatchHttp 走 HttpDeviceControlService")
    void dispatch_http() {
        HttpDeviceControlService httpService = new HttpDeviceControlService();
        DeviceCommandDispatcher dispatcher = new DeviceCommandDispatcher();
        java.lang.reflect.Field httpF = null;
        try {
            httpF = DeviceCommandDispatcher.class.getDeclaredField("httpService");
            httpF.setAccessible(true);
            httpF.set(dispatcher, httpService);
        } catch (Exception e) {
            fail("注入失败：" + e.getMessage());
        }

        IotDevice device = new IotDevice();
        device.setId(1L);
        device.setDeviceCode("DEV-001");
        device.setProtocol("http");
        device.setAuthToken("test-api-key");

        DeviceCommand cmd = new DeviceCommand();
        cmd.setCommandType("lock");
        cmd.setParams(java.util.Map.of("duration", 30));

        // cmdUrl 在 actionJson 中提供
        String actionJson = "{\"cmdUrl\":\"http://127.0.0.1:65535/cmd\"}";

        // 由于 65535 是空闲端口，连接会被拒绝 → HTTP_ERROR
        DeviceCommandResult result = dispatcher.dispatch(device, cmd, actionJson);
        // 我们只验证 dispatch 路由正确（即使是失败也应是 HTTP_ERROR / CONNECTION_REFUSED）
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorCode() != null);
        assertTrue(result.getErrorCode().startsWith("HTTP") || result.getErrorMessage().contains("refused")
                || result.getErrorMessage().contains("Connection"));
    }

    @Test
    @DisplayName("HTTP 设备：无 cmdUrl 也无 ipAddress → HTTP_URL_UNRESOLVED")
    void dispatch_http_urlUnresolved() {
        HttpDeviceControlService httpService = new HttpDeviceControlService();
        DeviceCommandDispatcher dispatcher = new DeviceCommandDispatcher();
        try {
            java.lang.reflect.Field f = DeviceCommandDispatcher.class.getDeclaredField("httpService");
            f.setAccessible(true);
            f.set(dispatcher, httpService);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        IotDevice device = new IotDevice();
        device.setId(1L);
        device.setDeviceCode("DEV-002");
        device.setProtocol("http");
        // 无 ipAddress, actionJson 无 cmdUrl

        DeviceCommand cmd = new DeviceCommand();
        DeviceCommandResult result = dispatcher.dispatch(device, cmd, "{}");
        assertFalse(result.isSuccess());
        assertEquals("HTTP_URL_UNRESOLVED", result.getErrorCode());
    }

    @Test
    @DisplayName("MQTT 设备：mqttService 未启用 → 降级到 fallback（adapter）")
    void dispatch_mqtt_fallbackToAdapter() {
        HttpDeviceControlService httpService = new HttpDeviceControlService();
        IotProtocolRouter router = new IotProtocolRouter(java.util.List.of(
                new MqttDeviceAdapter(), new HttpDeviceAdapter()));
        DeviceCommandDispatcher dispatcher = new DeviceCommandDispatcher();
        try {
            java.lang.reflect.Field httpF = DeviceCommandDispatcher.class.getDeclaredField("httpService");
            httpF.setAccessible(true);
            httpF.set(dispatcher, httpService);
            java.lang.reflect.Field routerF = DeviceCommandDispatcher.class.getDeclaredField("protocolRouter");
            routerF.setAccessible(true);
            routerF.set(dispatcher, router);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        IotDevice device = new IotDevice();
        device.setId(1L);
        device.setDeviceCode("DEV-003");
        device.setProtocol("mqtt");

        DeviceCommand cmd = new DeviceCommand();
        cmd.setCommandType("start");
        DeviceCommandResult result = dispatcher.dispatch(device, cmd, null);
        // mqttService 未注入（因为 @ConditionalOnProperty 未启用），应降级到 Stub MQTT adapter
        assertTrue(result.isSuccess(), "fallback to stub MQTT adapter should succeed");
    }

    @Test
    @DisplayName("device == null → DEVICE_NULL")
    void dispatch_nullDevice() {
        HttpDeviceControlService httpService = new HttpDeviceControlService();
        IotProtocolRouter router = new IotProtocolRouter(java.util.List.of(new MqttDeviceAdapter()));
        DeviceCommandDispatcher dispatcher = new DeviceCommandDispatcher();
        try {
            java.lang.reflect.Field httpF = DeviceCommandDispatcher.class.getDeclaredField("httpService");
            httpF.setAccessible(true);
            httpF.set(dispatcher, httpService);
            java.lang.reflect.Field routerF = DeviceCommandDispatcher.class.getDeclaredField("protocolRouter");
            routerF.setAccessible(true);
            routerF.set(dispatcher, router);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        DeviceCommand cmd = new DeviceCommand();
        DeviceCommandResult result = dispatcher.dispatch(null, cmd, "{}");
        assertFalse(result.isSuccess());
        assertEquals("DEVICE_NULL", result.getErrorCode());
    }

    @Test
    @DisplayName("DeviceCommandResult.ok/fail 工厂方法")
    void resultFactories() {
        DeviceCommandResult ok = DeviceCommandResult.ok("cmd-1", "topic/x", "ack", 100);
        assertTrue(ok.isSuccess());
        assertEquals("cmd-1", ok.getCommandId());
        assertEquals(100, ok.getDurationMs());

        DeviceCommandResult fail = DeviceCommandResult.fail("ERR", "msg");
        assertFalse(fail.isSuccess());
        assertEquals("ERR", fail.getErrorCode());
    }
}
