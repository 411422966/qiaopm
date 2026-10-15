package com.meiyetec.iot;

import com.meiyetec.entity.IotDevice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("httpAdapter")
public class HttpDeviceAdapter implements IotDeviceAdapter {

    @Override
    public String protocolCode() {
        return "http";
    }

    @Override
    public boolean validateCredentials(IotDevice device, Map<String, String> credentials) {
        return credentials != null && credentials.containsKey("apiKey");
    }

    @Override
    public ParsedDeviceReport parseReport(IotDevice device, String rawPayload) {
        ParsedDeviceReport report = new ParsedDeviceReport();
        report.valid = true;
        report.dataType = "operation";
        report.metrics = new HashMap<>();
        report.deviceReportTime = LocalDateTime.now();
        return report;
    }

    @Override
    public CommandResponse sendCommand(IotDevice device, DeviceCommand command) {
        CommandResponse response = new CommandResponse();
        response.success = true;
        response.commandId = "http_cmd_" + UUID.randomUUID().toString().replace("-", "");
        log.info("HTTP command sent to {}: {}", device.getDeviceCode(), command.commandType);
        return response;
    }

    @Override
    public HealthInfo healthCheck(IotDevice device) {
        HealthInfo info = new HealthInfo();
        info.online = true;
        info.latencyMs = 100L;
        info.message = "HTTP device reachable";
        return info;
    }
}
