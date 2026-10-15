package com.meiyetec.iot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * IOT-006：设备命令统一下发器（按设备 protocol 字段路由）
 *
 * <p>路由逻辑：
 * <ul>
 *   <li>{@code protocol == "mqtt"}：通过 {@link MqttClientService} 下发</li>
 *   <li>{@code protocol == "http"}：通过 {@link HttpDeviceControlService} 下发（命令端点从 actionJson.cmdUrl 解析）</li>
 *   <li>其它（fallback）：复用 {@link IotProtocolRouter} 兼容旧 {@link IotDeviceAdapter#sendCommand}</li>
 * </ul>
 */
@Slf4j
@Service
public class DeviceCommandDispatcher {

    @Autowired(required = false) private MqttClientService mqttService;
    @Autowired private HttpDeviceControlService httpService;
    @Autowired private IotProtocolRouter protocolRouter;

    /**
     * 下发命令
     *
     * @param device      目标设备（必须含 protocol 字段）
     * @param command     命令内容
     * @param actionJson  联动动作 JSON（含 cmdUrl 等参数，HTTP 模式必填）
     */
    public DeviceCommandResult dispatch(com.meiyetec.entity.IotDevice device,
                                         DeviceCommand command,
                                         String actionJson) {
        if (device == null) {
            return DeviceCommandResult.fail("DEVICE_NULL", "设备为空");
        }
        String protocol = device.getProtocol() == null ? "mqtt" : device.getProtocol().toLowerCase();
        return switch (protocol) {
            case "mqtt" -> dispatchMqtt(device, command);
            case "http" -> dispatchHttp(device, command, actionJson);
            default -> dispatchFallback(device, command);
        };
    }

    private DeviceCommandResult dispatchMqtt(com.meiyetec.entity.IotDevice device, DeviceCommand cmd) {
        if (mqttService == null) {
            log.warn("[IOT-006] MQTT 服务未启用（缺少 iot.mqtt.enabled=true 配置），降级到 Stub");
            return dispatchFallback(device, cmd);
        }
        return mqttService.publish(device, cmd);
    }

    private DeviceCommandResult dispatchHttp(com.meiyetec.entity.IotDevice device, DeviceCommand cmd,
                                              String actionJson) {
        // 从 actionJson 解析 cmdUrl，否则用 device.ipAddress 拼接默认端口
        String cmdUrl = parseCmdUrl(device, actionJson);
        if (cmdUrl == null) {
            return DeviceCommandResult.fail("HTTP_URL_UNRESOLVED", "无法解析 HTTP 命令端点");
        }
        return httpService.sendCommand(device, cmd, cmdUrl);
    }

    private DeviceCommandResult dispatchFallback(com.meiyetec.entity.IotDevice device, DeviceCommand cmd) {
        IotDeviceAdapter adapter = protocolRouter.getAdapterForDevice(device);
        IotDeviceAdapter.DeviceCommand legacyCmd = new IotDeviceAdapter.DeviceCommand();
        legacyCmd.commandType = cmd.getCommandType();
        legacyCmd.params = cmd.getParams();
        legacyCmd.timeoutSeconds = cmd.getTimeoutSeconds();
        IotDeviceAdapter.CommandResponse resp = adapter.sendCommand(device, legacyCmd);
        if (!resp.success) {
            return DeviceCommandResult.fail("ADAPTER_REJECTED",
                    resp.errorMessage == null ? "适配器拒绝" : resp.errorMessage);
        }
        return DeviceCommandResult.ok(resp.commandId, null, null, 0);
    }

    @SuppressWarnings("unchecked")
    private String parseCmdUrl(com.meiyetec.entity.IotDevice device, String actionJson) {
        if (actionJson != null && !actionJson.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> m = json.readValue(actionJson, java.util.Map.class);
                Object url = m.get("cmdUrl");
                if (url != null) return url.toString();
            } catch (Exception ignored) {
            }
        }
        // 兜底：用 ipAddress 拼接默认端口
        if (device.getIpAddress() != null && !device.getIpAddress().isEmpty()) {
            return "http://" + device.getIpAddress() + "/api/v1/cmd";
        }
        return null;
    }
}
