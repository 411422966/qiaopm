package com.meiyetec.iot;

import com.meiyetec.entity.IotDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IoT 协议路由
 *
 * <p>路由策略（按优先级）：
 * <ol>
 *   <li>{@code deviceType}（设备型号）— 最精确，如 "massage_chair" / "skin_analyzer"</li>
 *   <li>{@code protocol}（通信协议）— 如 "mqtt" / "http" / "ble"</li>
 *   <li>Fallback：默认返回 mqtt 适配器</li>
 * </ol>
 *
 * <p>适配器注册约定：实现类使用 {@code @Component} 注解，
 * Spring 自动收集后按 {@code protocolCode()} 注册。
 */
@Slf4j
@Component
public class IotProtocolRouter {

    private final List<IotDeviceAdapter> adapterList;
    // protocolCode → adapter
    private final Map<String, IotDeviceAdapter> byProtocol = new HashMap<>();
    // deviceType → adapter
    private final Map<String, IotDeviceAdapter> byDeviceType = new HashMap<>();

    @Autowired
    public IotProtocolRouter(List<IotDeviceAdapter> adapters) {
        this.adapterList = adapters;
        for (IotDeviceAdapter adapter : adapters) {
            String code = adapter.protocolCode();
            byProtocol.putIfAbsent(code, adapter);
            // 同时注册 deviceType 别名（massage_chair / skin_analyzer 也注册到 byDeviceType）
            byDeviceType.putIfAbsent(code, adapter);
        }
        adapters.forEach(a -> log.info("[IOT-ROUTER] Registered adapter: protocolCode={} ({})",
                a.protocolCode(), a.getClass().getSimpleName()));
    }

    /**
     * 根据设备对象的 {@code protocol} 字段获取适配器
     */
    public IotDeviceAdapter getAdapter(String protocol) {
        IotDeviceAdapter adapter = byProtocol.get(protocol);
        if (adapter == null) {
            // 兜底: 使用 MQTT 适配器
            adapter = byProtocol.get("mqtt");
        }
        return adapter != null ? adapter : byProtocol.values().stream().findFirst().orElse(null);
    }

    /**
     * 根据设备对象的 protocol + deviceType 综合判断（推荐使用）。
     * <p>优先用 deviceType 匹配，再 fallback 到 protocol。
     */
    public IotDeviceAdapter getAdapterForDevice(IotDevice device) {
        if (device == null) return byProtocol.get("mqtt");

        // 优先：按 deviceType 匹配
        String deviceType = device.getDeviceType();
        if (deviceType != null && !deviceType.isEmpty()) {
            IotDeviceAdapter byType = byDeviceType.get(deviceType.toLowerCase());
            if (byType != null) {
                log.debug("[IOT-ROUTER] Routed device {} by deviceType={}",
                        device.getDeviceCode(), deviceType);
                return byType;
            }
        }

        // Fallback：按 protocol 匹配
        return getAdapter(device.getProtocol());
    }

    public boolean hasAdapter(String protocol) {
        return byProtocol.containsKey(protocol);
    }

    /** 列出所有已注册的适配器 */
    public Map<String, String> listAdapters() {
        Map<String, String> result = new HashMap<>();
        byProtocol.forEach((k, v) -> result.put(k, v.getClass().getSimpleName()));
        return result;
    }
}
