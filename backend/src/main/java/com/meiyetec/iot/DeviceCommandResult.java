package com.meiyetec.iot;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * IOT-006：设备命令下发结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandResult {
    private boolean success;
    private String commandId;
    private String deviceTopic;
    private String errorCode;
    private String errorMessage;
    /** 设备侧 ACK 内容（如果有） */
    private String deviceAck;
    /** 下发耗时（毫秒） */
    private long durationMs;

    public static DeviceCommandResult ok(String commandId, String topic, String ack, long ms) {
        return new DeviceCommandResult(true, commandId, topic, null, null, ack, ms);
    }

    public static DeviceCommandResult fail(String code, String msg) {
        return new DeviceCommandResult(false, null, null, code, msg, null, 0);
    }
}
