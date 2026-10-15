package com.meiyetec.crm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EXT-15：CRM 适配器路由（对照 PaymentGatewayRouter）
 *
 * <p>运行时按 {@link CrmAdapter#platformCode()} 路由到对应实现。
 * 当前实现：SalesforceCrmAdapter（生产）+ MockCrmAdapter（dev fallback）。
 */
@Component
public class CrmAdapterRouter {

    private final Map<String, CrmAdapter> adapters = new HashMap<>();

    @Autowired
    public CrmAdapterRouter(List<CrmAdapter> adapterList) {
        for (CrmAdapter adapter : adapterList) {
            adapters.put(adapter.platformCode().toUpperCase(), adapter);
        }
    }

    public CrmAdapter getAdapter(String platform) {
        if (platform == null) return getAdapter("MOCK");
        CrmAdapter a = adapters.get(platform.toUpperCase());
        if (a == null) {
            // fallback to mock for unknown platforms in dev
            return adapters.get("MOCK");
        }
        return a;
    }

    public boolean hasAdapter(String platform) {
        return platform != null && adapters.containsKey(platform.toUpperCase());
    }

    public List<String> listPlatforms() {
        return adapters.keySet().stream().sorted().toList();
    }
}
