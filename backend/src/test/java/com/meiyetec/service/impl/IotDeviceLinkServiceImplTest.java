package com.meiyetec.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.IotDevice;
import com.meiyetec.entity.IotDeviceLink;
import com.meiyetec.entity.IotDeviceLinkLog;
import com.meiyetec.iot.DeviceCommandDispatcher;
import com.meiyetec.iot.DeviceCommandResult;
import com.meiyetec.iot.HttpDeviceControlService;
import com.meiyetec.iot.IotProtocolRouter;
import com.meiyetec.mapper.IotDeviceLinkLogMapper;
import com.meiyetec.mapper.IotDeviceLinkMapper;
import com.meiyetec.mapper.IotDeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IOT-006：设备联动执行单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>联动不存在 → LINK_NOT_FOUND</li>
 *   <li>联动已停用 → LINK_DISABLED</li>
 *   <li>条件不满足 → CONDITION_NOT_MET</li>
 *   <li>从设备不存在 → SLAVE_DEVICE_NOT_FOUND</li>
 *   <li>命令下发成功 → status=success</li>
 *   <li>命令下发失败 → status=failed + errorCode</li>
 * </ol>
 */
@DisplayName("IOT-006 IotDeviceLinkServiceImpl.executeLink")
class IotDeviceLinkServiceImplTest {

    private IotDeviceLinkServiceImpl service;
    private IotDeviceLinkMapper linkMapper;
    private IotDeviceLinkLogMapper logMapper;
    private IotDeviceMapper deviceMapper;
    private DeviceCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        service = new IotDeviceLinkServiceImpl();
        linkMapper = mock(IotDeviceLinkMapper.class);
        logMapper = mock(IotDeviceLinkLogMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        dispatcher = mock(DeviceCommandDispatcher.class);

        java.lang.reflect.Field f;
        f = IotDeviceLinkServiceImpl.class.getDeclaredField("linkLogMapper");
        f.setAccessible(true);
        f.set(service, logMapper);

        // baseMapper 继承自 ServiceImpl
        Class<?> c = service.getClass();
        while (c != null) {
            try {
                f = c.getDeclaredField("baseMapper");
                break;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        if (f == null) throw new IllegalStateException("baseMapper field not found");
        f.setAccessible(true);
        f.set(service, linkMapper);

        f = IotDeviceLinkServiceImpl.class.getDeclaredField("deviceMapper");
        f.setAccessible(true);
        f.set(service, deviceMapper);

        f = IotDeviceLinkServiceImpl.class.getDeclaredField("commandDispatcher");
        f.setAccessible(true);
        f.set(service, dispatcher);
    }

    private IotDeviceLink link(Long id, String condition, String action, Integer enabled) {
        IotDeviceLink link = new IotDeviceLink();
        link.setId(id);
        link.setTenantId(1L);
        link.setStoreId(1L);
        link.setMasterDeviceId(100L);
        link.setSlaveDeviceId(200L);
        link.setTriggerType("TELEMETRY");
        link.setTriggerCondition(condition);
        link.setActionJson(action);
        link.setEnabled(enabled);
        return link;
    }

    private IotDevice slaveDevice(Long id, String protocol) {
        IotDevice d = new IotDevice();
        d.setId(id);
        d.setDeviceCode("DEV-" + id);
        d.setProtocol(protocol);
        d.setMqttTopic("devices/1/DEV-" + id);
        return d;
    }

    @Test
    @DisplayName("联动不存在 → LINK_NOT_FOUND")
    void link_notFound() {
        when(linkMapper.selectById(anyLong())).thenReturn(null);
        IotDeviceLinkLog result = service.executeLink(999L, new HashMap<>());
        assertNotNull(result);
        assertEquals("failed", result.getStatus());
        assertEquals("LINK_NOT_FOUND", result.getErrorCode());
        verify(logMapper, never()).insert(any());
    }

    @Test
    @DisplayName("联动已停用 → LINK_DISABLED")
    void link_disabled() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null, null, 0));
        IotDeviceLinkLog result = service.executeLink(1L, new HashMap<>());
        assertEquals("failed", result.getStatus());
        assertEquals("LINK_DISABLED", result.getErrorCode());
    }

    @Test
    @DisplayName("triggerCondition 为空 → 默认通过")
    void condition_null_defaultPass() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null,
                "{\"commandType\":\"lock\"}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "mqtt"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.ok("cmd-1", "topic/x", null, 50));

        IotDeviceLinkLog result = service.executeLink(1L, Map.of("foo", "bar"));
        assertEquals("success", result.getStatus());
        assertEquals(1, result.getTriggerMatched());
    }

    @Test
    @DisplayName("triggerCondition 不满足 → CONDITION_NOT_MET")
    void condition_notMet() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L,
                "metric.temperature > 100", "{\"commandType\":\"alert\"}", 1));
        Map<String, Object> data = new HashMap<>();
        Map<String, java.math.BigDecimal> metrics = new HashMap<>();
        metrics.put("temperature", new java.math.BigDecimal("25"));
        data.put("metrics", metrics);

        IotDeviceLinkLog result = service.executeLink(1L, data);
        assertEquals("failed", result.getStatus());
        assertEquals("CONDITION_NOT_MET", result.getErrorCode());
        assertEquals(0, result.getTriggerMatched());
        verify(dispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("triggerCondition 满足 → 命令下发成功")
    void condition_met_dispatchOk() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L,
                "metric.temperature > 30", "{\"commandType\":\"lock\",\"params\":{\"duration\":60}}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "mqtt"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.ok("cmd-success", "devices/1/DEV-200/cmd/lock", null, 80));

        Map<String, Object> data = new HashMap<>();
        Map<String, java.math.BigDecimal> metrics = new HashMap<>();
        metrics.put("temperature", new java.math.BigDecimal("35"));
        data.put("metrics", metrics);

        IotDeviceLinkLog result = service.executeLink(1L, data);
        assertEquals("success", result.getStatus());
        assertEquals("mqtt", result.getProtocol());
        assertEquals("devices/1/DEV-200/cmd/lock", result.getDeviceTopic());
        assertNotNull(result.getCommandPayload());
        assertTrue(result.getCommandPayload().contains("\"commandType\":\"lock\""));
        assertEquals(1, result.getTriggerMatched());
    }

    @Test
    @DisplayName("从设备不存在 → SLAVE_DEVICE_NOT_FOUND")
    void slaveDevice_notFound() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null,
                "{\"commandType\":\"lock\"}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(null);

        IotDeviceLinkLog result = service.executeLink(1L, new HashMap<>());
        assertEquals("failed", result.getStatus());
        assertEquals("SLAVE_DEVICE_NOT_FOUND", result.getErrorCode());
    }

    @Test
    @DisplayName("命令下发失败 → 记录错误码")
    void dispatch_failed() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null,
                "{\"commandType\":\"lock\"}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "mqtt"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.fail("MQTT_ERROR", "broker not available"));

        IotDeviceLinkLog result = service.executeLink(1L, new HashMap<>());
        assertEquals("failed", result.getStatus());
        assertEquals("MQTT_ERROR", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("broker not available"));
    }

    @Test
    @DisplayName("triggerData 含 metrics 与 state")
    void triggerData_complex() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L,
                "metric.humidity >= 60 && state == \"idle\"", "{\"commandType\":\"alert\"}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "mqtt"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.ok("c", "t", null, 10));

        Map<String, Object> data = new HashMap<>();
        Map<String, java.math.BigDecimal> metrics = new HashMap<>();
        metrics.put("humidity", new java.math.BigDecimal("75"));
        data.put("metrics", metrics);
        data.put("state", "idle");

        IotDeviceLinkLog result = service.executeLink(1L, data);
        assertEquals("success", result.getStatus());
    }

    @Test
    @DisplayName("executeLink 触发日志写入")
    void insertLogCalled() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null,
                "{\"commandType\":\"lock\"}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "mqtt"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.ok("c", "t", null, 10));

        service.executeLink(1L, new HashMap<>());
        verify(logMapper, times(1)).insert(any(IotDeviceLinkLog.class));
    }

    @Test
    @DisplayName("commandTimeout 来自 actionJson")
    void timeoutFromActionJson() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null,
                "{\"commandType\":\"lock\",\"timeoutSeconds\":15}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "mqtt"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.ok("c", "t", null, 10));

        IotDeviceLinkLog result = service.executeLink(1L, new HashMap<>());
        assertEquals("success", result.getStatus());
        // timeoutSeconds 透传给 dispatch
        verify(dispatcher).dispatch(any(), any(com.meiyetec.iot.DeviceCommand.class), any());
    }

    @Test
    @DisplayName("HTTP 协议从 actionJson.cmdUrl 解析")
    void http_protocol() {
        when(linkMapper.selectById(1L)).thenReturn(link(1L, null,
                "{\"commandType\":\"unlock\",\"cmdUrl\":\"http://device.local/api/cmd\"}", 1));
        when(deviceMapper.selectById(200L)).thenReturn(slaveDevice(200L, "http"));
        when(dispatcher.dispatch(any(), any(), any()))
                .thenReturn(DeviceCommandResult.ok("c", "http://device.local/api/cmd", "ack", 100));

        IotDeviceLinkLog result = service.executeLink(1L, new HashMap<>());
        assertEquals("success", result.getStatus());
        assertEquals("http", result.getProtocol());
    }
}
