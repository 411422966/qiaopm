package com.meiyetec.iot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.IotDevice;
import com.meiyetec.entity.IotDeviceData;
import com.meiyetec.entity.IotTelemetry;
import com.meiyetec.mapper.IotDeviceDataMapper;
import com.meiyetec.mapper.IotDeviceMapper;
import com.meiyetec.mapper.IotTelemetryMapper;
import com.meiyetec.service.IotDeviceLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IOT-006：HiveMQ 设备上行消息监听器
 *
 * <p>数据流：
 * <pre>
 * HiveMQ → MqttClientService.subscribe() → MqttInboundListener
 *   → 解析 Topic（提取 tenantId / deviceCode）
 *   → 查询 IotDevice（匹配 protocol）
 *   → IotDeviceAdapter.parseReport()（按设备型号解析）
 *   → 持久化 IotTelemetry + biz_iot_device_data
 *   → 触发设备联动（IotDeviceLinkService.executeLinksByMaster）
 * </pre>
 *
 * <p>Topic 格式：
 * <pre>
 *   devices/{tenantId}/{deviceCode}/ack     — 设备响应 / 遥测上报
 *   devices/{tenantId}/{deviceCode}/status  — 设备状态变更
 * </pre>
 *
 * <p>激活方式：{@code iot.mqtt.enabled=true} 且配置了 {@code iot.mqtt.broker-uri}。
 * 未启用时自动跳过（ConditionalOnProperty）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "iot.mqtt", name = "enabled", havingValue = "true")
public class MqttInboundListener {

    private final MqttClientService mqttService;
    private final IotDeviceMapper deviceMapper;
    private final IotTelemetryMapper telemetryMapper;
    private final IotDeviceDataMapper deviceDataMapper;
    private final IotProtocolRouter protocolRouter;
    private final IotDeviceLinkService linkService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    private volatile boolean initialized = false;

    @Autowired
    public MqttInboundListener(
            MqttClientService mqttService,
            IotDeviceMapper deviceMapper,
            IotTelemetryMapper telemetryMapper,
            IotDeviceDataMapper deviceDataMapper,
            IotProtocolRouter protocolRouter,
            IotDeviceLinkService linkService,
            JdbcTemplate jdbc) {
        this.mqttService = mqttService;
        this.deviceMapper = deviceMapper;
        this.telemetryMapper = telemetryMapper;
        this.deviceDataMapper = deviceDataMapper;
        this.protocolRouter = protocolRouter;
        this.linkService = linkService;
        this.jdbc = jdbc;
    }

    /**
     * 向 MQTT 服务注册订阅（在第一次收到消息前延迟初始化，
     * 避免循环依赖：MqttClientService → MqttInboundListener → IotDeviceLinkService → ...）
     */
    private void ensureSubscribed() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            mqttService.subscribe(this::onMqttMessage);
            initialized = true;
            log.info("[IOT-006] MqttInboundListener 已注册到 MqttClientService");
        }
    }

    /**
     * MQTT 消息回调入口
     */
    public void onMqttMessage(String topic, String payload) {
        // 延迟注册订阅，避免 Spring 启动阶段循环依赖
        ensureSubscribed();

        long start = System.currentTimeMillis();
        try {
            TopicParts parts = parseTopic(topic);
            if (parts == null) {
                log.warn("[IOT-006] 无法解析 Topic：{}", topic);
                return;
            }

            log.debug("[IOT-006] 收到设备上报：topic={} payload={}", topic,
                    payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

            IotDevice device = findDevice(parts.tenantId, parts.deviceCode);
            if (device == null) {
                log.warn("[IOT-006] 设备未注册：tenantId={} deviceCode={}", parts.tenantId, parts.deviceCode);
                return;
            }

            // 分类处理
            if (parts.isStatus) {
                handleStatusReport(device, payload);
            } else {
                handleTelemetryReport(device, payload);
            }

            // 更新设备在线状态
            updateOnlineStatus(device.getId());

            long duration = System.currentTimeMillis() - start;
            log.info("[IOT-006] 设备上报处理完成：device={} topicType={} durationMs={}",
                    device.getDeviceCode(), parts.isStatus ? "status" : "telemetry", duration);

        } catch (Exception e) {
            log.error("[IOT-006] 处理设备上报异常：topic={} err={}", topic, e.getMessage(), e);
        }
    }

    // ====================================================================
    // 遥测上报处理
    // ====================================================================

    private void handleTelemetryReport(IotDevice device, String payload) {
        IotDeviceAdapter adapter = protocolRouter.getAdapterForDevice(device);
        IotDeviceAdapter.ParsedDeviceReport report = adapter.parseReport(device, payload);

        if (!report.isValid()) {
            log.warn("[IOT-006] 设备上报解析失败：device={} err={}",
                    device.getDeviceCode(), report.getErrorMessage());
            return;
        }

        // 1. 持久化 IotTelemetry（每指标一行）
        persistTelemetry(device, payload, report);

        // 2. 持久化 biz_iot_device_data（汇总行）
        persistDeviceData(device, report);

        // 3. 触发设备联动
        triggerDeviceLinks(device, report);
    }

    private void persistTelemetry(IotDevice device, String rawPayload, IotDeviceAdapter.ParsedDeviceReport report) {
        LocalDateTime now = LocalDateTime.now();

        // 从 payload 中提取各指标
        try {
            JsonNode root = json.readTree(rawPayload);

            // 通用字段提取
            JsonNode metricsNode = root.path("metrics");
            if (metricsNode.isObject()) {
                metricsNode.fields().forEachRemaining(e -> {
                    String metric = e.getKey().toUpperCase();
                    IotTelemetry t = new IotTelemetry();
                    t.setTenantId(device.getTenantId());
                    t.setDeviceId(device.getId());
                    t.setMetric(metric);
                    t.setTopic("devices/" + device.getTenantId() + "/" + device.getDeviceCode() + "/ack");
                    t.setPayload(rawPayload);
                    if (e.getValue().isNumber()) {
                        t.setValueNum(new BigDecimal(e.getValue().asText()));
                    } else {
                        t.setValueStr(e.getValue().asText());
                    }
                    telemetryMapper.insert(t);
                });
            }

            // 其他顶层字段
            root.fields().forEachRemaining(e -> {
                String key = e.getKey();
                if (List.of("metrics", "type", "deviceCode", "timestamp", "seq").contains(key)) return;
                if (e.getValue().isNumber()) {
                    IotTelemetry t = new IotTelemetry();
                    t.setTenantId(device.getTenantId());
                    t.setDeviceId(device.getId());
                    t.setMetric(key.toUpperCase());
                    t.setValueNum(new BigDecimal(e.getValue().asText()));
                    t.setTopic("devices/" + device.getTenantId() + "/" + device.getDeviceCode() + "/ack");
                    t.setPayload(rawPayload);
                    telemetryMapper.insert(t);
                }
            });
        } catch (JsonProcessingException e) {
            // 非 JSON：整条作为原始值
            IotTelemetry t = new IotTelemetry();
            t.setTenantId(device.getTenantId());
            t.setDeviceId(device.getId());
            t.setMetric("RAW");
            t.setValueStr(rawPayload);
            t.setTopic("devices/" + device.getTenantId() + "/" + device.getDeviceCode() + "/ack");
            t.setPayload(rawPayload);
            telemetryMapper.insert(t);
        }
    }

    private void persistDeviceData(IotDevice device, IotDeviceAdapter.ParsedDeviceReport report) {
        IotDeviceData data = new IotDeviceData();
        data.setTenantId(device.getTenantId());
        data.setStoreId(device.getStoreId());
        data.setDeviceId(device.getId());
        data.setDeviceCode(device.getDeviceCode());
        data.setDeviceType(device.getDeviceType());
        data.setMemberId(device.getBindMemberId());
        data.setDataType(report.getDataType() != null ? report.getDataType() : "operation");
        data.setImageUrl(report.getImageUrl());
        data.setReportUrl(report.getReportUrl());
        data.setDeviceReportTime(report.getDeviceReportTime() != null
                ? report.getDeviceReportTime() : LocalDateTime.now());
        data.setSource("device");
        data.setValid(1);

        // 从 metrics 中抽取主指标
        if (report.getMetrics() != null && !report.getMetrics().isEmpty()) {
            List<String> keys = List.copyOf(report.getMetrics().keySet());
            if (keys.size() >= 1) data.setPrimaryMetric(report.getMetrics().get(keys.get(0)));
            if (keys.size() >= 2) data.setSecondaryMetric(report.getMetrics().get(keys.get(1)));
            try {
                data.setMetrics(json.writeValueAsString(report.getMetrics()));
            } catch (JsonProcessingException e) {
                data.setMetrics("{}");
            }
        }
        deviceDataMapper.insert(data);
    }

    private void triggerDeviceLinks(IotDevice device, IotDeviceAdapter.ParsedDeviceReport report) {
        Map<String, Object> triggerData = new HashMap<>();
        if (report.getMetrics() != null) {
            triggerData.put("metrics", report.getMetrics());
        }
        triggerData.put("dataType", report.getDataType());
        triggerData.put("deviceCode", device.getDeviceCode());
        triggerData.put("deviceId", device.getId());
        if (report.getDeviceReportTime() != null) {
            triggerData.put("reportTime", report.getDeviceReportTime().toString());
        }

        try {
            List<?> logs = linkService.executeLinksByMaster(device.getId(), "TELEMETRY", triggerData);
            if (!logs.isEmpty()) {
                log.info("[IOT-006] 设备 {} 触发了 {} 个联动", device.getDeviceCode(), logs.size());
            }
        } catch (Exception e) {
            log.warn("[IOT-006] 联动触发失败：device={} err={}", device.getDeviceCode(), e.getMessage());
        }
    }

    // ====================================================================
    // 状态上报处理
    // ====================================================================

    private void handleStatusReport(IotDevice device, String payload) {
        try {
            JsonNode root = json.readTree(payload);
            String status = root.path("status").asText(null);
            String firmwareVersion = root.path("firmwareVersion").asText(device.getFirmwareVersion());
            String batteryLevel = root.path("batteryLevel").asText(null);

            if (status != null) {
                String onlineStatus = "online".equalsIgnoreCase(status) ? "ONLINE" : "OFFLINE";
                jdbc.update(
                        "UPDATE iot_device SET online_status = ?, firmware_version = ?, last_online_at = NOW() WHERE id = ?",
                        onlineStatus, firmwareVersion, device.getId());
                log.info("[IOT-006] 设备状态更新：device={} status={}", device.getDeviceCode(), onlineStatus);
            }
        } catch (Exception e) {
            log.warn("[IOT-006] 状态上报解析失败：device={} err={}", device.getDeviceCode(), e.getMessage());
        }
    }

    // ====================================================================
    // 工具
    // ====================================================================

    /**
     * 解析 Topic path parts。
     * 格式：devices/{tenantId}/{deviceCode}/ack
     *       devices/{tenantId}/{deviceCode}/status
     */
    private TopicParts parseTopic(String topic) {
        if (topic == null || !topic.startsWith("devices/")) return null;
        String[] parts = topic.split("/");
        if (parts.length < 4) return null;
        try {
            return new TopicParts(
                    Long.parseLong(parts[1]),   // tenantId
                    parts[2],                    // deviceCode
                    "status".equals(parts[3])   // isStatus
            );
        } catch (NumberFormatException e) {
            log.warn("[IOT-006] Topic tenantId 解析失败：{}", topic);
            return null;
        }
    }

    private IotDevice findDevice(Long tenantId, String deviceCode) {
        LambdaQueryWrapper<IotDevice> q = new LambdaQueryWrapper<>();
        q.eq(IotDevice::getDeviceCode, deviceCode);
        if (tenantId != null) q.eq(IotDevice::getTenantId, tenantId);
        return deviceMapper.selectOne(q);
    }

    private void updateOnlineStatus(Long deviceId) {
        if (deviceId == null) return;
        jdbc.update(
                "UPDATE iot_device SET online_status = 'ONLINE', last_online_at = NOW() WHERE id = ?",
                deviceId);
    }

    private record TopicParts(Long tenantId, String deviceCode, boolean isStatus) {}
}
