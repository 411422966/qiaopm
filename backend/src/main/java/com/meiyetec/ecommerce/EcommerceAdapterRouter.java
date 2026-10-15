package com.meiyetec.ecommerce;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EXT-16：电商适配器路由（与 CrmAdapterRouter 同源设计）
 *
 * <p>按 {@link EcommerceAdapter#platformCode()} 路由到对应实现；未知平台 → fallback MOCK。
 */
@Component
public class EcommerceAdapterRouter {

    private final Map<String, EcommerceAdapter> adapters = new HashMap<>();

    @Autowired
    public EcommerceAdapterRouter(List<EcommerceAdapter> adapterList) {
        for (EcommerceAdapter a : adapterList) {
            adapters.put(a.platformCode().toUpperCase(), a);
        }
    }

    public EcommerceAdapter getAdapter(String platform) {
        if (platform == null) return getAdapter("MOCK");
        EcommerceAdapter a = adapters.get(platform.toUpperCase());
        if (a == null) return adapters.get("MOCK");
        return a;
    }

    public boolean hasAdapter(String platform) {
        return platform != null && adapters.containsKey(platform.toUpperCase());
    }

    public List<String> listPlatforms() {
        return adapters.keySet().stream().sorted().toList();
    }
}
