package com.meiyetec.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.IotDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 按摩椅专用协议适配器
 *
 * <p>支持的设备型号：荣泰 RT-8900 / 奥佳华 OG-7505 / 傲胜 OS-8800 等
 * <p>通信方式：BLE 网关透传（MQTT transport）+ REST API 回调
 * <p>上报 Topic：devices/{tenantId}/{deviceCode}/ack
 *
 * <p>支持的 MQTT 上报格式（JSON）：
 * <pre>
 * {
 *   "type": "treatment",
 *   "seq": 1234,
 *   "timestamp": "2026-09-05T17:00:00",
 *   "mode": "深层按摩",          // 模式名称
 *   "modeCode": "DEEP",         // 模式代码
 *   "intensity": 80,            // 力度 0-100
 *   "temperature": 38.5,        // 温度（℃）
 *   "duration": 30,             // 治疗时长（分钟）
 *   "pressure": 60,             // 气压（kPa）
 *   "airbagCount": 12,          // 气囊数量
 *   "rollSpeed": 3,             // 滚轮速度（档位）
 *   "bodyZone": "back",         // 按摩部位 back / leg / full
 *   "userScore": 4,             // 用户满意度评分 1-5
 *   "calories": 45.2,           // 消耗卡路里
 *   "heartRate": 72,            // 心率（次/分）
 *   "metrics": {
 *     "massageScore": 4.5
 *   }
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
 *
 * <p>下发命令格式：
 * <pre>
 * // 启动按摩
 * { "commandType": "start", "params": { "mode": "DEEP", "intensity": 80, "duration": 30 } }
 * // 停止
 * { "commandType": "stop" }
 * // 调整力度
 * { "commandType": "adjust", "params": { "intensity": 60 } }
 * </pre>
 */
@Slf4j
@Component
public class MassageChairAdapter implements IotDeviceAdapter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String protocolCode() {
        return "massage_chair";
    }

    @Override
    public boolean validateCredentials(IotDevice device, Map<String, String> credentials) {
        return true; // 按摩椅通过 gateway auth
    }

    @Override
    public ParsedDeviceReport parseReport(IotDevice device, String rawPayload) {
        ParsedDeviceReport report = new ParsedDeviceReport();
        report.dataType = "treatment";
        try {
            Map<String, Object> payload = mapper.readValue(rawPayload, Map.class);
            report.valid = true;
            report.dataType = (String) payload.getOrDefault("type", "treatment");
            report.deviceReportTime = LocalDateTime.now();

            Map<String, BigDecimal> metrics = new HashMap<>();

            // 力度 / 温度 / 时长 / 气压
            extractNumeric(payload, "intensity", "力度", metrics);
            extractNumeric(payload, "temperature", "温度", metrics);
            extractNumeric(payload, "duration", "时长", metrics);
            extractNumeric(payload, "pressure", "气压", metrics);
            extractNumeric(payload, "rollSpeed", "滚轮速度", metrics);
            extractNumeric(payload, "airbagCount", "气囊数量", metrics);
            extractNumeric(payload, "userScore", "用户评分", metrics);
            extractNumeric(payload, "calories", "卡路里", metrics);
            extractNumeric(payload, "heartRate", "心率", metrics);
            extractNumeric(payload, "battery", "电池", metrics);

            // 模式名称转数值（用于条件联动）
            String mode = (String) payload.get("mode");
            String modeCode = (String) payload.get("modeCode");
            if (mode != null) metrics.put("模式", new BigDecimal(mode.hashCode() & 0xFFFF));
            if (modeCode != null) metrics.put("模式代码", new BigDecimal(modeCode.hashCode() & 0xFFFF));

            // 部位
            String bodyZone = (String) payload.get("bodyZone");
            if (bodyZone != null) {
                int zone = switch (bodyZone.toLowerCase()) {
                    case "back" -> 1;
                    case "leg" -> 2;
                    case "full" -> 3;
                    case "neck" -> 4;
                    case "shoulder" -> 5;
                    default -> 0;
                };
                metrics.put("按摩部位", BigDecimal.valueOf(zone));
            }

            // 嵌套 metrics
            Object innerMetrics = payload.get("metrics");
            if (innerMetrics instanceof Map) {
                ((Map<?, ?>) innerMetrics).forEach((k, v) -> {
                    if (v instanceof Number n) {
                        metrics.put(String.valueOf(k), new BigDecimal(n.toString()));
                    }
                });
            }

            report.metrics = metrics;
            report.imageUrl = (String) payload.get("imageUrl");
            report.reportUrl = (String) payload.get("reportUrl");
            report.errorMessage = (String) payload.get("error");

            log.info("[MASSAGE] Treatment report: device={} mode={} intensity={} duration={}°C={}",
                    device.getDeviceCode(), mode,
                    metrics.get("力度"), metrics.get("时长"), metrics.get("温度"));
            return report;

        } catch (Exception e) {
            log.error("[MASSAGE] Parse error device={}: {}", device.getDeviceCode(), e.getMessage());
            report.valid = false;
            report.errorMessage = e.getMessage();
            return report;
        }
    }

    @Override
    public CommandResponse sendCommand(IotDevice device, DeviceCommand command) {
        CommandResponse response = new CommandResponse();
        response.success = true;
        response.commandId = "massage_cmd_" + System.currentTimeMillis();
        String cmdType = command.commandType;
        Map<String, Object> params = command.params;

        String action = switch (cmdType) {
            case "start" -> "启动按摩：" + params.get("mode") + " 力度=" + params.get("intensity");
            case "stop" -> "停止按摩";
            case "pause" -> "暂停按摩";
            case "resume" -> "继续按摩";
            case "adjust" -> "调整：" + params.keySet();
            default -> "自定义命令：" + cmdType;
        };
        log.info("[MASSAGE] Command to {}: {}", device.getDeviceCode(), action);
        return response;
    }

    @Override
    public HealthInfo healthCheck(IotDevice device) {
        HealthInfo info = new HealthInfo();
        info.online = "ONLINE".equals(device.getOnlineStatus());
        info.latencyMs = null;
        info.firmwareVersion = device.getFirmwareVersion();
        info.message = info.online ? "按摩椅在线" : "按摩椅离线";
        return info;
    }

    private void extractNumeric(Map<String, Object> src, String key, String metricKey,
                                Map<String, BigDecimal> target) {
        if (src.containsKey(key)) {
            Object v = src.get(key);
            if (v instanceof Number n) {
                target.put(metricKey, new BigDecimal(n.toString()));
            }
        }
    }
}
