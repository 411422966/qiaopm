package com.meiyetec.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * IOT-006：HTTP REST 设备控制通道
 *
 * <p>对接方式：
 * <ul>
 *   <li>设备对外暴露 REST API（如 {@code POST http://device.local/api/v1/cmd}）</li>
 *   <li>后端调用，传入 {@code apiKey} / {@code commandType} / {@code params}</li>
 *   <li>设备返回 200 + JSON 表示成功</li>
 * </ul>
 *
 * <p>适用：网络摄像头、Wi-Fi 美容仪、智能门禁。
 */
@Slf4j
@Service
public class HttpDeviceControlService {

    private final HttpClient httpClient;
    private final ObjectMapper json = new ObjectMapper();

    public HttpDeviceControlService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 下发命令（POST）
     *
     * @param device   目标设备（authToken 用作 apiKey）
     * @param command  命令内容
     * @param cmdUrl   命令端点 URL（设备控制接口）
     * @return 下发结果
     */
    public DeviceCommandResult sendCommand(com.meiyetec.entity.IotDevice device,
                                            DeviceCommand command, String cmdUrl) {
        long start = System.currentTimeMillis();
        if (cmdUrl == null || cmdUrl.isEmpty()) {
            return DeviceCommandResult.fail("HTTP_URL_MISSING", "设备未配置 HTTP 命令端点");
        }
        try {
            // 1. 构造请求 body
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("commandId", command.getCorrelationId() != null ? command.getCorrelationId()
                    : UUID.randomUUID().toString());
            body.put("commandType", command.getCommandType());
            body.put("params", command.getParams() == null ? java.util.Map.of() : command.getParams());
            String bodyStr = json.writeValueAsString(body);

            // 2. 构造 HTTP 请求
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cmdUrl))
                    .timeout(Duration.ofSeconds(command.getTimeoutSeconds() == null
                            ? 5 : command.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Device-Api-Key", device.getAuthToken() == null ? "" : device.getAuthToken())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                    .build();

            // 3. 同步调用
            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;
            if (resp.statusCode() / 100 == 2) {
                log.info("[IOT-006] HTTP 命令已下发：device={} url={} cmd={} status={}",
                        device.getDeviceCode(), cmdUrl, command.getCommandType(), resp.statusCode());
                return DeviceCommandResult.ok(
                        "http-" + UUID.randomUUID().toString().substring(0, 8),
                        cmdUrl, resp.body(), duration);
            }
            return DeviceCommandResult.fail("HTTP_" + resp.statusCode(),
                    "设备返回 HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 200));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("[IOT-006] HTTP 下发失败：device={} url={} err={}",
                    device.getDeviceCode(), cmdUrl, e.getMessage());
            return DeviceCommandResult.fail("HTTP_ERROR",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
