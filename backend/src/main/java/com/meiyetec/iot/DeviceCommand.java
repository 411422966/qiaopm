package com.meiyetec.iot;

import lombok.Data;

/**
 * IOT-006：设备联动命令
 *
 * <p>由 {@code executeLink} 构造，下发给从设备。
 *
 * <h3>字段</h3>
 * <ul>
 *   <li>{@code commandType}：lock / unlock / adjust / start / stop / alert / custom</li>
 *   <li>{@code params}：参数 JSON（如 {"duration": 30, "level": 80}）</li>
 *   <li>{@code timeoutSeconds}：执行超时</li>
 *   <li>{@code correlationId}：链路关联 ID（用于日志追踪）</li>
 * </ul>
 */
@Data
public class DeviceCommand {
    private String commandType;
    private java.util.Map<String, Object> params;
    private Integer timeoutSeconds;
    private String correlationId;
}
