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
 * 皮肤检测仪专用协议适配器
 *
 * <p>支持的设备型号：VISIA Gen7 / 智能美容镜 SM-2024 / Canfield / 魔镜系列
 * <p>通信方式：Wi-Fi REST 回调 / MQTT 上报
 * <p>上报 Topic：devices/{tenantId}/{deviceCode}/ack
 *
 * <p>支持的 MQTT 上报格式（JSON）：
 * <pre>
 * {
 *   "type": "detection",
 *   "seq": 5678,
 *   "timestamp": "2026-09-05T17:00:00",
 *   "customerId": 12345,
 *   "customerName": "张三",
 *   "skinAge": 28,
 *   "overallScore": 78.5,
 *   "metrics": {
 *     "moisture": 35.5,          // 水分 0-100
 *     "oil": 28.2,               // 油分 0-100
 *     "elasticity": 0.85,        // 弹性 0-1
 *     "pore": 45,                // 毛孔 0-100（越大毛孔越粗）
 *     "spot": 22,                // 色斑 0-100
 *     "wrinkle": 18,             // 皱纹 0-100
 *     "acne": 5,                 // 痘痘 0-100
 *     "sensitivity": 30,          // 敏感度 0-100
 *     "brightness": 65           // 亮度 0-100
 *   },
 *   "uvSpotCount": 120,          // 紫外光下色斑数量
 *   "redZoneArea": 5.2,          // 红区面积（%）
 *   "brownSpotArea": 3.8,        // 棕色区面积（%）
 *   "imageUrl": "https://.../skin/visia_001.jpg",
 *   "reportUrl": "https://.../report/visia_001.pdf"
 * }
 *
 * // 治疗模式格式
 * {
 *   "type": "treatment",
 *   "treatmentMode": "补水护理",
 *   "productUsed": "玻尿酸精华",
 *   "duration": 30,
 *   "skinBefore": { "moisture": 25, "oil": 45 },
 *   "skinAfter": { "moisture": 55, "oil": 30 },
 *   "imageBefore": "https://...",
 *   "imageAfter": "https://..."
 * }
 * </pre>
 *
 * <p>下发命令格式：
 * <pre>
 * // 拍照检测
 * { "commandType": "capture", "params": { "lightMode": "UV" } }
 * // 开始治疗
 * { "commandType": "startTreatment", "params": { "mode": "补水", "duration": 30 } }
 * // 停止
 * { "commandType": "stop" }
 * </pre>
 */
@Slf4j
@Component
public class SkinAnalyzerAdapter implements IotDeviceAdapter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String protocolCode() {
        return "skin_analyzer";
    }

    @Override
    public boolean validateCredentials(IotDevice device, Map<String, String> credentials) {
        return true; // 设备通过 gateway auth
    }

    @Override
    public ParsedDeviceReport parseReport(IotDevice device, String rawPayload) {
        ParsedDeviceReport report = new ParsedDeviceReport();
        report.dataType = "detection";
        try {
            Map<String, Object> payload = mapper.readValue(rawPayload, Map.class);
            report.valid = true;
            report.dataType = (String) payload.getOrDefault("type", "detection");
            report.deviceReportTime = LocalDateTime.now();

            Map<String, BigDecimal> metrics = new HashMap<>();

            // 标准皮肤指标
            extractNumeric(payload, "skinAge", "肌肤年龄", metrics);
            extractNumeric(payload, "overallScore", "综合评分", metrics);
            extractNumeric(payload, "moisture", "水分", metrics);
            extractNumeric(payload, "oil", "油分", metrics);
            extractNumeric(payload, "elasticity", "弹性", metrics);
            extractNumeric(payload, "pore", "毛孔", metrics);
            extractNumeric(payload, "spot", "色斑", metrics);
            extractNumeric(payload, "wrinkle", "皱纹", metrics);
            extractNumeric(payload, "acne", "痘痘", metrics);
            extractNumeric(payload, "sensitivity", "敏感度", metrics);
            extractNumeric(payload, "brightness", "亮度", metrics);

            // VISIA 专项
            extractNumeric(payload, "uvSpotCount", "紫外斑点数", metrics);
            extractNumeric(payload, "redZoneArea", "红区面积", metrics);
            extractNumeric(payload, "brownSpotArea", "棕色区面积", metrics);

            // 中文别名
            extractNumeric(payload, "moisture", "水分", metrics);
            extractNumeric(payload, "oil", "油分", metrics);

            // 嵌套 metrics
            Object innerMetrics = payload.get("metrics");
            if (innerMetrics instanceof Map) {
                ((Map<?, ?>) innerMetrics).forEach((k, v) -> {
                    if (v instanceof Number n) {
                        metrics.put(String.valueOf(k), new BigDecimal(n.toString()));
                    }
                });
            }

            // 治疗前后对比
            Object skinBefore = payload.get("skinBefore");
            if (skinBefore instanceof Map) {
                ((Map<?, ?>) skinBefore).forEach((k, v) -> {
                    if (v instanceof Number n) {
                        metrics.put("治疗前_" + k, new BigDecimal(n.toString()));
                    }
                });
            }
            Object skinAfter = payload.get("skinAfter");
            if (skinAfter instanceof Map) {
                ((Map<?, ?>) skinAfter).forEach((k, v) -> {
                    if (v instanceof Number n) {
                        metrics.put("治疗后_" + k, new BigDecimal(n.toString()));
                    }
                });
            }

            report.metrics = metrics;
            report.imageUrl = (String) payload.get("imageUrl");
            report.reportUrl = (String) payload.get("reportUrl");
            report.errorMessage = (String) payload.get("error");

            // 关键指标摘要日志
            BigDecimal moisture = metrics.get("水分");
            BigDecimal oil = metrics.get("油分");
            BigDecimal elasticity = metrics.get("弹性");
            log.info("[SKIN] Detection report: device={} moisture={} oil={} elasticity={} score={} image={}",
                    device.getDeviceCode(), moisture, oil, elasticity,
                    metrics.get("综合评分"),
                    report.imageUrl != null ? "YES" : "NO");
            return report;

        } catch (Exception e) {
            log.error("[SKIN] Parse error device={}: {}", device.getDeviceCode(), e.getMessage());
            report.valid = false;
            report.errorMessage = e.getMessage();
            return report;
        }
    }

    @Override
    public CommandResponse sendCommand(IotDevice device, DeviceCommand command) {
        CommandResponse response = new CommandResponse();
        response.success = true;
        response.commandId = "skin_cmd_" + System.currentTimeMillis();
        String cmdType = command.commandType;
        Map<String, Object> params = command.params;

        String action = switch (cmdType) {
            case "capture" -> "拍照采集：" + params.get("lightMode");
            case "startTreatment" -> "开始治疗：" + params.get("mode") + " 时长=" + params.get("duration") + "分钟";
            case "stop" -> "停止设备";
            case "reset" -> "重置设备";
            default -> "自定义命令：" + cmdType;
        };
        log.info("[SKIN] Command to {}: {}", device.getDeviceCode(), action);
        return response;
    }

    @Override
    public HealthInfo healthCheck(IotDevice device) {
        HealthInfo info = new HealthInfo();
        info.online = "ONLINE".equals(device.getOnlineStatus());
        info.firmwareVersion = device.getFirmwareVersion();
        info.message = info.online ? "皮肤检测仪在线" : "皮肤检测仪离线";
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
