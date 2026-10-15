package com.meiyetec.dcep;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P0-MONEY-001 后续 · DCEP 适配器工厂
 *
 * <p>根据 channelCode 路由到具体的银行实现。
 * <p>扩展：新增银行时，只需要注入一个 {@link DcepChannelAdapter} Bean 即可。
 */
@Component
public class DcepAdapterFactory {

    private final Map<String, DcepChannelAdapter> adapterMap = new HashMap<>();

    @Autowired
    public DcepAdapterFactory(List<DcepChannelAdapter> adapters) {
        for (DcepChannelAdapter adapter : adapters) {
            adapterMap.put(adapter.channelCode(), adapter);
        }
    }

    /**
     * 根据 channelCode 获取适配器
     *
     * @throws IllegalArgumentException 没有匹配的实现时
     */
    public DcepChannelAdapter getAdapter(String channelCode) {
        DcepChannelAdapter adapter = adapterMap.get(channelCode);
        if (adapter == null) {
            throw new IllegalArgumentException(
                "No DCEP adapter for channelCode=" + channelCode +
                ", available: " + adapterMap.keySet());
        }
        return adapter;
    }

    /**
     * 列出所有已注册的 channelCode（用于健康检查 / 运维）
     */
    public java.util.Set<String> listChannelCodes() {
        return adapterMap.keySet();
    }
}
