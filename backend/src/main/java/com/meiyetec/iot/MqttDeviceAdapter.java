package com.meiyetec.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.IotDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT 设备协议适配器
 *
 * <p>支持的 MQTT 上报格式（JSON）：
 * <pre>
 * // 通用格式
 * {
 *   "type": "detection",              // detection / treatment / operation / heartbeat / alert
 *   "timestamp": "2026-09-05T17:00:00",
 *   "metrics": {
 *     "水分": 35.5,
 *     "油分": 28.2,
 *     "弹性": 0.85
 *   },
 *   "imageUrl": "https://...",
 *   "reportUrl": "https://..."
 * }
 *
 * // 按摩椅格式
 * {
 *   "type": "treatment",
 *   "mode": "深层按摩",
 *   "duration": 30,
 *   "intensity": 80,
 *   "temperature": 38.5
 * }
 *
 * // 心跳格式
 * {
 *   "type": "heartbeat",
 *   "battery": 85,
 *   "firmwareVersion": "v2.3.1",
 *   "status": "idle"
 * }
 * </pre>
 */
@Slf4j
@Component("mqttAdapter")
public class MqttDeviceAdapter implements IotDeviceAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired(required = false)
    private MqttClientService mqttClient;

    @Override
    public String protocolCode() {
        return "mqtt";
    }

    @Override
    public boolean validateCredentials(IotDevice device, Map<String, String> credentials) {
        return credentials != null && credentials.containsKey("clientId");
    }

    @Override
    public ParsedDeviceReport parseReport(IotDevice device, String rawPayload) {
        ParsedDeviceReport report = new ParsedDeviceReport();
        try {
            Map<String, Object> payload = mapper.readValue(rawPayload, Map.class);
            report.valid = true;
            report.dataType = (String) payload.getOrDefault("type", inferDataType(device, payload));
            report.deviceReportTime = parseTimestamp(payload.get("timestamp"));

            // 提取 metrics（通用字段）
            Object metricsObj = payload.get("metrics");
            Map<String, BigDecimal> metrics = new HashMap<>();
            if (metricsObj instanceof Map) {
                ((Map<?, ?>) metricsObj).forEach((k, v) -> {
                    if (v instanceof Number n) {
                        metrics.put(String.valueOf(k), new BigDecimal(n.toString()));
                    } else if (v instanceof String s) {
                        try { metrics.put(String.valueOf(k), new BigDecimal(s)); } catch (Exception ignored) {}
                    }
                });
            }

            // 补充设备型号特定字段
            enrichByDeviceType(device, payload, metrics);

            report.metrics = metrics;
            report.imageUrl = (String) payload.get("imageUrl");
            report.reportUrl = (String) payload.get("reportUrl");
            report.errorMessage = (String) payload.get("error");

            log.info("[MQTT] Report from device {}: type={}, metrics={}",
                    device.getDeviceCode(), report.dataType, metrics);
            return report;

        } catch (Exception e) {
            log.error("[MQTT] Parse error device={}: {}", device.getDeviceCode(), e.getMessage());
            report.valid = false;
            report.errorMessage = e.getMessage();
            return report;
        }
    }

    /**
     * 根据设备型号推断数据类型
     */
    private String inferDataType(IotDevice device, Map<String, Object> payload) {
        if (payload.containsKey("moisture") || payload.containsKey("水分")
                || payload.containsKey("oil") || payload.containsKey("油分")
                || "skin_analyzer".equals(device.getDeviceType())) {
            return "detection";
        }
        if (payload.containsKey("intensity") || payload.containsKey("mode")
                || "massage_bed".equals(device.getDeviceType())
                || "massage_chair".equals(device.getDeviceType())) {
            return "treatment";
        }
        if (payload.containsKey("status") && "heartbeat".equals(payload.get("type"))) {
            return "heartbeat";
        }
        if (payload.containsKey("alert") || payload.containsKey("errorCode")) {
            return "alert";
        }
        return "operation";
    }

    /**
     * 按设备型号补充特定指标
     */
    private void enrichByDeviceType(IotDevice device, Map<String, Object> payload,
                                   Map<String, BigDecimal> metrics) {
        String deviceType = device.getDeviceType();
        if (deviceType == null) return;

        switch (deviceType) {
            case "skin_analyzer" -> {
                // VISIA / 智能美容镜：水分/油分/弹性/毛孔/色斑
                extractNumeric(payload, "moisture", "水分", metrics);
                extractNumeric(payload, "oil", "油分", metrics);
                extractNumeric(payload, "elasticity", "弹性", metrics);
                extractNumeric(payload, "pore", "毛孔", metrics);
                extractNumeric(payload, "spot", "色斑", metrics);
                extractNumeric(payload, "wrinkle", "皱纹", metrics);
            }
            case "massage_bed", "massage_chair" -> {
                // 按摩椅：力度/温度/时长/模式
                extractNumeric(payload, "intensity", "力度", metrics);
                extractNumeric(payload, "temperature", "温度", metrics);
                extractNumeric(payload, "duration", "时长", metrics);
                extractNumeric(payload, "pressure", "压力", metrics);
                String mode = (String) payload.get("mode");
                if (mode != null) metrics.put("模式", new BigDecimal(mode.hashCode()));
            }
            case "led_lamp" -> {
                // LED 红光仪：波长/照度/治疗时长
                extractNumeric(payload, "wavelength", "波长", metrics);
                extractNumeric(payload, "illuminance", "照度", metrics);
                extractNumeric(payload, "treatmentMinutes", "治疗时长", metrics);
            }
            case "smart_lock" -> {
                // 智能门锁：电池电量/开关次数
                extractNumeric(payload, "battery", "电池", metrics);
                extractNumeric(payload, "openCount", "开关次数", metrics);
            }
            case "smart_mirror" -> {
                // 智能美容镜：综合评分
                extractNumeric(payload, "score", "综合评分", metrics);
                extractNumeric(payload, "skinAge", "肌肤年龄", metrics);
            }
            default -> {
                // 通用：从 payload 中抽取所有数值字段
                payload.forEach((k, v) -> {
                    if (!List.of("type", "timestamp", "metrics", "imageUrl",
                            "reportUrl", "error", "deviceCode", "seq").contains(k)) {
                        extractNumeric(payload, k, k, metrics);
                    }
                });
            }
        }
    }

    private void extractNumeric(Map<String, Object> src, String key, String metricKey,
                                Map<String, BigDecimal> target) {
        if (src.containsKey(key)) {
            Object v = src.get(key);
            if (v instanceof Number n) {
                target.put(metricKey, new BigDecimal(n.toString()));
            } else if (v instanceof String s) {
                try { target.put(metricKey, new BigDecimal(s)); } catch (Exception ignored) {}
            }
        }
    }

    private LocalDateTime parseTimestamp(Object ts) {
        if (ts == null) return LocalDateTime.now();
        try {
            if (ts instanceof String s) {
                return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (ts instanceof Number n) {
                return LocalDateTime.now(); // epoch ms
            }
        } catch (Exception e) {
            log.warn("[MQTT] Timestamp parse failed: {}", ts);
        }
        return LocalDateTime.now();
    }

    @Override
    public CommandResponse sendCommand(IotDevice device, DeviceCommand command) {
        CommandResponse response = new CommandResponse();
        if (mqttClient == null) {
            // 无 MQTT 服务，降级为日志记录
            response.success = true;
            response.commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "");
            log.warn("[MQTT-ADAPTER] MqttClientService 未启用，命令仅记录：device={} cmd={}",
                    device.getDeviceCode(), command.commandType);
            return response;
        }
        try {
            com.meiyetec.iot.DeviceCommandResult r =
                    mqttClient.publish(device, mapToIotCommand(command));
            response.success = r.isSuccess();
            response.commandId = r.getCommandId();
            if (!r.isSuccess()) {
                response.errorMessage = r.getErrorCode() + ": " + r.getErrorMessage();
            }
            return response;
        } catch (Exception e) {
            response.success = false;
            response.errorMessage = e.getMessage();
            return response;
        }
    }

    private com.meiyetec.iot.DeviceCommand mapToIotCommand(DeviceCommand cmd) {
        com.meiyetec.iot.DeviceCommand c = new com.meiyetec.iot.DeviceCommand();
        c.setCommandType(cmd.commandType);
        c.setParams(cmd.params);
        c.setTimeoutSeconds(cmd.timeoutSeconds);
        return c;
    }

    @Override
    public HealthInfo healthCheck(IotDevice device) {
        HealthInfo info = new HealthInfo();
        // 如果有实时心跳数据，可以从 iot_telemetry 取最新记录
        info.online = "ONLINE".equals(device.getOnlineStatus());
        info.latencyMs = null;
        info.batteryLevel = null;
        info.firmwareVersion = device.getFirmwareVersion();
        info.message = info.online ? "设备在线" : "设备离线";
        return info;
    }
}
