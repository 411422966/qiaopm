package com.meiyetec.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayRouter {

    private final Map<String, OverseasPaymentAdapter> adapters = new HashMap<>();

    @Autowired
    public PaymentGatewayRouter(List<OverseasPaymentAdapter> adapterList) {
        for (OverseasPaymentAdapter adapter : adapterList) {
            adapters.put(adapter.gatewayCode(), adapter);
        }
    }

    public OverseasPaymentAdapter getAdapter(String gatewayCode) {
        OverseasPaymentAdapter adapter = adapters.get(gatewayCode);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported gateway: " + gatewayCode);
        }
        return adapter;
    }

    public boolean hasAdapter(String gatewayCode) {
        return adapters.containsKey(gatewayCode);
    }
}
