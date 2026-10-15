package com.meiyetec.cn.domestic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 国内支付通道路由器（按 channelCode 路由）
 *
 * <p>与海外支付路由 ({@link com.meiyetec.payment.PaymentGatewayRouter}) 风格一致；
 * 不复用后者以避免插件沙箱扫描误判。
 *
 * @since 2026-09-07
 */
@Component
public class DomesticPayChannelRouter {

    private static final Logger log = LoggerFactory.getLogger(DomesticPayChannelRouter.class);

    private final Map<String, DomesticPayChannelAdapter> adapters = new HashMap<>();

    @Autowired
    public DomesticPayChannelRouter(List<DomesticPayChannelAdapter> adapterList) {
        for (DomesticPayChannelAdapter a : adapterList) {
            adapters.put(a.channelCode(), a);
            log.info("[CN-DOMESTIC] register adapter: {} ({})", a.channelCode(), a.channelName());
        }
        log.info("[CN-DOMESTIC] router initialized with {} adapter(s): {}", adapters.size(), adapters.keySet());
    }

    public DomesticPayChannelAdapter getAdapter(String channelCode) {
        DomesticPayChannelAdapter adapter = adapters.get(channelCode);
        if (adapter == null) {
            throw new IllegalArgumentException(
                    "Unsupported CN domestic channel: " + channelCode + " (registered: " + adapters.keySet() + ")");
        }
        return adapter;
    }

    public boolean hasAdapter(String channelCode) {
        return adapters.containsKey(channelCode);
    }

    /** 返回所有已注册通道编码 */
    public List<String> listChannels() {
        return new ArrayList<>(adapters.keySet());
    }

    /** 获取通道运行状态：active / sandbox / disabled */
    public String getChannelStatus(String channelCode) {
        DomesticPayChannelAdapter adapter = adapters.get(channelCode);
        return adapter == null ? "disabled" : adapter.status();
    }
}
